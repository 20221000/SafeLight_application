package com.example.safelight.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.ApiEnvelope
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.UnreadCountDto
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Response

/** 화면을 보고 있는 동안 이 간격으로 다시 센다. 웹 useUnreadNotifications 의 POLL_MS 와 같다. */
const val UNREAD_POLL_MS = 30_000L

/**
 * 상단바 벨 뱃지용 — 안 읽은 **긴급 알림 수**와 **쪽지 수**를 따로 센다.
 *
 * 둘을 합치지 않는 이유: 벨이 둘을 다르게 그린다. 긴급은 빨강(벨 자체가 빨개진다),
 * 쪽지는 파란 점만. 합계 하나로는 "지금 급한 일인지"를 알 수 없다.
 *
 * 백엔드 notifications 테이블은 emergency_report_id 가 NOT NULL 이라 쪽지 알림을 담지 못한다.
 * 그래서 쪽지는 쪽지 API 로 따로 센다 — 둘 다 정식 API 이고 숫자도 서버가 센 값 그대로다.
 */
class UnreadViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var emergency by mutableStateOf(0)
        private set
    var message by mutableStateOf(0)
        private set

    fun clear() {
        emergency = 0
        message = 0
    }

    /** 한쪽이 실패해도 다른 쪽 숫자는 살린다 — 네트워크 오류로 뱃지를 지우지 않는다. */
    fun refresh() {
        viewModelScope.launch {
            val emergencyCall = async { runCatching { api.getUnreadNotificationCount() }.getOrNull() }
            val messageCall = async { runCatching { api.getUnreadMessageCount() }.getOrNull() }
            emergencyCall.await()?.count()?.let { emergency = it }
            messageCall.await()?.count()?.let { message = it }
        }
    }

    private fun Response<ApiEnvelope<UnreadCountDto>>.count(): Int? =
        if (isSuccessful && body()?.success == true) body()?.data?.unreadCount else null
}
