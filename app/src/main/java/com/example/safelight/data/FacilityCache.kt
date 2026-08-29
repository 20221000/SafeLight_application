package com.example.safelight.data

import android.util.Log
import com.example.safelight.data.net.CctvDto
import com.example.safelight.data.net.LocationDto
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.unwrap
import com.example.safelight.ui.map.MapBounds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "FacilityCache"

/**
 * 지도 위 안전시설(CCTV·가로등)을 '지금 보이는 범위'만 받아 둔다.
 *
 * 예전 CctvCache 는 전국 목록을 앱에서 한 벌 통째로 들고 있었다. 백엔드가 서울 CSV 대신
 * 전국 공공데이터를 쓰게 되면서 CCTV 25만·가로등 184만 건이 되었고, 전체 조회는
 * 각각 5.7MB / 80MB 라 더는 쓸 수 없다(백엔드도 범위를 안 주면 400 을 준다).
 *
 * 지도 화면과 경로 안내 화면이 둘 다 이걸 쓰므로 화면 수명보다 오래 사는 자리에 둔다.
 * 웹의 utils/facilityApi.js 와 같은 규칙이다.
 */
class BoundsCache<T>(
    private val label: String,
    private val fetch: suspend (MapBounds) -> List<T>,
) {

    /**
     * 요청을 한 번에 하나만 내보낸다. 지도를 빠르게 끌면 응답 순서가 뒤집혀
     * 옛 결과가 나중에 캐시를 덮어쓰는데, 줄을 세우면 그 문제가 생기지 않는다.
     * 뒤에 밀린 요청은 대개 앞 요청이 채워 둔 캐시에 걸려 네트워크를 타지 않는다.
     */
    private val mutex = Mutex()
    private var cachedBounds: MapBounds? = null
    private var cached: List<T> = emptyList()

    /**
     * 실패는 빈 목록으로 뭉뚱그리지 않고 예외로 올린다.
     * 부르는 쪽이 '아직 못 받음'과 '이 지역에 없음'을 구분해야 하기 때문이다.
     */
    suspend fun load(bounds: MapBounds): List<T> = mutex.withLock {
        cachedBounds?.let { if (it.contains(bounds)) return cached }

        val target = bounds.padded()
        val data = fetch(target)

        cachedBounds = target
        cached = data
        Log.d(TAG, "$label ${data.size}건 로드")
        data
    }
}

object FacilityCache {

    private val api: SafeLightApi by lazy { Network.backend(SafeLightApi::class.java) }

    val cctv = BoundsCache<CctvDto>("CCTV") { bounds ->
        api.getCctvs(
            minLatitude = bounds.minLat,
            maxLatitude = bounds.maxLat,
            minLongitude = bounds.minLng,
            maxLongitude = bounds.maxLng,
        ).unwrap()
    }

    val lamps = BoundsCache<LocationDto>("가로등") { bounds ->
        api.getSecurityLights(
            minLatitude = bounds.minLat,
            maxLatitude = bounds.maxLat,
            minLongitude = bounds.minLng,
            maxLongitude = bounds.maxLng,
        ).unwrap()
    }
}
