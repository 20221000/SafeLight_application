package com.example.safelight.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 앱을 다시 열어도 남아야 하는 사용자 설정. 웹의 `localStorage` 자리다.
 *
 * 야간 모드는 기기의 다크 모드 설정과 무관하다 — 웹에서도 사용자가 헤더에서 직접 켜는 값이고
 * 기본값은 밝은 화면이다([SafeLightTheme] 참고).
 */
object SettingsStore {

    private const val PREFS = "safelight_settings"
    private const val KEY_NIGHT_MODE = "nightMode"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var nightMode: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_MODE, value).apply()
}
