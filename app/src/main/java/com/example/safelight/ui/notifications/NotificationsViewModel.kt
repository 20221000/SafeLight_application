package com.example.safelight.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.NotificationDto
import com.example.safelight.data.net.ReceivedMessageDto
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.SharedLocationDto
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** 알림 한 줄. 긴급신고와 쪽지를 한 모양으로 맞춘 것이다. */
data class AlertItem(
    /** 두 테이블의 id 가 겹치므로 종류를 섞어 만든다. */
    val key: String,
    val emergency: Boolean,
    val createdAt: String,
    val isRead: Boolean,
    val title: String,
    val preview: String,
    /** 쪽지 줄이 대표하는 안 읽은 통수. 긴급은 늘 0 이다. */
    val unreadCount: Int = 0,
    val notification: NotificationDto? = null,
    val sender: SenderGroup? = null,
)

/** 보낸 사람별로 묶은 쪽지. 줄 하나가 그 사람의 쪽지 전부를 대표한다. */
data class SenderGroup(
    val senderId: Long,
    val senderNickname: String,
    val latest: ReceivedMessageDto,
    val unreadIds: List<Long>,
    val total: Int,
)

/**
 * 알림함. 웹 NotificationsPage 와 같이 **두 곳**에서 받아 시간순으로 섞는다.
 *
 * 백엔드 notifications 테이블은 emergency_report_id 가 NOT NULL 이라 쪽지 알림을 담지 못한다.
 * 그래서 쪽지는 쪽지 API 에서 그대로 가져온다 — 숫자도 내용도 서버가 준 값 그대로다.
 */
class NotificationsViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var items by mutableStateOf<List<AlertItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var selectedKey by mutableStateOf<String?>(null)
        private set

    // 위치 공유 조회 결과. 신고자가 허용한 친구만 볼 수 있어 실패 사유를 따로 들고 있는다.
    var shared by mutableStateOf<SharedLocationDto?>(null)
        private set
    var sharedError by mutableStateOf("")
        private set
    var sharedLoading by mutableStateOf(false)
        private set

    /**
     * 읽음 처리가 실제로 끝날 때마다 올라간다. 헤더 벨이 이걸 보고 다시 센다 —
     * 벨이 이 화면 위에 그대로 떠 있어서, 방금 읽은 것이 뱃지에서 바로 빠져야 한다.
     * (웹의 notifyNotificationsChanged 자리다.)
     */
    var readRevision by mutableStateOf(0)
        private set

    private var started = false

    val selected: AlertItem?
        get() = items.firstOrNull { it.key == selectedKey }

    /** 줄 수가 아니라 실제 건수로 센다 — 쪽지 3통이 한 줄로 묶여도 안 읽은 건 3건이다. */
    val unreadCount: Int
        get() = items.sumOf { if (it.isRead) 0 else maxOf(it.unreadCount, 1) }

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun load() {
        loading = true
        viewModelScope.launch {
            // 한쪽이 막혀도 다른 쪽은 보여준다 — 쪽지 API 가 죽었다고 긴급 알림까지 가릴 이유가 없다.
            val alertsCall = async { runCatching { api.getNotifications() }.getOrNull() }
            val messagesCall = async { runCatching { api.getReceivedMessages() }.getOrNull() }

            val alerts = alertsCall.await()?.takeIf { it.isSuccessful }?.body()?.data.orEmpty()
            val messages = messagesCall.await()?.takeIf { it.isSuccessful }?.body()?.data.orEmpty()

            val merged = alerts.map { n ->
                AlertItem(
                    key = "n${n.notificationId}",
                    emergency = true,
                    createdAt = n.createdAt,
                    isRead = n.isRead,
                    title = n.title.ifBlank { "긴급 알림" },
                    preview = listOfNotNull(
                        n.reporterNickname.takeIf { it.isNotBlank() },
                        n.message.ifBlank { "내용 없음" },
                    ).joinToString(" · "),
                    notification = n,
                )
            } + groupMessagesBySender(messages)

            // 최신순. 서버가 각자 정렬해 줘도 섞고 나면 다시 세워야 한다.
            items = merged.sortedByDescending { it.createdAt }
            loading = false
        }
    }

    /**
     * 쪽지는 보낸 사람별로 한 줄로 묶는다. 한 통에 한 줄이면 수다스러운 친구 하나가
     * 목록을 다 차지해서 정작 긴급 알림이 아래로 밀려난다.
     */
    private fun groupMessagesBySender(messages: List<ReceivedMessageDto>): List<AlertItem> =
        messages.groupBy { it.senderId }.map { (senderId, msgs) ->
            // 서버 정렬을 믿지 않고 직접 고른다 — 두 목록을 섞는 마당에 여기만 순서를 가정할 이유가 없다.
            val latest = msgs.maxByOrNull { it.createdAt } ?: msgs.first()
            val unreadIds = msgs.filter { !it.isRead }.map { it.messageId }
            AlertItem(
                key = "m$senderId",
                emergency = false,
                createdAt = latest.createdAt,
                isRead = unreadIds.isEmpty(),
                title = "${latest.senderNickname.ifBlank { "친구" }}님의 쪽지",
                preview = latest.content.ifBlank { "내용 없음" },
                unreadCount = unreadIds.size,
                sender = SenderGroup(senderId, latest.senderNickname, latest, unreadIds, msgs.size),
            )
        }

    fun open(item: AlertItem) {
        selectedKey = item.key
        if (item.emergency) {
            val reportId = item.notification?.reportId
            if (reportId != null) {
                loadSharedLocation(reportId)
            } else {
                shared = null
                sharedError = "연결된 신고가 없습니다."
            }
        } else {
            // 쪽지에는 지도가 없다. 앞선 신고의 위치가 남아 있으면 엉뚱한 곳을 가리킨다.
            shared = null
            sharedError = ""
        }
        markRead(item)
    }

    fun close() {
        selectedKey = null
        shared = null
        sharedError = ""
    }

    private fun loadSharedLocation(reportId: Long) {
        shared = null
        sharedError = ""
        sharedLoading = true
        viewModelScope.launch {
            val response = runCatching { api.getSharedLocation(reportId) }.getOrNull()
            val data = response?.takeIf { it.isSuccessful && it.body()?.success == true }?.body()?.data
            if (data != null) {
                shared = data
            } else {
                sharedError = response?.errorMessage("위치를 불러오지 못했습니다.") ?: "서버에 연결하지 못했습니다."
            }
            sharedLoading = false
        }
    }

    /**
     * 종류마다 읽음 처리 방법이 다르다.
     *  - 긴급: `PATCH /notifications/{id}/read` — 알림 1건이 줄 1개다.
     *  - 쪽지: 전용 엔드포인트가 없어 `GET /messages/{id}` 의 부수효과로 대신한다.
     *    줄 하나가 그 사람의 쪽지 여러 통을 대표하므로 안 읽은 것을 **전부** 훑어야 한다.
     */
    private fun markRead(item: AlertItem) {
        if (item.isRead) return
        viewModelScope.launch {
            val ok = if (item.emergency) {
                val id = item.notification?.notificationId ?: return@launch
                runCatching { api.markNotificationRead(id) }.getOrNull()?.isSuccessful == true
            } else {
                val ids = item.sender?.unreadIds.orEmpty()
                ids.map { async { runCatching { api.readMessage(it) }.getOrNull()?.isSuccessful == true } }
                    .awaitAll()
                    .all { it }
            }
            if (!ok) {
                // 일부라도 실패하면 아직 안 읽은 게 남아 있다 — 다 읽은 척하지 않는다.
                load()
                return@launch
            }
            items = items.map {
                if (it.key == item.key) it.copy(isRead = true, unreadCount = 0) else it
            }
            readRevision++
        }
    }

    /** 일괄 처리 엔드포인트가 없어 건별로 보낸다. */
    fun markAllRead() {
        items.filter { !it.isRead }.forEach { markRead(it) }
    }
}
