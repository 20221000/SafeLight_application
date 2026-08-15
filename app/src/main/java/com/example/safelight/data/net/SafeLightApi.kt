package com.example.safelight.data.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 우리 Spring 백엔드. 응답은 모두 {success, data, message} 봉투에 들어온다([ApiEnvelope]).
 *
 * 웹의 대응 관계:
 *  - MapView.jsx 의 `fetch('/cctvs')`             → [getCctvs]
 *  - useSafetyData.js 의 `fetch('/danger-zones')` → [getDangerZones]
 *  - RoutePage.jsx 의 `fetch('/routes', POST)`    → [getRoutes]
 *  - RoutePage.jsx 의 `/bookmarks`·`/recent-routes` → 아래 나머지
 *
 * 실패 사유를 사용자에게 그대로 보여줘야 하는 것들은 [Response] 로 받는다 —
 * Retrofit 이 4xx 를 예외로 바꿔 버리면 봉투 안의 message 를 읽을 수 없다([errorMessage]).
 */
interface SafeLightApi {

    @GET("cctvs")
    suspend fun getCctvs(): ApiEnvelope<List<CctvDto>>

    /** 토큰이 없으면 빈 목록이 온다(웹도 토큰이 없으면 아예 요청하지 않는다). */
    @GET("danger-zones")
    suspend fun getDangerZones(): ApiEnvelope<List<DangerZoneDto>>

    /**
     * 안전 경로 탐색. 백엔드가 안전 점수 상위 경로를 최대 3개 돌려준다(개수는 백엔드가 정한다).
     * 로그인 상태면 백엔드가 이 검색을 최근 경로에 알아서 저장한다 — 앱이 따로 저장하지 않는다.
     */
    @POST("routes")
    suspend fun getRoutes(@Body body: RouteRequest): Response<ApiEnvelope<List<RouteDto>>>

    @GET("bookmarks")
    suspend fun getBookmarks(): ApiEnvelope<List<BookmarkDto>>

    @POST("bookmarks")
    suspend fun saveBookmark(@Body body: BookmarkRequest): Response<ApiEnvelope<BookmarkDto>>

    @DELETE("bookmarks/{id}")
    suspend fun deleteBookmark(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @GET("recent-routes")
    suspend fun getRecentRoutes(): ApiEnvelope<List<RouteHistoryDto>>

    @DELETE("recent-routes/{routeHistoryId}")
    suspend fun deleteRecentRoute(@Path("routeHistoryId") id: Long): Response<ApiEnvelope<JsonElement>>

    @DELETE("recent-routes/all")
    suspend fun deleteAllRecentRoutes(): Response<ApiEnvelope<JsonElement>>
}

@Serializable
data class CctvDto(
    val cctvId: Long = 0,
    val cctvName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val purpose: String = "",
)

@Serializable
data class DangerZoneDto(
    val dangerZoneId: Long = 0,
    val centerLatitude: Double = 0.0,
    val centerLongitude: Double = 0.0,
    /** 미터 */
    val radius: Double = 0.0,
    /** HIGH · MEDIUM · LOW */
    val dangerLevel: String = "LOW",
    val isActive: Boolean = true,
)

/** 위경도 한 쌍. 백엔드 LocationDto 와 같다(경로 좌표·시설 좌표에 두루 쓰인다). */
@Serializable
data class LocationDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

@Serializable
data class RouteRequest(
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
)

/**
 * 탐색된 경로 하나.
 *
 * [safetyScore] 는 CCTV 수 + 편의점 수의 합이다(백엔드 RouteService.analyzeSafetyData).
 * 'CCTV 개수'가 아니므로 화면에서도 '안전시설 n곳'이라고 적는다.
 */
@Serializable
data class RouteDto(
    val routeId: Int = 0,
    val path: List<LocationDto> = emptyList(),
    val safetyScore: Int = 0,
    val description: String = "",
    val cctvLocations: List<LocationDto> = emptyList(),
    val storeLocations: List<LocationDto> = emptyList(),
)

@Serializable
data class BookmarkDto(
    val id: Long = 0,
    val routeName: String = "",
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    val endLatitude: Double = 0.0,
    val endLongitude: Double = 0.0,
    val safetyScore: Int = 0,
)

@Serializable
data class BookmarkRequest(
    val routeName: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val safetyScore: Int,
)

/**
 * 최근 검색 경로. 백엔드가 자동 저장할 때 이름을 '최근 검색 경로'로 고정하므로
 * 목록에서는 도착지 좌표를 역지오코딩한 주소를 대신 보여준다(웹도 같다).
 *
 * [searchedAt] 은 `2026-08-06T08:35:12` 형태의 문자열이다.
 */
@Serializable
data class RouteHistoryDto(
    val routeHistoryId: Long = 0,
    val routeName: String = "",
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    val endLatitude: Double = 0.0,
    val endLongitude: Double = 0.0,
    val searchedAt: String = "",
)
