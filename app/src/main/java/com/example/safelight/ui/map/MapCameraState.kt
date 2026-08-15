package com.example.safelight.ui.map

/**
 * 지도 카메라. 탭을 오가면 지도 화면의 컴포지션이 통째로 사라지므로,
 * 화면보다 오래 사는 곳(SafeLightRoot)에 이걸 두고 마지막으로 보던 자리를 넘겨받는다.
 * 초기값은 웹 MapView 의 초기 center/level 과 같다.
 */
class MapCameraState(
    var latitude: Double = INITIAL_LATITUDE,
    var longitude: Double = INITIAL_LONGITUDE,
    var zoom: Int = INITIAL_ZOOM,
)
