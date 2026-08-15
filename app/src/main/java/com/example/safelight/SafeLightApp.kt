package com.example.safelight

import android.app.Application
import android.util.Log
import com.example.safelight.data.SessionStore
import com.example.safelight.data.SettingsStore
import com.example.safelight.data.net.TokenStore
import com.kakao.vectormap.KakaoMapSdk

class SafeLightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
        SettingsStore.init(this)
        SessionStore.init(this)

        // 지도 SDK 는 앱 시작 시 한 번만 초기화한다.
        // 키가 비어 있으면 init 이 던지므로, 아직 발급 전이어도 앱은 뜨도록 막아둔다.
        val key = BuildConfig.KAKAO_NATIVE_APP_KEY
        if (key.isBlank()) {
            Log.w(TAG, "KAKAO_NATIVE_APP_KEY 가 비어 있다. local.properties 에 네이티브 앱 키를 넣어야 지도가 뜬다.")
            return
        }
        KakaoMapSdk.init(this, key)
    }

    companion object {
        private const val TAG = "SafeLightApp"
    }
}
