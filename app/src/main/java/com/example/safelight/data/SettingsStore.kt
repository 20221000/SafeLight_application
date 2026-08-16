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
    private const val KEY_ALARM_SOUND = "alarmSound"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var nightMode: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_MODE, value).apply()

    /**
     * 긴급 신고 접수 시 사이렌을 울릴지. 기본은 켬.
     *
     * 웹은 이 값을 화면 상태로만 들고 있어 새로고침하면 되돌아간다. 앱에서는 여기 저장한다 —
     * 화면을 나갔다 오면 잊어버리는 스위치는 '꺼 뒀다'고 믿게 만들어 위험한 쪽으로 어긋난다.
     * (사이렌 재생 자체는 웹과 마찬가지로 아직 없다 — 값만 보관한다.)
     */
    var alarmSound: Boolean
        get() = prefs.getBoolean(KEY_ALARM_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_SOUND, value).apply()
}
