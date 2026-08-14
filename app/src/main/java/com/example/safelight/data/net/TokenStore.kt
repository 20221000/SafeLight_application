package com.example.safelight.data.net

import android.content.Context

/**
 * 웹의 localStorage('accessToken') 자리.
 * Application 에서 한 번 init 한 뒤 인터셉터와 화면이 같이 읽는다.
 */
object TokenStore {
    private const val PREFS = "safelight_auth"
    private const val KEY_TOKEN = "accessToken"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val prefs get() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
        }.apply()

    fun clear() {
        accessToken = null
    }
}
