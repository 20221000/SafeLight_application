package com.example.safelight.ui.map

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.CctvCache
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

/** 가로등 목록을 다시 받아 볼 횟수. 자세한 이유는 MapViewModel.ensureLamps. */
private const val MAX_LAMP_ATTEMPTS = 3

/** 지도에 보이는 영역. 카카오 SDK 타입을 그대로 쓰면 뷰모델이 지도에 묶이므로 값만 들고 다닌다. */
data class MapBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
) {
    fun contains(lat: Double, lng: Double) =
        lat in minLat..maxLat && lng in minLng..maxLng

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

    /**
     * 가로등 목록을 실제로 받아왔는지 = 칩을 열어도 되는지.
     * 못 받으면 잠근 채로 둔다 — 켤 수 없는 칩을 켜지게 해두면 눌러도 아무 일이 없어 고장으로 보인다.
     */
    var lampReady by mutableStateOf(false)
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

    /**
     * CCTV 전체 목록. 백엔드에 '영역으로 조회'가 없어 웹과 똑같이 한 번 다 받아 두고
     * 화면이 움직일 때마다 메모리에서 걸러 낸다([CctvCache] 가 앱 전체에서 한 벌만 들고 있다).
     */
    private var allCctv: List<CctvDto> = emptyList()
    private var lastBounds: MapBounds? = null
    private var lastZoom: Int = INITIAL_ZOOM
    private var storeJob: Job? = null
    private var cctvJob: Job? = null
    private var zoneRefreshJob: Job? = null
    private var allLamps: List<LocationDto> = emptyList()
    private var lampJob: Job? = null
    private var lampFailures = 0

    init {
        ensureCctv()
        ensureLamps()
        pollDangerZones()
    }

    /**
     * 가로등 전체 목록. [ensureCctv] 와 같은 이유로 지도가 멈출 때마다 다시 시도한다.
     *
     * 실패와 '엔드포인트 없음'을 빈 목록으로 뭉뚱그리지 않는다. 둘을 같게 다루면 아직 안 만들어진
     * 기능이 '이 지역에는 가로등이 없습니다' 로 보인다 — 서울 한복판에서 그 문구가 뜨면
     * 데이터가 없는 줄 알게 된다. 못 받은 동안에는 [lampReady] 가 false 로 남아 칩이 잠기고
     * 안내 문구도 띄우지 않는다(웹 MapView 와 같은 처리).
     */
    private fun ensureLamps() {
        if (allLamps.isNotEmpty() || lampJob?.isActive == true) return
        // CCTV 와 달리 횟수를 제한한다. /cctvs 는 있는 엔드포인트라 실패가 대개 일시적이지만,
        // /security-lights 는 아직 백엔드에 없어서(2026-08-21 현재 500) 지도를 움직일 때마다
        // 영영 다시 부르게 된다 — 실제로 앱을 켠 지 10초 만에 세 번 나갔다. 세 번이면
        // 서버가 늦게 뜨는 정도는 넘기고, 그 뒤로는 조용히 포기한다(다시 켜면 처음부터 센다).
        if (lampFailures >= MAX_LAMP_ATTEMPTS) return
        lampJob = viewModelScope.launch {
            val list = runCatching { api.getSecurityLights().unwrap() }
                .onFailure { Log.e(TAG, "가로등 조회 실패", it) }
                .getOrNull()
                .orEmpty()
            if (list.isEmpty()) {
                lampFailures++
                return@launch
            }
            allLamps = list
            lampReady = true
            lastBounds?.let { refreshLamps(it, lastZoom) }
        }
    }

    /**
     * CCTV 전체 목록을 확보한다. 이미 들고 있거나 받는 중이면 아무것도 하지 않는다.
     *
     * **다시 시도할 길이 있어야 한다.** 예전에는 이걸 init 에서 한 번만 불렀는데, 그 한 번이
     * 실패하면 — 서버가 아직 안 떴거나, 4만 건짜리 응답이 앱을 켤 때 몰리는 다른 요청들과
     * 겹쳐 끊기거나 — 그걸로 끝이었다. 그러면 지도에 CCTV 가 영영 안 나오는데 화면에는
     * '이 지역에는 CCTV가 없습니다' 라고 떠서, 서울 한복판에서도 데이터가 없는 것처럼 보였다.
     * 게다가 경로 화면은 뷰모델이 나중에(탭을 처음 열 때) 만들어지는 바람에 그쪽만 멀쩡해
     * 보여서, 지도 쪽 데이터만 없는 것처럼 읽혔다.
     *
     * 지도가 멈출 때마다 부른다 — 마침 목록이 필요해지는 시점이다. [CctvCache] 는 성공한 것만
     * 기억하므로 한 번 받아 둔 뒤의 호출은 네트워크를 타지 않고, 실패해서 계속 비어 있는 동안에도
     * cctvJob 때문에 요청은 한 번에 하나만 나간다.
     */
    private fun ensureCctv() {
        if (allCctv.isNotEmpty() || cctvJob?.isActive == true) return
        cctvJob = viewModelScope.launch {
            allCctv = CctvCache.all()
            if (allCctv.isNotEmpty()) lastBounds?.let { refreshCctv(it, lastZoom) }
        }
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
            // 목록을 못 받았으면 켤 것이 없다. 화면도 이때는 칩을 잠가 둔다(lampReady).
            "streetLamp" -> if (lampReady) filters.copy(streetLamp = !filters.streetLamp) else return
            else -> return
        }
        lastBounds?.let { onCameraIdle(it, lastZoom) }
    }

    /** 지도가 멈출 때마다(웹의 'idle' 리스너 자리) 보이는 것만 다시 계산한다. */
    fun onCameraIdle(bounds: MapBounds, zoom: Int) {
        lastBounds = bounds
        lastZoom = zoom
        // 앱을 켤 때 CCTV 목록을 못 받았으면 여기서 다시 받는다(자세한 이유는 ensureCctv).
        ensureCctv()
        ensureLamps()
        refreshCctv(bounds, zoom)
        refreshLamps(bounds, zoom)
        refreshStores(bounds, zoom)
    }

    private fun refreshLamps(bounds: MapBounds, zoom: Int) {
        // 목록을 못 받았으면 문구도 띄우지 않는다 — 아직 못 받아온 것과 진짜 없는 것은 다르다.
        if (!lampReady || !filters.streetLamp) {
            visibleLamps = emptyList()
            lampNotice = ""
            return
        }
        // CCTV 보다 한 단계 더 확대해야 그린다(개수가 3배다 — LAMP_MIN_ZOOM).
        if (zoom < LAMP_MIN_ZOOM) {
            visibleLamps = emptyList()
            lampNotice = "지도를 확대하면 주변 가로등이 표시됩니다"
            return
        }
        val inBounds = allLamps.filter { bounds.contains(it.latitude, it.longitude) }
        visibleLamps = inBounds
        lampNotice =
            if (inBounds.isEmpty()) "이 지역에는 가로등이 없습니다" else "가로등 ${inBounds.size}개"
    }

    private fun refreshCctv(bounds: MapBounds, zoom: Int) {
        if (!filters.cctv) {
            visibleCctv = emptyList()
            cctvNotice = ""
            return
        }
        if (zoom < FACILITY_MIN_ZOOM) {
            visibleCctv = emptyList()
            cctvNotice = "지도를 확대하면 주변 CCTV가 표시됩니다"
            return
        }
        val inBounds = allCctv.filter { bounds.contains(it.latitude, it.longitude) }
        visibleCctv = inBounds
        cctvNotice =
            if (inBounds.isEmpty()) "이 지역에는 CCTV가 없습니다" else "CCTV ${inBounds.size}대"
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
