package com.example.safelight.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.safelight.MainActivity
import com.example.safelight.R
import com.example.safelight.data.net.NotificationDto
import com.example.safelight.data.net.ReceivedMessageDto

/**
 * 휴대폰 알림(상태바)을 띄우는 곳. 채널이 셋이다.
 *  - 긴급 알림: 친구의 긴급신고. 소리·진동과 함께 화면 위로 뜬다.
 *  - 쪽지: 친구가 보낸 쪽지. 보낸 사람마다 한 장으로 묶는다.
 *  - 알림 대기: [AlertWatchService] 가 떠 있다는 표시. 시스템이 요구하는 것이라 조용히 둔다.
 *
 * 어떤 친구의 신고가 오는지는 서버가 정한다 — 긴급 위치 공유를 허용한 친구에게만
 * 알림을 만든다. 앱은 서버가 만든 알림을 그대로 옮길 뿐 다시 거르지 않는다.
 */
object AlertNotifier {

    const val CHANNEL_EMERGENCY = "emergency"
    const val CHANNEL_MESSAGE = "message"
    const val CHANNEL_WATCH = "watch"

    /** 알림을 누르면 [MainActivity] 가 이 값을 보고 알림함을 연다. */
    const val EXTRA_OPEN = "open"
    const val OPEN_INBOX = "inbox"

    private const val TAG_EMERGENCY = "emergency"
    private const val TAG_MESSAGE = "message"

    // 헤더 로고 바탕색(BluePrimary). 긴급만 빨강으로 구분하고 나머지는 앱 색을 쓴다.
    private const val COLOR_BRAND = 0xFF2563EB.toInt()
    private const val COLOR_EMERGENCY = 0xFFE5484D.toInt()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_EMERGENCY, "친구 긴급신고", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "위치 공유를 허용한 친구가 긴급신고를 하면 알려줍니다."
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_MESSAGE, "쪽지", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "친구가 쪽지를 보내면 알려줍니다."
                },
                NotificationChannel(CHANNEL_WATCH, "알림 대기", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "앱을 닫아도 긴급신고·쪽지를 받기 위해 떠 있는 표시입니다."
                    setShowBadge(false)
                },
            )
        )
    }

    /** Android 13 부터는 사용자가 알림 권한을 줘야 뜬다. 그 아래는 늘 허용이다. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun openInboxIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_INBOX)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun watchNotification(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(COLOR_BRAND)
            .setContentTitle("긴급신고·쪽지 알림 받는 중")
            .setContentText("친구의 긴급신고와 쪽지를 확인하고 있습니다.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openInboxIntent(context, 0))
            .build()

    fun postEmergency(context: Context, item: NotificationDto) {
        if (!canPost(context)) return
        val who = item.reporterNickname.ifBlank { "친구" }
        val text = item.message.ifBlank { "${who}님이 긴급신고를 했습니다. 눌러서 위치를 확인하세요." }
        val notification = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(COLOR_EMERGENCY)
            .setContentTitle(item.title.ifBlank { "${who}님의 긴급신고" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openInboxIntent(context, item.notificationId.toInt()))
            .build()
        notify(context, TAG_EMERGENCY, item.notificationId.toInt(), notification)
    }

    /** 같은 사람이 여러 통 보내면 알림을 쌓지 않고 그 사람 것 한 장을 고쳐 쓴다. */
    fun postMessages(context: Context, sender: Long, messages: List<ReceivedMessageDto>) {
        if (!canPost(context) || messages.isEmpty()) return
        val latest = messages.maxBy { it.messageId }
        val who = latest.senderNickname.ifBlank { "친구" }
        val title = if (messages.size == 1) "${who}님의 쪽지" else "${who}님의 쪽지 ${messages.size}통"
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGE)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(COLOR_BRAND)
            .setContentTitle(title)
            .setContentText(latest.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(latest.content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openInboxIntent(context, sender.toInt()))
            .build()
        notify(context, TAG_MESSAGE, sender.toInt(), notification)
    }

    /** 알림함을 열면 상태바에 남은 긴급·쪽지 알림을 걷는다. 알림 대기 표시는 그대로 둔다. */
    fun clearInbox(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.tag == TAG_EMERGENCY || it.tag == TAG_MESSAGE }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    @Suppress("MissingPermission") // canPost 로 먼저 확인한다
    private fun notify(context: Context, tag: String, id: Int, notification: android.app.Notification) {
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }
}
