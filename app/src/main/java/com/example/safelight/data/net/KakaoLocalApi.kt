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
data class PlaceDocument(
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
