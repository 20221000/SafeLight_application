package com.example.safelight.data

import android.util.Log
import com.example.safelight.data.net.CctvDto
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.unwrap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "CctvCache"

/**
 * CCTV 전체 목록을 앱에서 한 번만 받아 들고 있는다.
 *
 * 백엔드에 '영역으로 조회'가 없어 웹도 통째로 받아 두고 화면에서 걸러 낸다.
 * 지도 화면과 경로 안내 화면이 둘 다 이 목록을 쓰는데, 화면마다 따로 받으면
 * 4만 건이 넘는 응답(2026-08-15 기준 41,905건)을 탭을 옮길 때마다 다시 내려받게 된다.
 *
 * 화면 수명보다 오래 사는 값이라 뷰모델이 아니라 여기 둔다.
 */
object CctvCache {

    private val api: SafeLightApi by lazy { Network.backend(SafeLightApi::class.java) }
    private val mutex = Mutex()
    private var cached: List<CctvDto>? = null

    /** 실패하면 빈 목록을 돌려주고 캐시에 남기지 않는다 — 다음에 다시 시도한다. */
    suspend fun all(): List<CctvDto> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: runCatching { api.getCctvs().unwrap() }
                .onSuccess {
                    cached = it
                    Log.d(TAG, "CCTV ${it.size}건 로드")
                }
                .onFailure { Log.e(TAG, "CCTV 조회 실패", it) }
                .getOrDefault(emptyList())
        }
    }
}
