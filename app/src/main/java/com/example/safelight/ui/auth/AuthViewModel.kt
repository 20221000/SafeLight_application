package com.example.safelight.ui.auth

import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.SessionStore
import com.example.safelight.data.SessionUser
import com.example.safelight.data.net.AuthApi
import com.example.safelight.data.net.LoginRequest
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.RegisterRequest
import com.example.safelight.data.net.TokenStore
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val TAG = "Auth"

/** 회원가입이 끝났을 때 완료 화면에 보여줄 값. 방금 만든 계정을 눈으로 확인시키기 위한 것이다. */
data class RegisteredAccount(val username: String, val nickname: String, val email: String)

/**
 * 로그인·회원가입·로그아웃. 웹 App.jsx 의 handleLogin/handleLogout 과 LoginPage/RegisterPage 의
 * 제출 처리를 한곳에 모은 것이다.
 */
class AuthViewModel : ViewModel() {

    private val api: AuthApi = Network.backend(AuthApi::class.java)

    var user by mutableStateOf(SessionStore.user)
        private set

    var error by mutableStateOf("")
        private set

    var loading by mutableStateOf(false)
        private set

    /** 가입 완료 화면으로 넘어갈지. null 이면 아직 입력 중이다. */
    var registered by mutableStateOf<RegisteredAccount?>(null)
        private set

    init {
        checkToken()
    }

    fun clearError() {
        error = ""
    }

    fun login(usernameOrEmail: String, password: String, onSuccess: () -> Unit) {
        if (usernameOrEmail.isBlank() || password.isBlank()) {
            error = "아이디와 비밀번호를 입력해주세요."
            return
        }
        loading = true
        error = ""
        viewModelScope.launch {
            runCatching { api.login(LoginRequest(usernameOrEmail, password)) }
                .onSuccess { response ->
                    val body = response.body()
                    val data = body?.data
                    if (!response.isSuccessful || body?.success != true || data == null) {
                        error = if (response.isSuccessful) {
                            body?.message ?: "로그인에 실패했습니다."
                        } else {
                            response.errorMessage("로그인에 실패했습니다.")
                        }
                        return@onSuccess
                    }
                    TokenStore.accessToken = data.accessToken
                    val session = SessionUser(
                        userId = data.userId,
                        username = data.username,
                        role = roleFromToken(data.accessToken),
                    )
                    SessionStore.user = session
                    user = session
                    onSuccess()
                }
                .onFailure {
                    Log.e(TAG, "로그인 실패", it)
                    error = "서버 연결에 실패했습니다."
                }
            loading = false
        }
    }

    fun register(username: String, nickname: String, email: String, password: String, passwordConfirm: String) {
        validateRegister(username, nickname, email, password, passwordConfirm)?.let {
            error = it
            return
        }
        loading = true
        error = ""
        viewModelScope.launch {
            runCatching { api.register(RegisterRequest(username, nickname, email, password)) }
                .onSuccess { response ->
                    if (!response.isSuccessful || response.body()?.success != true) {
                        error = if (response.isSuccessful) {
                            response.body()?.message ?: "회원가입에 실패했습니다."
                        } else {
                            response.errorMessage("회원가입에 실패했습니다.")
                        }
                        return@onSuccess
                    }
                    registered = RegisteredAccount(username, nickname, email)
                }
                .onFailure {
                    Log.e(TAG, "회원가입 실패", it)
                    error = "서버 연결에 실패했습니다."
                }
            loading = false
        }
    }

    /** 가입 완료 화면에서 로그인 화면으로 넘어갈 때. */
    fun consumeRegistered() {
        registered = null
        error = ""
    }

    fun logout() {
        // 서버에도 알린다. 실패해도(네트워크·이미 만료 등) 로컬 정리는 그대로 진행한다.
        viewModelScope.launch { runCatching { api.logout() } }
        clearSession()
    }

    private fun clearSession() {
        TokenStore.accessToken = null
        SessionStore.user = null
        user = null
    }

    /**
     * 앱을 열 때 저장된 토큰이 아직 유효한지 한 번 확인한다.
     * 401/403(명시적 인증 거부)일 때만 조용히 로그아웃한다 —
     * 서버가 꺼져 있거나 네트워크가 끊긴 것으로는 로그인 상태를 지우지 않는다.
     */
    private fun checkToken() {
        if (user == null || TokenStore.accessToken.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching { api.authCheck() }
                .onSuccess { if (it.code() == 401 || it.code() == 403) clearSession() }
        }
    }

    private fun validateRegister(
        username: String,
        nickname: String,
        email: String,
        password: String,
        passwordConfirm: String,
    ): String? = when {
        username.isBlank() || nickname.isBlank() || email.isBlank() || password.isBlank() ->
            "모든 항목을 입력해주세요."
        password != passwordConfirm -> "비밀번호가 일치하지 않습니다."
        // 백엔드 @Size(min = 8, max = 72) 와 맞춰둔다. 어긋나면 서버가 400 으로 되돌린다.
        password.length < 8 -> "비밀번호는 8자 이상이어야 합니다."
        password.length > 72 -> "비밀번호는 72자 이하여야 합니다."
        else -> null
    }

    /**
     * JWT 페이로드에서 role 을 꺼낸다. 로그인 응답에는 role 이 없고 토큰 안에만 있다(웹도 같다).
     * 서명은 검증하지 않는다 — 권한 판단은 서버가 하고, 여기서는 관리자 화면을 보여줄지만 정한다.
     */
    private fun roleFromToken(token: String): String = runCatching {
        val payload = token.split(".")[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        (Json.parseToJsonElement(json) as? JsonObject)
            ?.get("role")
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
    }.getOrNull() ?: "USER"
}
