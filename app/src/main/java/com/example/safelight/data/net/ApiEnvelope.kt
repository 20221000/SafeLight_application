package com.example.safelight.data.net

import kotlinx.serialization.Serializable

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
