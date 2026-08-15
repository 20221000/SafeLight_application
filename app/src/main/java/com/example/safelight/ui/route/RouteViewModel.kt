package com.example.safelight.ui.route

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.CctvCache
import com.example.safelight.data.net.BookmarkDto
import com.example.safelight.data.net.BookmarkRequest
import com.example.safelight.data.net.CctvDto
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.RouteDto
import com.example.safelight.data.net.RouteHistoryDto
import com.example.safelight.data.net.RouteRequest
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.errorMessage
import com.example.safelight.ui.map.FACILITY_MIN_ZOOM
import com.example.safelight.ui.map.MapBounds
import com.example.safelight.ui.search.SearchedPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "RouteViewModel"

/** 출발지를 어떻게 정할지. 웹 RoutePage 의 startMode 와 같다. */
enum class StartMode { Current, Search }

/** 도착지 탭. 웹 destMode. */
enum class DestMode { Recent, Search }

/** 북마크 정렬. 백엔드에 생성 시각이 없어 '최신'은 id 순(= 저장한 순서)으로 본다. */
enum class BookmarkSort { Recent, Name }

/** 화면에 띄울 경로 하나 — 백엔드 응답에 순위 라벨을 붙인 것이다. */
data class RankedRoute(val rank: Int, val route: RouteDto) {
    val safetyScore get() = route.safetyScore
    val cctvCount get() = route.cctvLocations.size
    val storeCount get() = route.storeLocations.size
}

/**
 * 웹 RoutePage.jsx 의 상태와 통신을 옮긴 것이다.
 *
 * 웹은 실패를 `alert()` 로 알리지만 안드로이드에는 그런 게 없다 — [message] 에 담아
 * 화면이 스낵바로 띄운다. 조용히 삼키지 않는 것이 목적이라 문구는 웹과 같게 뒀다.
 */
class RouteViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    // ── 출발지 ────────────────────────────────────────────────────────────────
    var startMode by mutableStateOf(StartMode.Current)
        private set
    var currentLocation by mutableStateOf<RoutePlace?>(null)
        private set

    /** 현재 위치의 사람이 읽는 주소(역지오코딩). 없으면 좌표를 대신 보여준다. */
    var startAddress by mutableStateOf("")
        private set
    var startQuery by mutableStateOf("")
        private set
    var startResults by mutableStateOf<List<SearchedPlace>>(emptyList())
        private set
    var selectedStart by mutableStateOf<RoutePlace?>(null)
        private set

    // ── 도착지 ────────────────────────────────────────────────────────────────
    var destMode by mutableStateOf(DestMode.Search)
        private set
    var destQuery by mutableStateOf("")
        private set
    var destResults by mutableStateOf<List<SearchedPlace>>(emptyList())
        private set
    var selectedDest by mutableStateOf<RoutePlace?>(null)
        private set

    // ── 북마크 · 최근 경로 ─────────────────────────────────────────────────────
    var bookmarks by mutableStateOf<List<BookmarkDto>>(emptyList())
        private set
    var bookmarkQuery by mutableStateOf("")
        private set
    var bookmarkSort by mutableStateOf(BookmarkSort.Recent)
        private set

    /** 경로를 다시 받아오는 중인 북마크. 그동안 다른 북마크를 누르지 못하게 한다. */
    var bookmarkBusyId by mutableStateOf<Long?>(null)
        private set
    var recentRoutes by mutableStateOf<List<RouteHistoryDto>>(emptyList())
        private set

    /** routeHistoryId → 도착지 주소. 백엔드가 이름을 '최근 검색 경로'로 고정 저장하기 때문이다. */
    var recentLabels by mutableStateOf<Map<Long, String>>(emptyMap())
        private set

    // ── 결과 ─────────────────────────────────────────────────────────────────
    var routes by mutableStateOf<List<RankedRoute>>(emptyList())
        private set
    var selectedRoute by mutableStateOf<RankedRoute?>(null)
        private set
    var isSearched by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set

    /** 한 번 보여주고 지우는 알림. 화면이 [messageShown] 으로 비운다. */
    var message by mutableStateOf<String?>(null)
        private set

    /**
     * 지도를 여기로 옮겨 달라는 요청. 웹이 장소를 고를 때마다 부르던 centerOnVisible 자리다.
     * 화면이 옮기고 나면 [cameraTargetHandled] 로 비운다 — 남겨 두면 지도를 움직일 때마다
     * 다시 그 자리로 끌려간다.
     */
    var cameraTarget by mutableStateOf<RoutePlace?>(null)
        private set

    fun cameraTargetHandled() {
        cameraTarget = null
    }

    /** 지도 배경에 깔 CCTV — 지금 화면 안에 있고 충분히 확대했을 때만. */
    var visibleCctv by mutableStateOf<List<CctvDto>>(emptyList())
        private set

    private var allCctv: List<CctvDto> = emptyList()
    private var lastBounds: MapBounds? = null
    private var lastZoom: Int = FACILITY_MIN_ZOOM

    private var startSearchJob: Job? = null
    private var destSearchJob: Job? = null

    init {
        viewModelScope.launch {
            allCctv = CctvCache.all()
            lastBounds?.let { refreshCctv(it, lastZoom) }
        }
        loadBookmarks()
        loadRecentRoutes()
    }

    fun messageShown() {
        message = null
    }

    // ── 지도 ─────────────────────────────────────────────────────────────────

    fun onCameraIdle(bounds: MapBounds, zoom: Int) {
        lastBounds = bounds
        lastZoom = zoom
        refreshCctv(bounds, zoom)
    }

    private fun refreshCctv(bounds: MapBounds, zoom: Int) {
        // 웹 RoutePage 도 지도 화면과 같은 규칙을 쓴다 — 화면 안에 있는 것만, 이 확대부터.
        visibleCctv =
            if (zoom < FACILITY_MIN_ZOOM) emptyList()
            else allCctv.filter { bounds.contains(it.latitude, it.longitude) }
    }

    // ── 출발지 ────────────────────────────────────────────────────────────────

    fun selectStartMode(mode: StartMode) {
        startMode = mode
        if (mode == StartMode.Current) startResults = emptyList()
    }

    /** 화면이 GPS 로 알아낸 현재 위치를 넘겨준다(웹은 navigator.geolocation 자리). */
    fun onCurrentLocation(latitude: Double, longitude: Double) {
        val here = RoutePlace("현재 위치", latitude, longitude)
        val firstFix = currentLocation == null
        currentLocation = here
        if (startMode == StartMode.Current) {
            selectedStart = here
            // 처음 위치를 잡았을 때만 지도를 옮긴다. 그 뒤에도 계속 옮기면 사용자가 다른 곳을
            // 보려고 지도를 움직일 때마다 화면을 뺏는다.
            if (firstFix && selectedRoute == null) cameraTarget = here
        }
        // 주소를 알아내면 출발지 이름도 주소로 바꾼다 — 북마크 이름이 '현재 위치 → …' 처럼
        // 나중에 알아볼 수 없는 이름으로 저장되는 걸 막는다.
        viewModelScope.launch {
            val address = reverseGeocode(latitude, longitude)
            if (address.isBlank()) return@launch
            startAddress = address
            // 그사이 사용자가 다른 곳을 고를 수 있으니, 아직 이 좌표를 보고 있을 때만 이름을 바꾼다.
            currentLocation = currentLocation?.let { if (it.samePlace(here)) it.copy(name = address) else it }
            selectedStart = selectedStart?.let { if (it.samePlace(here)) it.copy(name = address) else it }
        }
    }

    /**
     * '현재 위치'가 실제와 다를 때(GPS 오차·실내 등) 직접 고칠 수 있게 한다.
     * 검색 모드로 넘기면서 지금 주소를 미리 채워 넣고 후보까지 띄운다.
     */
    fun editStart() {
        val seed = startAddress.ifBlank { startQuery }
        startMode = StartMode.Search
        onStartQueryChange(seed)
    }

    fun onStartQueryChange(value: String) {
        startQuery = value
        startSearchJob?.cancel()
        if (value.isBlank()) {
            startResults = emptyList()
            return
        }
        startSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            startResults = searchPlaces(value)
        }
    }

    fun selectStart(place: SearchedPlace) {
        selectStart(RoutePlace(place.name, place.latitude, place.longitude))
    }

    fun selectStart(place: RoutePlace) {
        startSearchJob?.cancel()
        selectedStart = place
        startResults = emptyList()
        startQuery = place.name
        cameraTarget = place
        invalidateResultsIfSegmentChanged()
    }

    // ── 도착지 ────────────────────────────────────────────────────────────────

    fun selectDestMode(mode: DestMode) {
        destMode = mode
    }

    fun onDestQueryChange(value: String) {
        destQuery = value
        destSearchJob?.cancel()
        if (value.isBlank()) {
            destResults = emptyList()
            return
        }
        destSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            destResults = searchPlaces(value)
        }
    }

    fun selectDest(place: SearchedPlace) {
        selectDest(RoutePlace(place.name, place.latitude, place.longitude))
    }

    fun selectDest(place: RoutePlace) {
        destSearchJob?.cancel()
        selectedDest = place
        destResults = emptyList()
        destQuery = place.name
        cameraTarget = place
        invalidateResultsIfSegmentChanged()
    }

    // ── 경로 탐색 ─────────────────────────────────────────────────────────────

    fun searchRoute() {
        val start = selectedStart
        val dest = selectedDest
        if (start == null || dest == null) {
            message = "출발지와 도착지를 설정해주세요."
            return
        }
        if (loading) return
        loading = true
        viewModelScope.launch {
            val found = requestRoutes(start, dest)
            loading = false
            if (found.isEmpty()) return@launch
            showRoutes(found, start, dest)
            // 백엔드가 이번 검색을 최근 경로에 저장했으므로 목록을 새로 받는다.
            loadRecentRoutes()
        }
    }

    fun selectRoute(route: RankedRoute) {
        selectedRoute = route
    }

    /** '다시 검색' — 결과를 버리고 출발지를 현재 위치로 되돌린다(웹과 같다). */
    fun resetSearch() {
        clearResults()
        selectedDest = null
        destQuery = ""
        startQuery = ""
        startMode = StartMode.Current
        // 이미 Current 모드였다면 위치 조회가 다시 돌지 않으므로 알고 있는 현재 위치를 직접 되살린다.
        selectedStart = currentLocation
    }

    /**
     * 요청 한 곳. 경로 검색과 북마크가 같은 응답 형태를 쓰므로 공유한다.
     * 실패하면 백엔드 문구를 그대로 [message] 에 담는다 — '경로 없음'과 '권한 없음'은 다르다.
     */
    private suspend fun requestRoutes(start: RoutePlace, dest: RoutePlace): List<RankedRoute> {
        val response = runCatching {
            api.getRoutes(
                RouteRequest(
                    startLatitude = start.latitude,
                    startLongitude = start.longitude,
                    endLatitude = dest.latitude,
                    endLongitude = dest.longitude,
                ),
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            Log.e(TAG, "경로 검색 실패", it)
            message = "경로 검색에 실패했습니다."
            return emptyList()
        }

        if (!response.isSuccessful) {
            message = response.errorMessage("경로를 찾을 수 없습니다.")
            return emptyList()
        }
        val body = response.body()
        val found = body?.data.orEmpty()
        if (found.isEmpty()) {
            message = body?.message?.takeIf { it.isNotBlank() } ?: "경로를 찾을 수 없습니다."
            return emptyList()
        }
        // 백엔드가 점수 순으로 준다는 보장이 없어 여기서 정렬한다(웹도 같다).
        return found.sortedByDescending { it.safetyScore }
            .mapIndexed { index, route -> RankedRoute(rank = index + 1, route = route) }
    }

    /** 결과를 화면에 올린다. 어느 구간의 결과인지 함께 적어 둔다([invalidateResultsIfSegmentChanged]). */
    private fun showRoutes(found: List<RankedRoute>, start: RoutePlace, dest: RoutePlace) {
        resultSegment = segmentKey(start, dest)
        routes = found
        selectedRoute = found.first()
        isSearched = true
    }

    private fun clearResults() {
        resultSegment = ""
        routes = emptyList()
        selectedRoute = null
        isSearched = false
    }

    /**
     * 출발·도착이 바뀌면 이전 결과는 더 이상 이 구간의 것이 아니다 — 통째로 버린다.
     * 안 버리면 마커만 새 위치로 옮겨가고 경로선·추천 목록은 옛 구간 그대로 남는다.
     *
     * [resultSegment] 는 '지금 띄워둔 결과가 어느 구간의 것인지'다. 북마크는 구간과 결과를
     * 한 번에 바꾸므로, 이게 없으면 방금 띄운 결과를 곧바로 지워 버린다.
     */
    private var resultSegment: String = ""

    private fun segmentKey(start: RoutePlace?, dest: RoutePlace?) =
        "${start?.latitude},${start?.longitude}|${dest?.latitude},${dest?.longitude}"

    private fun invalidateResultsIfSegmentChanged() {
        if (segmentKey(selectedStart, selectedDest) == resultSegment) return
        clearResults()
    }

    // ── 북마크 ────────────────────────────────────────────────────────────────

    fun onBookmarkQueryChange(value: String) {
        bookmarkQuery = value
    }

    fun selectBookmarkSort(sort: BookmarkSort) {
        bookmarkSort = sort
    }

    /** 검색어 + 정렬을 적용한 목록. 목록 API 가 id DESC 로 주므로 '최신'도 같은 기준이다. */
    val visibleBookmarks: List<BookmarkDto>
        get() {
            val query = bookmarkQuery.trim()
            val filtered =
                if (query.isEmpty()) bookmarks
                else bookmarks.filter { it.routeName.contains(query, ignoreCase = true) }
            return when (bookmarkSort) {
                BookmarkSort.Name -> filtered.sortedBy { it.routeName }
                BookmarkSort.Recent -> filtered.sortedByDescending { it.id }
            }
        }

    private fun loadBookmarks() = viewModelScope.launch {
        runCatching { api.getBookmarks() }
            .onSuccess { bookmarks = it.data.orEmpty() }
            .onFailure { Log.e(TAG, "북마크 조회 실패", it) }
    }

    fun saveBookmark() {
        val route = selectedRoute ?: return
        val start = selectedStart ?: return
        val dest = selectedDest ?: return
        viewModelScope.launch {
            val response = runCatching {
                api.saveBookmark(
                    BookmarkRequest(
                        routeName = "${start.name} → ${dest.name}",
                        startLatitude = start.latitude,
                        startLongitude = start.longitude,
                        endLatitude = dest.latitude,
                        endLongitude = dest.longitude,
                        safetyScore = route.safetyScore,
                    ),
                )
            }.getOrElse {
                if (it is CancellationException) throw it
                Log.e(TAG, "북마크 저장 실패", it)
                message = "저장에 실패했습니다."
                return@launch
            }
            if (response.isSuccessful) {
                message = "북마크에 저장되었습니다."
                loadBookmarks()
            } else {
                message = response.errorMessage("저장에 실패했습니다.")
            }
        }
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch {
        runDelete({ api.deleteBookmark(id) }, "북마크 삭제 실패") { loadBookmarks() }
    }

    /**
     * 북마크를 누르면 이 화면에 경로를 바로 띄운다 — 안내 시작은 사용자가 직접 누른다.
     * 북마크에는 출발·도착 좌표만 있고 경로 선은 없어서 경로는 다시 받아야 한다.
     */
    fun openBookmark(bookmark: BookmarkDto) {
        if (bookmarkBusyId != null) return
        val names = bookmark.routeName.split(" → ")
        val start = RoutePlace(
            names.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "출발지",
            bookmark.startLatitude,
            bookmark.startLongitude,
        )
        val dest = RoutePlace(
            names.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "도착지",
            bookmark.endLatitude,
            bookmark.endLongitude,
        )
        bookmarkBusyId = bookmark.id
        // 출발·도착을 먼저 채운다. 경로를 못 받더라도 처음부터 다시 넣지 않아도 되고,
        // 받아 오는 동안 어디→어디를 준비 중인지 보인다.
        selectedStart = start
        selectedDest = dest
        startQuery = start.name
        destQuery = dest.name
        startMode = StartMode.Search
        destMode = DestMode.Search
        clearPlaceResults()

        viewModelScope.launch {
            val found = requestRoutes(start, dest)
            bookmarkBusyId = null
            if (found.isEmpty()) return@launch
            loadRecentRoutes()
            showRoutes(found, start, dest)
        }
    }

    // ── 최근 경로 ─────────────────────────────────────────────────────────────

    private fun loadRecentRoutes() = viewModelScope.launch {
        runCatching { api.getRecentRoutes() }
            .onSuccess {
                recentRoutes = it.data.orEmpty()
                labelRecentRoutes()
            }
            .onFailure {
                // 로그인 전에는 401 이 온다 — 알림까지 띄울 일은 아니다.
                Log.d(TAG, "최근 경로 조회 실패: ${it.message}")
                recentRoutes = emptyList()
            }
    }

    private fun labelRecentRoutes() = viewModelScope.launch {
        val pairs = recentRoutes.map { it.routeHistoryId to reverseGeocode(it.endLatitude, it.endLongitude) }
        recentLabels = pairs.filter { it.second.isNotBlank() }.toMap()
    }

    fun openRecentRoute(history: RouteHistoryDto) {
        val start = RoutePlace("출발지", history.startLatitude, history.startLongitude)
        val dest = RoutePlace(
            recentLabels[history.routeHistoryId] ?: "도착지",
            history.endLatitude,
            history.endLongitude,
        )
        selectedStart = start
        selectedDest = dest
        startQuery = start.name
        destQuery = dest.name
        startMode = StartMode.Search
        clearPlaceResults()
        invalidateResultsIfSegmentChanged()

        // 출발지 주소는 목록에 없으니 이때 한 번 더 조회해 이름을 채운다.
        viewModelScope.launch {
            val address = reverseGeocode(history.startLatitude, history.startLongitude)
            if (address.isBlank()) return@launch
            selectedStart = selectedStart?.let { if (it.samePlace(start)) it.copy(name = address) else it }
            if (selectedStart?.name == address) startQuery = address
        }
    }

    fun deleteRecentRoute(routeHistoryId: Long) = viewModelScope.launch {
        runDelete({ api.deleteRecentRoute(routeHistoryId) }, "최근 경로 삭제 실패") { loadRecentRoutes() }
    }

    fun clearRecentRoutes() = viewModelScope.launch {
        runDelete({ api.deleteAllRecentRoutes() }, "최근 경로 전체 삭제 실패") { loadRecentRoutes() }
    }

    /**
     * 삭제 요청 공통. Retrofit 이 [Response] 로 주는 4xx 는 예외가 아니라서
     * `runCatching` 만으로는 실패가 성공으로 보인다 — 상태 코드를 직접 본다.
     */
    private suspend fun runDelete(
        request: suspend () -> retrofit2.Response<*>,
        logLabel: String,
        onDeleted: () -> Unit,
    ) {
        val response = runCatching { request() }.getOrElse {
            if (it is CancellationException) throw it
            Log.e(TAG, logLabel, it)
            message = "삭제에 실패했습니다."
            return
        }
        if (response.isSuccessful) onDeleted()
        else message = response.errorMessage("삭제에 실패했습니다.")
    }

    /**
     * 띄워 둔 장소 후보를 접는다. 북마크·최근 경로는 출발·도착을 한 번에 채우는데,
     * 이걸 안 하면 직전 검색어의 후보 목록이 새 도착지 밑에 그대로 남는다.
     */
    private fun clearPlaceResults() {
        startSearchJob?.cancel()
        destSearchJob?.cancel()
        startResults = emptyList()
        destResults = emptyList()
    }

    // ── 카카오 Local ──────────────────────────────────────────────────────────

    private suspend fun searchPlaces(keyword: String): List<SearchedPlace> = runCatching {
        Network.kakaoLocal.searchKeyword(query = keyword).documents.map {
            SearchedPlace(
                name = it.placeName,
                address = it.roadAddress.ifBlank { it.address },
                latitude = it.y.toDoubleOrNull() ?: 0.0,
                longitude = it.x.toDoubleOrNull() ?: 0.0,
            )
        // 웹도 출발·도착 후보는 4개까지만 띄운다.
        }.take(PLACE_RESULT_LIMIT)
    }.getOrElse {
        if (it is CancellationException) throw it
        Log.e(TAG, "장소 검색 실패", it)
        emptyList()
    }

    /** 좌표 → 도로명(없으면 지번) 주소. 웹의 `Geocoder.coord2Address` 자리다. */
    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String = runCatching {
        val document = Network.kakaoLocal
            .coord2Address(longitude = longitude.toString(), latitude = latitude.toString())
            .documents.firstOrNull()
        document?.roadAddress?.addressName?.ifBlank { null }
            ?: document?.address?.addressName.orEmpty()
    }.getOrElse {
        if (it is CancellationException) throw it
        Log.e(TAG, "주소 조회 실패", it)
        ""
    }

    private companion object {
        /** 글자마다 요청을 내보내지 않는다(헤더 검색과 같은 이유·같은 값). */
        const val SEARCH_DEBOUNCE_MS = 250L
        const val PLACE_RESULT_LIMIT = 4
    }
}
