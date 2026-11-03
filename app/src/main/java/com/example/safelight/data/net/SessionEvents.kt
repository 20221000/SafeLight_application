package com.example.safelight.data.net

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 로그인 만료 알림. 웹 api.js 의 SESSION_EXPIRED 이벤트 자리다.
 *
 * 백엔드 토큰은 24시간짜리인데, 예전에는 앱을 열 때 한 번만 확인했다. 쓰는 도중 만료되면
 * 화면은 로그인된 채로 긴급신고가 막혔고, 친구 알림 서비스는 아무 말 없이 멈췄다.
 * 이제 어느 요청에서든 401 을 받으면 [Network] 가 세션을 지우고 여기로 알린다.
 * 화면(AuthViewModel)은 안내 창을, 앱(SafeLightApp)은 휴대폰 알림을 띄운다.
 */
object SessionEvents {
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired

    fun notifyExpired() {
        _expired.tryEmit(Unit)
    }
}
