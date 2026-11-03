package com.example.safelight

import android.app.Application
import android.util.Log
import com.example.safelight.data.SessionStore
import com.example.safelight.data.SettingsStore
import com.example.safelight.alert.AlertNotifier
import com.example.safelight.alert.AlertWatchService
import com.example.safelight.data.net.SessionEvents
import com.example.safelight.data.net.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.kakao.vectormap.KakaoMapSdk

class SafeLightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
        SettingsStore.init(this)
        SessionStore.init(this)

        // 로그인이 만료되면(어느 요청이든 401) 친구 알림 서비스를 멈추고, 멈췄다는 걸 휴대폰 알림으로 남긴다.
        // 앱 화면이 없어도(재부팅 뒤 서비스만 떠 있어도) 받아야 하므로 화면이 아니라 여기서 듣는다.
        appScope.launch {
            SessionEvents.expired.collect {
                AlertWatchService.stop(this@SafeLightApp)
                AlertNotifier.postSessionExpired(this@SafeLightApp)
            }
        }

        // 지도 SDK 는 앱 시작 시 한 번만 초기화한다.
        // 키가 비어 있으면 init 이 던지므로, 아직 발급 전이어도 앱은 뜨도록 막아둔다.
        val key = BuildConfig.KAKAO_NATIVE_APP_KEY
        if (key.isBlank()) {
            Log.w(TAG, "KAKAO_NATIVE_APP_KEY 가 비어 있다. local.properties 에 네이티브 앱 키를 넣어야 지도가 뜬다.")
            return
        }
        KakaoMapSdk.init(this, key)
    }

    /** 앱이 살아 있는 동안 도는 일. 프로세스와 수명이 같아 따로 취소하지 않는다. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "SafeLightApp"
    }
}
