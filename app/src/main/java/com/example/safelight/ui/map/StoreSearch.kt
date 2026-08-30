package com.example.safelight.ui.map

import android.util.Log
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.PlaceDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private const val TAG = "StoreSearch"

/**
 * 편의점(안전거점) 조회 — 지도 화면과 경로 화면이 같이 쓴다.
 *
 * 백엔드에는 편의점 단독 엔드포인트가 없다. KakaoLocalService.getConvenienceStores 는
 * POST /routes 안에서 '경로 50m 반경'으로만 쓰여 '지금 보이는 지도 영역' 질문에는 답할 수 없다.
 * 그래서 백엔드가 쓰는 것과 같은 소스(카카오 로컬, category_group_code=CS2)를 직접 조회한다.
 *
 * MapViewModel 안에 있던 것을 그대로 옮겼다. 경로 화면도 CCTV·가로등과 함께 편의점을 깔아야 하는데,
 * 뷰모델 안에 갇혀 있으면 같은 재귀 분할 로직을 한 벌 더 쓰게 된다.
 * 뷰모델의 scope 를 쓰던 자리는 coroutineScope 로 바꿨다 — 부르는 쪽 코루틴에 그대로 매달리므로
 * 화면이 사라지면 이 검색도 같이 취소된다.
 */
data class StoreResult(
    val places: List<StorePlace>,
    val capped: Boolean,
    val failed: Boolean,
)

/**
 * 45곳에서 잘린 영역만 4등분해 재귀로 파고든다. 마지막에 id 로 중복을 걷어낸다 —
 * 이웃한 조각은 경계를 공유하므로 경계 위의 편의점이 양쪽 결과에 다 들어온다.
 */
suspend fun collectStores(bounds: MapBounds, depth: Int = 0): StoreResult {
    val area = searchArea(bounds)
    if (area.failed) return StoreResult(emptyList(), capped = false, failed = true)
    if (!area.capped || depth >= STORE_SPLIT_DEPTH) return area

    val parts = coroutineScope {
        bounds.split().map { async { collectStores(it, depth + 1) } }.awaitAll()
    }

    val byId = LinkedHashMap<String, StorePlace>()
    parts.forEach { part -> part.places.forEach { byId[it.id] = it } }
    return StoreResult(
        places = byId.values.toList(),
        capped = parts.any { it.capped },
        failed = parts.all { it.failed },
    )
}

/** 한 영역을 끝까지(최대 3페이지) 훑는다. */
private suspend fun searchArea(bounds: MapBounds): StoreResult {
    val found = LinkedHashMap<String, StorePlace>()
    var page = 1
    while (true) {
        val response = runCatching {
            Network.kakaoLocal.searchCategory(
                categoryGroupCode = STORE_CATEGORY,
                rect = bounds.toRect(),
                page = page,
            )
        }.getOrElse {
            // 지도를 빠르게 움직이면 이전 검색이 취소된다. 그건 실패가 아니라 '이미 필요 없어진 일'이라
            // 그대로 위로 던져 코루틴이 정상적으로 끝나게 둔다 — 삼키면 '불러오지 못했습니다'가 잘못 뜬다.
            if (it is CancellationException) throw it
            Log.e(TAG, "편의점 조회 실패", it)
            return StoreResult(emptyList(), capped = false, failed = true)
        }

        response.documents.forEach { it.toStore()?.let { s -> found[s.id] = s } }
        if (response.meta.isEnd || page >= 3) break
        page++
    }
    return StoreResult(
        places = found.values.toList(),
        capped = found.size >= STORE_PAGE_CAP,
        failed = false,
    )
}

/** 카카오는 경도를 x, 위도를 y 로 준다(위경도 순서가 뒤집혀 있다). */
private fun PlaceDocument.toStore(): StorePlace? {
    val lat = y.toDoubleOrNull() ?: return null
    val lng = x.toDoubleOrNull() ?: return null
    return StorePlace(id = id, name = placeName, latitude = lat, longitude = lng)
}
