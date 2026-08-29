package com.example.safelight.ui.map

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.FacilityCache
import com.example.safelight.data.net.CctvDto
import com.example.safelight.data.net.DangerZoneDto
import com.example.safelight.data.net.LocationDto
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.PlaceDocument
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.unwrap
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MapViewModel"

/** 백엔드 MapBounds.MAX_SPAN_DEGREE 와 같은 값. 이보다 넓게 요청하면 400 이다. */
const val MAX_SPAN_DEGREE = 0.5

/** 받아 둘 범위를 화면보다 이만큼 넓게 잡는다. */
private const val PAD_RATIO = 0.5

/** 지도에 보이는 영역. 카카오 SDK 타입을 그대로 쓰면 뷰모델이 지도에 묶이므로 값만 들고 다닌다. */
data class MapBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
) {
    fun contains(lat: Double, lng: Double) =
        lat in minLat..maxLat && lng in minLng..maxLng

    /** 이 범위가 [other] 를 통째로 품고 있는지. 받아 둔 범위를 다시 쓸 수 있는지 판단할 때. */
    fun contains(other: MapBounds) =
        minLat <= other.minLat && maxLat >= other.maxLat &&
            minLng <= other.minLng && maxLng >= other.maxLng

    /**
     * 백엔드가 한 번에 내주는 폭(MapBounds.MAX_SPAN_DEGREE)을 넘는지.
     * 넘으면 400 이 오고, 그만큼 넓게 보는 축척에서는 점을 그리지도 않는다.
     */
    fun isTooWide() =
        (maxLat - minLat) > MAX_SPAN_DEGREE || (maxLng - minLng) > MAX_SPAN_DEGREE

    /**
     * 화면보다 조금 넓게 받아 둔다. 지도를 살짝 끌 때마다 다시 요청하지 않기 위한 여유분이다.
     * 백엔드 상한을 넘지 않는 선까지만 넓힌다.
     */
    fun padded(): MapBounds {
        val latSpan = maxLat - minLat
        val lngSpan = maxLng - minLng
        val latPad = minOf(latSpan * PAD_RATIO / 2, maxOf(0.0, (MAX_SPAN_DEGREE - latSpan) / 2))
        val lngPad = minOf(lngSpan * PAD_RATIO / 2, maxOf(0.0, (MAX_SPAN_DEGREE - lngSpan) / 2))
        return MapBounds(
            minLat = minLat - latPad,
            minLng = minLng - lngPad,
            maxLat = maxLat + latPad,
            maxLng = maxLng + lngPad,
        )
    }

    /** 카카오 Local 의 rect 파라미터 형식(경도,위도,경도,위도). */
    fun toRect() = "$minLng,$minLat,$maxLng,$maxLat"

    /** 4분면으로 나눈다. 45곳에서 잘린 영역을 더 파고들 때 쓴다. */
    fun split(): List<MapBounds> {
        val midLat = (minLat + maxLat) / 2
        val midLng = (minLng + maxLng) / 2
        return listOf(
            MapBounds(minLat, minLng, midLat, midLng),
            MapBounds(minLat, midLng, midLat, maxLng),
            MapBounds(midLat, minLng, maxLat, midLng),
            MapBounds(midLat, midLng, maxLat, maxLng),
        )
    }
}

/** 지도에 그릴 편의점 하나. */
data class StorePlace(val id: String, val name: String, val latitude: Double, val longitude: Double)

/** 레이어 켬/끔. 웹 MainPage 의 `{ cctv: true, streetLamp: true, safeZone: true }` 와 같은 초기값이다. */
data class MapFilters(
    val cctv: Boolean = true,
    val streetLamp: Boolean = true,
    val safeZone: Boolean = true,
)

class MapViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var filters by mutableStateOf(MapFilters())
        private set

    var dangerZones by mutableStateOf<List<DangerZoneDto>>(emptyList())
        private set

    /** 위험구역을 다시 받는 중. 시트의 LIVE 칩이 '갱신중'으로 바뀐다(웹 isLoading). */
    var zonesLoading by mutableStateOf(false)
        private set

    /** 내 위치의 행정동. 못 찾으면 null 이고, 그때는 지역 표기를 생략한다. */
    var regionName by mutableStateOf<String?>(null)
        private set

    private var regionJob: Job? = null

    var visibleCctv by mutableStateOf<List<CctvDto>>(emptyList())
        private set

    var visibleLamps by mutableStateOf<List<LocationDto>>(emptyList())
        private set

    var visibleStores by mutableStateOf<List<StorePlace>>(emptyList())
        private set

    /** 상호를 띄울 만큼 확대했는지. 넓게 보면 라벨끼리 겹쳐 지도를 가린다. */
    var showStoreNames by mutableStateOf(false)
        private set

    var cctvNotice by mutableStateOf("")
        private set

    var lampNotice by mutableStateOf("")
        private set

    var storeNotice by mutableStateOf("")
        private set

    private var lastBounds: MapBounds? = null
    private var lastZoom: Int = INITIAL_ZOOM
    private var storeJob: Job? = null
    private var cctvJob: Job? = null
    private var zoneRefreshJob: Job? = null
    private var lampJob: Job? = null

    init {
        pollDangerZones()
    }

    /** 웹 useSafetyData 와 같이 30초마다 새로 받는다. */
    private fun pollDangerZones() = viewModelScope.launch {
        while (isActive) {
            fetchDangerZones()
            delay(30_000)
        }
    }

    private suspend fun fetchDangerZones() {
        zonesLoading = true
        runCatching { api.getDangerZones().unwrap() }
            .onSuccess { dangerZones = it.filter { zone -> zone.isActive } }
            .onFailure { Log.e(TAG, "위험구역 조회 실패", it) }
        zonesLoading = false
    }

    /**
     * 위험구역을 지금 바로 다시 읽는다. 긴급신고를 접수한 직후처럼 30초를 기다리면 안 되는
     * 순간에 부른다 — 신고 하나로 백엔드가 위험구역을 새로 만들거나 등급·신고수를 올리는데
     * (EmergencyReportService.createReport), 방금 내가 만든 구역이 30초 동안 지도에 없으면
     * 접수가 안 된 것처럼 보인다. 긴급 기능에서 제일 하면 안 되는 착각이다.
     *
     * 폴링 루프와는 별도 job 이라 겹쳐 불려도 요청은 한 번에 하나만 나간다.
     * (루프 job 은 앱이 사는 내내 active 라 그걸로는 막을 수 없다.)
     */
    fun refreshDangerZones() {
        if (zoneRefreshJob?.isActive == true) return
        zoneRefreshJob = viewModelScope.launch { fetchDangerZones() }
    }

    /**
     * 내 위치의 행정동 이름. 웹 useRegionName 자리다 —
     * 안전 현황 시트의 '반경 500m · 마포구 서교동' 뒷부분이다.
     *
     * 한 번만 찾는다. 지도를 옮길 때마다 다시 부르면 시트 제목이 계속 바뀌는데,
     * 이 줄이 말하는 것은 '내가 있는 곳'이지 '지금 보고 있는 곳'이 아니다(웹도 같다).
     */
    fun onLocationKnown(latitude: Double, longitude: Double) {
        if (regionName != null || regionJob?.isActive == true) return
        regionJob = viewModelScope.launch {
            val document = runCatching {
                Network.kakaoLocal
                    .coord2RegionCode(longitude = longitude.toString(), latitude = latitude.toString())
                    .documents
            }.getOrNull().orEmpty()
            // region_type 'H' = 행정동. 없으면 첫 결과(법정동)로 대신한다.
            val region = document.firstOrNull { it.regionType == "H" } ?: document.firstOrNull()
            regionName = region
                ?.let { listOf(it.depth2, it.depth3).filter(String::isNotBlank).joinToString(" ") }
                ?.takeIf { it.isNotBlank() }
        }
    }

    fun toggleFilter(key: String) {
        filters = when (key) {
            "cctv" -> filters.copy(cctv = !filters.cctv)
            "safeZone" -> filters.copy(safeZone = !filters.safeZone)
            "streetLamp" -> filters.copy(streetLamp = !filters.streetLamp)
            else -> return
        }
        lastBounds?.let { onCameraIdle(it, lastZoom) }
    }

    /** 지도가 멈출 때마다(웹의 'idle' 리스너 자리) 보이는 범위를 다시 받아 그린다. */
    fun onCameraIdle(bounds: MapBounds, zoom: Int) {
        lastBounds = bounds
        lastZoom = zoom
        refreshCctv(bounds, zoom)
        refreshLamps(bounds, zoom)
        refreshStores(bounds, zoom)
    }

    /**
     * 보이는 범위의 가로등을 받아 그린다.
     *
     * 예전에는 전국 목록을 한 번 받아 두고 메모리에서 걸렀다. 전국 데이터가 184만 건이 되면서
     * 그 방식이 없어졌고, 이제 화면이 멈출 때마다 그 범위만 받는다
     * (같은 범위 안이면 [FacilityCache] 가 네트워크를 타지 않는다).
     *
     * 실패를 '데이터 없음'과 구분하는 원칙은 그대로다 — 못 받았는데
     * '이 지역에는 가로등이 없습니다' 라고 하면 데이터가 없는 동네로 오해한다.
     */
    private fun refreshLamps(bounds: MapBounds, zoom: Int) {
        if (!filters.streetLamp) {
            visibleLamps = emptyList()
            lampNotice = ""
            return
        }
        // CCTV 보다 한 단계 더 확대해야 그린다(개수가 3배다 — LAMP_MIN_ZOOM).
        if (zoom < LAMP_MIN_ZOOM || bounds.isTooWide()) {
            visibleLamps = emptyList()
            lampNotice = "지도를 확대하면 주변 가로등이 표시됩니다"
            return
        }
        lampJob?.cancel()
        lampJob = viewModelScope.launch {
            val list = runCatching { FacilityCache.lamps.load(bounds) }
                .onFailure { Log.e(TAG, "가로등 조회 실패", it) }
                .getOrElse {
                    lampNotice = "가로등 정보를 불러오지 못했습니다"
                    return@launch
                }
            // 기다리는 사이 칩이 꺼졌으면 그리지 않는다.
            if (!filters.streetLamp) return@launch
            val inBounds = list.filter { bounds.contains(it.latitude, it.longitude) }
            visibleLamps = inBounds
            lampNotice =
                if (inBounds.isEmpty()) "이 지역에는 가로등이 없습니다" else "가로등 ${inBounds.size}개"
        }
    }

    private fun refreshCctv(bounds: MapBounds, zoom: Int) {
        if (!filters.cctv) {
            visibleCctv = emptyList()
            cctvNotice = ""
            return
        }
        if (zoom < FACILITY_MIN_ZOOM || bounds.isTooWide()) {
            visibleCctv = emptyList()
            cctvNotice = "지도를 확대하면 주변 CCTV가 표시됩니다"
            return
        }
        cctvJob?.cancel()
        cctvJob = viewModelScope.launch {
            val list = runCatching { FacilityCache.cctv.load(bounds) }
                .onFailure { Log.e(TAG, "CCTV 조회 실패", it) }
                .getOrElse {
                    cctvNotice = "CCTV 정보를 불러오지 못했습니다"
                    return@launch
                }
            if (!filters.cctv) return@launch
            val inBounds = list.filter { bounds.contains(it.latitude, it.longitude) }
            visibleCctv = inBounds
            cctvNotice =
                if (inBounds.isEmpty()) "이 지역에는 CCTV가 없습니다" else "CCTV ${inBounds.size}대"
        }
    }

    private fun refreshStores(bounds: MapBounds, zoom: Int) {
        // 이 시점 이후 도착하는 이전 검색 결과는 버린다 — 지도를 빠르게 움직이면 순서가 뒤집힌다.
        storeJob?.cancel()

        if (!filters.safeZone) {
            visibleStores = emptyList()
            storeNotice = ""
            return
        }
        if (zoom < FACILITY_MIN_ZOOM) {
            visibleStores = emptyList()
            storeNotice = "지도를 확대하면 주변 편의점이 표시됩니다"
            return
        }
        showStoreNames = zoom >= STORE_NAME_MIN_ZOOM

        storeJob = viewModelScope.launch {
            val result = collectStores(bounds, 0)
            if (result.failed) {
                visibleStores = emptyList()
                storeNotice = "편의점 정보를 불러오지 못했습니다"
                return@launch
            }
            visibleStores = result.places
            // 끝까지 쪼개고도 넘쳤다면 그 사실을 숨기지 않는다 — 실제로는 더 있다.
            storeNotice = when {
                result.places.isEmpty() -> "이 지역에는 편의점이 없습니다"
                result.capped -> "편의점 ${result.places.size}곳 이상 · 확대하면 더 정확합니다"
                else -> "편의점 ${result.places.size}곳"
            }
        }
    }

    private data class StoreResult(
        val places: List<StorePlace>,
        val capped: Boolean,
        val failed: Boolean,
    )

    /**
     * 45곳에서 잘린 영역만 4등분해 재귀로 파고든다. 마지막에 id 로 중복을 걷어낸다 —
     * 이웃한 조각은 경계를 공유하므로 경계 위의 편의점이 양쪽 결과에 다 들어온다.
     */
    private suspend fun collectStores(bounds: MapBounds, depth: Int): StoreResult {
        val area = searchArea(bounds)
        if (area.failed) return StoreResult(emptyList(), capped = false, failed = true)
        if (!area.capped || depth >= STORE_SPLIT_DEPTH) return area

        val parts = viewModelScope.async {
            bounds.split().map { async { collectStores(it, depth + 1) } }.awaitAll()
        }.await()

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
                if (it is kotlinx.coroutines.CancellationException) throw it
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
}
