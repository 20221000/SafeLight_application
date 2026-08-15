package com.example.safelight.ui.route

import com.example.safelight.data.net.LocationDto
import com.kakao.vectormap.LatLng

/** 출발지·도착지 한 곳. 웹 RoutePage 의 `{ lat, lng, name }` 과 같다. */
data class RoutePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun toLatLng(): LatLng = LatLng.from(latitude, longitude)

    /** 이름이 아니라 좌표로 같은 곳인지 본다 — 같은 자리라도 이름은 나중에 주소로 바뀐다. */
    fun samePlace(other: RoutePlace?) =
        other != null && latitude == other.latitude && longitude == other.longitude
}

/**
 * 안내 중인 경로. 사용자가 '지도에서 경로 보기'를 누르면 만들어져 지도 화면이 그린다.
 *
 * 웹은 sessionStorage 에 담아 화면을 옮겨도 살아남게 했는데, 여기서는 탭 컨테이너
 * (SafeLightRoot)가 화면보다 오래 살기 때문에 거기에 두는 것으로 같은 효과를 낸다.
 * 앱을 껐다 켜면 사라지는 것도 웹(탭을 닫으면 정리된다)과 같다.
 */
data class ActiveRoute(
    val path: List<LocationDto>,
    val start: RoutePlace?,
    val destination: RoutePlace?,
    val safetyScore: Int,
)
