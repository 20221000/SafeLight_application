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

/** 본문이 비어 있을 때 상태코드로 짐작하는 문구. 웹 apiResponse.js 의 STATUS_MESSAGE 와 같다. */
private fun statusMessage(status: Int): String? = when (status) {
    400 -> "요청 내용을 확인해주세요."
    401 -> "로그인이 필요합니다."
    403 -> "접근 권한이 없습니다. 로그인 후 다시 시도해주세요."
    404 -> "요청한 정보를 찾을 수 없습니다."
    409 -> "이미 처리된 요청입니다."
    500 -> "서버에 문제가 발생했습니다. 잠시 후 다시 시도해주세요."
    else -> null
}

/**
 * 실패 응답(4xx/5xx)에서 사용자에게 보여줄 문구를 꺼낸다. 웹 readEnvelope 와 같은 순서로 본다.
 *
 * 실패 봉투는 최상위 message 가 null 이고 진짜 문구는 `error.message` 에 담겨 온다
 * (`{"success":false,"data":null,"message":null,"error":{"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}}`).
 * 그쪽을 먼저 보지 않으면 모든 실패가 호출한 쪽의 뭉뚱그린 문구로 덮인다.
 */
fun Response<*>.errorMessage(fallback: String): String {
    val body = runCatching { errorBody()?.string() }.getOrNull()
    val message: String? = if (body.isNullOrBlank()) null else runCatching {
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body) as? JsonObject
        val nested = (root?.get("error") as? JsonObject)?.get("message") as? JsonPrimitive
        nested?.contentOrNull ?: (root?.get("message") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()
    return message?.takeIf { it.isNotBlank() } ?: statusMessage(code()) ?: fallback
}
