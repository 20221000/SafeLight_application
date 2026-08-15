
package com.example.safelight.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 카카오 Local REST API.
 *
 * 웹의 대응 관계:
 *  - PlaceSearchBox / RoutePage 의 `kakao.maps.services.Places.keywordSearch` → [searchKeyword]
 *  - geocode.js / useRegionName 의 `Geocoder.coord2Address`                   → [coord2Address]
 *  - useRegionName 의 `Geocoder.coord2RegionCode`                             → [coord2RegionCode]
 *
 * 주의: 카카오는 경도를 x, 위도를 y 로 받는다(위경도 순서가 뒤집혀 있다).
 */
interface KakaoLocalApi {

    @GET("v2/local/search/keyword.json")
    suspend fun searchKeyword(
        @Query("query") query: String,
        @Query("x") longitude: String? = null,
        @Query("y") latitude: String? = null,
        @Query("radius") radiusMeters: Int? = null,
        @Query("size") size: Int = 15,
    ): KeywordSearchResponse

    /**
     * 카테고리 검색. 웹 MapView 의 `Places.categorySearch(STORE_CATEGORY, ..., { bounds })` 자리다.
     *
     * [rect] 은 `경도,위도,경도,위도` 네 값이며 카카오가 알아서 정규화한다
     * (min/max 순서를 바꿔 넣어도 결과가 같은 것을 확인했다, 2026-08-15).
     * 한 요청은 15곳 × 3페이지 = 45곳까지만 준다 — 우리 한도가 아니라 카카오의 한도다.
     */
    @GET("v2/local/search/category.json")
    suspend fun searchCategory(
        @Query("category_group_code") categoryGroupCode: String,
        @Query("rect") rect: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 15,
    ): CategorySearchResponse

    @GET("v2/local/geo/coord2address.json")
    suspend fun coord2Address(
        @Query("x") longitude: String,
        @Query("y") latitude: String,
    ): Coord2AddressResponse

    @GET("v2/local/geo/coord2regioncode.json")
    suspend fun coord2RegionCode(
        @Query("x") longitude: String,
        @Query("y") latitude: String,
    ): Coord2RegionResponse
}

@Serializable
data class KeywordSearchResponse(val documents: List<PlaceDocument> = emptyList())

@Serializable
data class CategorySearchResponse(
    val documents: List<PlaceDocument> = emptyList(),
    val meta: CategoryMeta = CategoryMeta(),
)

@Serializable
data class CategoryMeta(
    /** 마지막 페이지인지. false 면 page 를 올려 더 받는다. */
    @SerialName("is_end") val isEnd: Boolean = true,
    @SerialName("pageable_count") val pageableCount: Int = 0,
    @SerialName("total_count") val totalCount: Int = 0,
)

@Serializable
data class PlaceDocument(
    /** 인접한 사각형을 따로 검색하면 경계 위의 장소가 양쪽에 다 들어온다 — 이 id 로 걷어낸다. */
    val id: String = "",
    @SerialName("place_name") val placeName: String = "",
    @SerialName("road_address_name") val roadAddress: String = "",
    @SerialName("address_name") val address: String = "",
    @SerialName("category_group_code") val categoryGroupCode: String = "",
    val x: String = "",  // 경도
    val y: String = "",  // 위도
)

@Serializable
data class Coord2AddressResponse(val documents: List<Coord2AddressDocument> = emptyList())

@Serializable
data class Coord2AddressDocument(
    @SerialName("road_address") val roadAddress: RoadAddress? = null,
    val address: LotAddress? = null,
)

@Serializable
data class RoadAddress(@SerialName("address_name") val addressName: String = "")

@Serializable
data class LotAddress(@SerialName("address_name") val addressName: String = "")

@Serializable
data class Coord2RegionResponse(val documents: List<RegionDocument> = emptyList())

@Serializable
data class RegionDocument(
    @SerialName("region_type") val regionType: String = "",
    @SerialName("region_1depth_name") val depth1: String = "",
    @SerialName("region_2depth_name") val depth2: String = "",
    @SerialName("region_3depth_name") val depth3: String = "",
)
