package com.example.safelight.data.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 로그인·회원가입. 웹 LoginPage / RegisterPage / App.jsx 가 부르던 것과 같은 엔드포인트다.
 *
 * 실패 응답도 봉투로 오고 그 안의 message 를 사용자에게 보여줘야 하므로,
 * Retrofit 이 예외로 바꿔 버리지 않도록 [Response] 로 받아 본문을 직접 읽는다.
 */
interface AuthApi {

    @POST("users/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<LoginResponse>>

    @POST("users/register")
    suspend fun register(@Body body: RegisterRequest): Response<ApiEnvelope<JsonElement>>

    @POST("users/logout")
    suspend fun logout(): Response<ApiEnvelope<JsonElement>>

    /** 저장된 토큰이 아직 유효한지. 401/403 이면 만료된 것이다. */
    @GET("users/auth-check")
    suspend fun authCheck(): Response<ApiEnvelope<JsonElement>>
}

@Serializable
data class LoginRequest(val usernameOrEmail: String, val password: String)

@Serializable
data class LoginResponse(
    val accessToken: String = "",
    val userId: Long = 0,
    val username: String = "",
)

@Serializable
data class RegisterRequest(
    val username: String,
    val nickname: String,
    val email: String,
    val password: String,
)
