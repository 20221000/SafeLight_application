package com.example.safelight.data.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.Response

/**
 * 백엔드가 모든 응답을 이 형태로 감싼다. 웹의 readEnvelope() 와 같은 자리다.
 *
 * `{ "success": true, "data": {...}, "message": "..." }`
 *
 * data 가 null 인 응답(삭제 등)도 있어서 nullable 로 둔다.
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
)

/** 봉투를 벗겨 data 를 돌려준다. success=false 면 message 를 담아 던진다. */
fun <T> ApiEnvelope<T>.unwrap(): T {
    if (!success) throw ApiException(message ?: "요청에 실패했습니다.")
    return data ?: throw ApiException(message ?: "응답 본문이 비어 있습니다.")
}

class ApiException(message: String) : RuntimeException(message)

/**
 * 실패 응답(4xx/5xx)의 봉투에서 message 만 꺼낸다.
 * 백엔드는 실패도 봉투로 돌려주므로 그 문구를 그대로 보여주는 게 사용자에게 가장 정확하다.
 * 본문이 비어 있거나 봉투가 아니면 [fallback] 을 쓴다.
 */
fun Response<*>.errorMessage(fallback: String): String {
    val body = runCatching { errorBody()?.string() }.getOrNull()
    if (body.isNullOrBlank()) return fallback
    val message: String? = runCatching {
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body) as? JsonObject
        (root?.get("message") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()
    return message?.takeIf { it.isNotBlank() } ?: fallback
}
