package com.example.safelight.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.FriendDto
import com.example.safelight.data.net.MessageSendRequest
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** 받은·보낸 쪽지를 한 모양으로 맞춘 것. [mine] 이면 내가 보낸 쪽지다. */
data class ChatMessage(
    val messageId: Long,
    val peerId: Long,
    val peerName: String,
    val mine: Boolean,
    val content: String,
    val isRead: Boolean,
    val createdAt: String,
)

/** 상대 한 명과의 대화. */
data class ChatRoom(
    val peerId: Long,
    val peerName: String,
    val messages: List<ChatMessage>,
    val unread: Int,
)

const val MESSAGE_MAX = 1000

/**
 * 쪽지함. 웹 MessagesPage.jsx 와 같은 방식이다 —
 * 받은·보낸 목록을 한 배열로 합쳐 상대별로 묶고, 방을 열면 안 읽은 것을 읽음 처리한다.
 */
class MessagesViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    private var items by mutableStateOf<List<ChatMessage>>(emptyList())

    var rooms by mutableStateOf<List<ChatRoom>>(emptyList())
        private set
    /** 쪽지를 보낼 수 있는 상대. 백엔드가 친구(ACCEPTED)에게만 발송을 허용한다. */
    var friends by mutableStateOf<List<FriendDto>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set
    var selectedPeer by mutableStateOf<Long?>(null)
        private set
    var picking by mutableStateOf(false)
        private set
    var draft by mutableStateOf("")
        private set
    var sending by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** 읽음 처리가 끝날 때마다 올라간다. 헤더 벨이 이걸 보고 다시 센다. */
    var readRevision by mutableStateOf(0)
        private set

    private var started = false

    fun start(openWith: Long?) {
        if (started) return
        started = true
        // 친구 관리에서 '쪽지'로 넘어왔으면 그 방을 바로 연다.
        if (openWith != null && openWith > 0) selectedPeer = openWith
        load()
    }

    fun messageShown() {
        message = null
    }

    fun onDraftChange(value: String) {
        draft = value.take(MESSAGE_MAX)
    }

    /** 열려 있는 방. 아직 주고받은 게 없는 친구를 골랐으면 빈 방을 만들어 준다. */
    val room: ChatRoom?
        get() {
            val peer = selectedPeer ?: return null
            rooms.firstOrNull { it.peerId == peer }?.let { return it }
            val friend = friends.firstOrNull { it.friendUserId == peer }
            return ChatRoom(peer, friend?.friendNickname.orEmpty(), emptyList(), 0)
        }

    fun openRoom(peerId: Long) {
        selectedPeer = peerId
        draft = ""
        picking = false
        markRoomRead(peerId)
    }

    fun closeRoom() {
        selectedPeer = null
        draft = ""
    }

    fun startPicking() {
        picking = true
    }

    fun stopPicking() {
        picking = false
    }

    fun load() {
        loading = true
        viewModelScope.launch {
            val receivedCall = async { runCatching { api.getReceivedMessages() }.getOrNull() }
            val sentCall = async { runCatching { api.getSentMessages() }.getOrNull() }
            val friendsCall = async { runCatching { api.getFriends() }.getOrNull() }

            val received = receivedCall.await()
                ?.takeIf { it.isSuccessful }?.body()?.data.orEmpty()
                .map {
                    ChatMessage(it.messageId, it.senderId, it.senderNickname, false, it.content, it.isRead, it.createdAt)
                }
            val sent = sentCall.await()
                ?.takeIf { it.isSuccessful }?.body()?.data.orEmpty()
                .map {
                    ChatMessage(it.messageId, it.receiverId, it.receiverNickname, true, it.content, it.isRead, it.createdAt)
                }
            friends = friendsCall.await()?.takeIf { it.isSuccessful }?.body()?.data.orEmpty()

            // 오래된 것이 위로 — 대화는 위에서 아래로 읽는다.
            items = (received + sent).sortedBy { it.createdAt }
            rooms = groupRooms(items)
            loading = false
            selectedPeer?.let { markRoomRead(it) }
        }
    }

    private fun groupRooms(all: List<ChatMessage>): List<ChatRoom> =
        all.groupBy { it.peerId }
            .map { (peerId, msgs) ->
                ChatRoom(
                    peerId = peerId,
                    // 이름이 빈 쪽지가 섞여 있을 수 있어 있는 것 중 마지막을 쓴다.
                    peerName = msgs.lastOrNull { it.peerName.isNotBlank() }?.peerName.orEmpty(),
                    messages = msgs,
                    unread = msgs.count { !it.mine && !it.isRead },
                )
            }
            // 최근 대화 순.
            .sortedByDescending { it.messages.lastOrNull()?.createdAt.orEmpty() }

    /**
     * 방을 열면 그 방의 안 읽은 쪽지를 읽음으로 바꾼다.
     * 일괄 처리 엔드포인트가 없어 건별 상세 조회의 부수효과로 대신한다(MessageService.getMessageDetail).
     */
    private fun markRoomRead(peerId: Long) {
        val unread = items.filter { it.peerId == peerId && !it.mine && !it.isRead }
        if (unread.isEmpty()) return
        viewModelScope.launch {
            unread.map { async { runCatching { api.readMessage(it.messageId) } } }.awaitAll()
            val ids = unread.map { it.messageId }.toSet()
            items = items.map { if (it.messageId in ids) it.copy(isRead = true) else it }
            rooms = groupRooms(items)
            readRevision++
        }
    }

    fun send() {
        val peer = selectedPeer ?: return
        val content = draft.trim()
        if (content.isEmpty() || sending) return
        sending = true
        viewModelScope.launch {
            val response = runCatching { api.sendMessage(peer, MessageSendRequest(content)) }.getOrNull()
            if (response?.isSuccessful == true && response.body()?.success == true) {
                draft = ""
                load()
            } else {
                message = response?.errorMessage("쪽지를 보내지 못했습니다.") ?: "서버에 연결하지 못했습니다."
            }
            sending = false
        }
    }

    /**
     * 대화 삭제. 백엔드에는 건별 삭제만 있어 방 안의 쪽지를 하나씩 지운다.
     * 내 쪽에서만 사라지고 상대에게는 그대로 남는다.
     */
    fun deleteRoom() {
        val current = room ?: return
        if (current.messages.isEmpty()) return
        viewModelScope.launch {
            val results = current.messages
                .map { async { runCatching { api.deleteMessage(it.messageId) }.getOrNull()?.isSuccessful == true } }
                .awaitAll()
            val failed = results.count { !it }
            if (failed > 0) message = "쪽지 ${failed}개를 삭제하지 못했습니다."
            selectedPeer = null
            load()
        }
    }
}
