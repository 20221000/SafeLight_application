package com.example.safelight.ui.map

/**
 * 지도의 마지막 카메라 위치.
 *
 * Navigation Compose 는 탭을 떠나면 그 화면의 컴포지션을 버린다. MapScreen 의 MapView 도 같이 사라져서
 * 돌아올 때마다 지도 엔진이 새로 뜨고 화면이 초기 좌표로 튄다.
 * MapView 자체를 살려두면 Activity 컨텍스트를 붙잡아 새는 쪽이 더 위험하므로,
 * 좌표·확대 수준만 SafeLightRoot 수명에 얹어두고 다시 들어올 때 그 자리에서 시작한다.
 */
class MapCameraState(
    // 웹 MapView.jsx 의 초기 center 와 같은 값이다.
    var latitude: Double = 37.4979,
    var longitude: Double = 127.0276,
    // 안드로이드 v2 는 숫자가 클수록 확대다(웹 JS SDK 와 반대).
    var zoom: Int = 16,
)
