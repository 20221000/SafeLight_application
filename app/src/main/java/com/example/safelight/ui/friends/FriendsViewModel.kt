package com.example.safelight.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.ApiEnvelope
import com.example.safelight.data.net.EmergencyAllowRequest
import com.example.safelight.data.net.FriendDto
import com.example.safelight.data.net.FriendRequestBody
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.ReceivedRequestDto
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.SentRequestDto
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Response

/** 친구 화면의 세 갈래. 웹 FriendsPage 의 TABS 와 같은 순서다. */
enum class FriendTab(val label: String) {
    Friends("친구 목록"),
    Received("받은 요청"),
    Sent("보낸 요청"),
}

/** 웹 FriendsPage.jsx 의 상태. 목록 3종을 한꺼번에 읽고, 무엇을 바꾸든 다시 읽는다. */
class FriendsViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var tab by mutableStateOf(FriendTab.Friends)
        private set
    var search by mutableStateOf("")

    var friends by mutableStateOf<List<FriendDto>>(emptyList())
        private set
    var received by mutableStateOf<List<ReceivedRequestDto>>(emptyList())
        private set
    var sent by mutableStateOf<List<SentRequestDto>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var started = false

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun selectTab(next: FriendTab) {
        tab = next
    }

    fun messageShown() {
        message = null
    }

    fun count(tab: FriendTab): Int = when (tab) {
        FriendTab.Friends -> friends.size
        FriendTab.Received -> received.size
        FriendTab.Sent -> sent.size
    }

    fun load() {
        loading = true
        viewModelScope.launch {
            val friendsCall = async { runCatching { api.getFriends() }.getOrNull() }
            val receivedCall = async { runCatching { api.getReceivedRequests() }.getOrNull() }
            val sentCall = async { runCatching { api.getSentRequests() }.getOrNull() }
            friends = friendsCall.await()?.dataOrNull().orEmpty()
            received = receivedCall.await()?.dataOrNull().orEmpty()
            sent = sentCall.await()?.dataOrNull().orEmpty()
            loading = false
        }
    }

    fun sendRequest() {
        val username = search.trim()
        if (username.isEmpty()) {
            message = "친구의 아이디를 입력해주세요."
            return
        }
        send({ api.sendFriendRequest(FriendRequestBody(username)) }, "친구 요청에 실패했습니다.") {
            search = ""
            message = "친구 요청을 보냈습니다."
        }
    }

    fun accept(requestId: Long) =
        send({ api.acceptFriendRequest(requestId) }, "요청 수락에 실패했습니다.")

    fun reject(requestId: Long) =
        send({ api.rejectFriendRequest(requestId) }, "요청 거절에 실패했습니다.")

    fun cancel(requestId: Long) =
        send({ api.cancelFriendRequest(requestId) }, "요청 취소에 실패했습니다.")

    fun removeFriend(friendUserId: Long) =
        send({ api.deleteFriend(friendUserId) }, "친구 삭제에 실패했습니다.")

    /**
     * 내 쪽 공유만 바꾼다. 응답에 양방향 설정이 다 들어 있어서
     * 목록 3종을 다시 받지 않고 그 줄만 갈아 끼운다.
     */
    fun toggleEmergency(friend: FriendDto) {
        val next = !friend.isEmergencyAllowed
        viewModelScope.launch {
            val response = runCatching {
                api.setEmergencyAllow(friend.friendsId, EmergencyAllowRequest(next))
            }.getOrNull()
            val updated = response?.dataOrNull()
            if (updated != null) {
                friends = friends.map { if (it.friendsId == friend.friendsId) updated else it }
                return@launch
            }
            message = response?.errorMessage("공유 설정을 바꾸지 못했습니다.") ?: "서버에 연결하지 못했습니다."
            load()
        }
    }

    private fun send(
        request: suspend () -> Response<out ApiEnvelope<*>>,
        failMessage: String,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val response = runCatching { request() }.getOrNull()
            if (response?.isSuccessful == true && response.body()?.success == true) {
                onSuccess()
                load()
                return@launch
            }
            // 없는 아이디면 '존재하지 않는 아이디입니다.' 가 서버에서 그대로 올라온다.
            message = response?.errorMessage(failMessage) ?: "서버에 연결하지 못했습니다."
        }
    }

    private fun <T> Response<ApiEnvelope<T>>.dataOrNull(): T? =
        if (isSuccessful && body()?.success == true) body()?.data else null
}
