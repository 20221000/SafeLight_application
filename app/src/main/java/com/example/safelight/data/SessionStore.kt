package com.example.safelight.data

import android.content.Context
import android.content.SharedPreferences

/** 로그인한 사용자. 웹이 localStorage('user') 에 넣던 `{ userId, username, role }` 과 같다. */
data class SessionUser(
    val userId: Long,
    val username: String,
    val role: String,
) {
    val isAdmin: Boolean get() = role == "ADMIN"
}

/**
 * 로그인 상태를 앱 재실행 후에도 유지한다.
 * 토큰 자체는 [com.example.safelight.data.net.TokenStore] 가 들고 있고 여기엔 사용자 정보만 둔다.
 */
object SessionStore {

    private const val PREFS = "safelight_session"
    private const val KEY_USER_ID = "userId"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var user: SessionUser?
        get() {
            val id = prefs.getLong(KEY_USER_ID, 0)
            if (id == 0L) return null
            return SessionUser(
                userId = id,
                username = prefs.getString(KEY_USERNAME, "").orEmpty(),
                role = prefs.getString(KEY_ROLE, "USER").orEmpty(),
            )
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    clear()
                } else {
                    putLong(KEY_USER_ID, value.userId)
                    putString(KEY_USERNAME, value.username)
                    putString(KEY_ROLE, value.role)
                }
            }.apply()
        }
}
