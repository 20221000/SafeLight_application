package com.example.safelight.alert

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.safelight.data.SessionStore
import com.example.safelight.data.net.ApiEnvelope
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.TokenStore
import com.example.safelight.data.net.UnreadCountDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * 앱을 닫아도 친구의 긴급신고·쪽지를 휴대폰 알림으로 띄우기 위해 떠 있는 서비스.
 *
 * 백엔드에 푸시(FCM)가 없어서 서버가 휴대폰을 깨울 방법이 없다. 그래서 앱이
 * [POLL_MS] 마다 안 읽은 수를 묻고, 늘었을 때만 목록을 받아 새 것만 알린다.
 * 안드로이드는 화면이 없는 앱의 작업을 곧 멈추므로 포그라운드 서비스로 띄운다
 * (그 대가로 '알림 받는 중' 표시가 상태바에 남는다).
 *
 * 로그인할 때 [start], 로그아웃할 때 [stop] 한다. 토큰이 거절되면(401/403) 스스로 멈춘다.
 */
class AlertWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api: SafeLightApi by lazy { Network.backend(SafeLightApi::class.java) }
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AlertNotifier.createChannels(this)
        val notification = AlertNotifier.watchNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(WATCH_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(WATCH_ID, notification)
        }
        if (!started) {
            started = true
            scope.launch { watch() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun watch() {
        while (scope.isActive) {
            val keepGoing = runCatching { checkOnce() }
                // 서버가 꺼졌거나 네트워크가 끊긴 것은 다음 차례에 다시 본다.
                .onFailure { Log.w(TAG, "알림 확인 실패", it) }
                .getOrDefault(true)
            if (!keepGoing) {
                stopSelf()
                return
            }
            delay(POLL_MS)
        }
    }

    /** false 를 돌려주면 더 볼 수 없는 상태(로그아웃·토큰 만료)라 서비스를 멈춘다. */
    private suspend fun checkOnce(): Boolean {
        val user = SessionStore.user ?: return false
        if (TokenStore.accessToken.isNullOrBlank()) return false
        val cursor = AlertCursor(this, user.userId)

        val emergencyCount = scope.async { api.getUnreadNotificationCount() }
        val messageCount = scope.async { api.getUnreadMessageCount() }
        val emergency = emergencyCount.await()
        val message = messageCount.await()
        if (emergency.isAuthRejected() || message.isAuthRejected()) return false

        // 처음 도는 계정은 이미 쌓여 있던 것을 기준선으로만 잡는다 — 로그인하자마자 옛 알림이 쏟아지지 않게.
        val firstRun = !cursor.initialized
        var posted = false
        var baselineReady = true

        if (firstRun || (emergency.unread() ?: 0) > 0) {
            val list = api.getNotifications().body()?.takeIf { it.success }?.data
            if (list != null) {
                val fresh = list.filter { !it.isRead && it.notificationId > cursor.emergency }
                if (!firstRun) {
                    fresh.sortedBy { it.notificationId }.forEach { AlertNotifier.postEmergency(this, it) }
                    posted = posted || fresh.isNotEmpty()
                }
                list.maxOfOrNull { it.notificationId }?.let { cursor.emergency = maxOf(cursor.emergency, it) }
            } else {
                baselineReady = false
            }
        }

        if (firstRun || (message.unread() ?: 0) > 0) {
            val list = api.getReceivedMessages().body()?.takeIf { it.success }?.data
            if (list != null) {
                val fresh = list.filter { !it.isRead && it.messageId > cursor.message }
                if (!firstRun) {
                    fresh.groupBy { it.senderId }.forEach { (sender, items) ->
                        AlertNotifier.postMessages(this, sender, items)
                    }
                    posted = posted || fresh.isNotEmpty()
                }
                list.maxOfOrNull { it.messageId }?.let { cursor.message = maxOf(cursor.message, it) }
            } else {
                baselineReady = false
            }
        }

        // 기준선을 다 못 잡았으면 다음 차례에 처음부터 다시 잡는다 — 반쪽 기준선이면 옛 알림이 새 것처럼 뜬다.
        if (firstRun && baselineReady) cursor.initialized = true
        if (posted) _updates.tryEmit(Unit)
        return true
    }

    private fun Response<*>.isAuthRejected() = code() == 401 || code() == 403

    private fun Response<ApiEnvelope<UnreadCountDto>>.unread(): Int? =
        if (isSuccessful && body()?.success == true) body()?.data?.unreadCount else null

    companion object {
        private const val TAG = "AlertWatchService"
        private const val WATCH_ID = 1
        /** 화면 안의 벨 뱃지([com.example.safelight.ui.notifications.UNREAD_POLL_MS])와 같은 간격이다. */
        private const val POLL_MS = 30_000L

        private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** 새 알림을 띄웠을 때 한 번씩 흐른다. 앱이 열려 있으면 벨 뱃지를 바로 다시 센다. */
        val updates: SharedFlow<Unit> = _updates

        fun start(context: Context) {
            if (SessionStore.user == null || !AlertNotifier.canPost(context)) return
            ContextCompat.startForegroundService(context, Intent(context, AlertWatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlertWatchService::class.java))
        }
    }
}

/**
 * 어디까지 알렸는지. 계정마다 따로 둔다 — 다른 계정으로 로그인했을 때 앞사람의 기준선을 쓰지 않게.
 * 앱을 지웠다 깔기 전까지 남으므로, 서비스가 재시작돼도 같은 알림을 두 번 띄우지 않는다.
 */
private class AlertCursor(context: Context, userId: Long) {
    private val prefs = context.getSharedPreferences("safelight_alerts_$userId", Context.MODE_PRIVATE)

    var initialized: Boolean
        get() = prefs.getBoolean("initialized", false)
        set(value) = prefs.edit().putBoolean("initialized", value).apply()

    var emergency: Long
        get() = prefs.getLong("lastNotificationId", 0)
        set(value) = prefs.edit().putLong("lastNotificationId", value).apply()

    var message: Long
        get() = prefs.getLong("lastMessageId", 0)
        set(value) = prefs.edit().putLong("lastMessageId", value).apply()
}
