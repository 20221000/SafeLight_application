package com.example.safelight.data.net

import com.example.safelight.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * 통신 진입점. 서버가 둘이라 Retrofit 인스턴스도 둘이다.
 *  - [backend]     우리 Spring 서버. {success,data,message} 봉투 + Bearer 토큰.
 *  - [kakaoLocal]  카카오 Local REST. 웹에서 kakao.maps.services 가 하던 장소검색·역지오코딩을 대신한다.
 *                  (안드로이드 지도 SDK 에는 services 에 해당하는 기능이 없다.)
 */
object Network {

    // 백엔드가 필드를 추가해도 앱이 깨지지 않도록 모르는 키는 무시한다.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val converter = json.asConverterFactory("application/json".toMediaType())

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    /** 로그인 후 저장된 토큰을 모든 요청에 붙인다. 웹의 `Authorization: Bearer ${token}` 과 같다. */
    private val authInterceptor = Interceptor { chain ->
        val token = TokenStore.accessToken
        val request = if (token.isNullOrBlank()) chain.request()
        else chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        chain.proceed(request)
    }

    private fun client(vararg interceptors: Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .apply { interceptors.forEach { addInterceptor(it) } }
            .addInterceptor(logging)
            .build()

    private val backendRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client(authInterceptor))
            .addConverterFactory(converter)
            .build()
    }

    private val kakaoRetrofit: Retrofit by lazy {
        // 카카오 Local 은 Bearer 가 아니라 `KakaoAK {REST API 키}` 형식이다.
        val kakaoAuth = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")
                    .build()
            )
        }
        Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com/")
            .client(client(kakaoAuth))
            .addConverterFactory(converter)
            .build()
    }

    fun <T> backend(service: Class<T>): T = backendRetrofit.create(service)

    val kakaoLocal: KakaoLocalApi by lazy { kakaoRetrofit.create(KakaoLocalApi::class.java) }
}
