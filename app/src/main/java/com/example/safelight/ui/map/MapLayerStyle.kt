package com.example.safelight.ui.map

import androidx.compose.ui.graphics.Color

/**
 * 웹 `src/components/Map/layerStyle.js` 를 옮긴 것이다.
 * 한쪽만 바꾸면 웹과 앱의 지도가 서로 다른 규칙으로 그려진다 — 값을 고칠 땐 양쪽을 같이 고친다.
 */

// 레이어별 점 색. 내 위치가 파란 원이라 CCTV 도 파랑이면 둘을 구분할 수 없다 —
// CCTV 빨강 / 가로등 노랑 / 편의점 초록으로 나눠 파랑은 '내 위치' 전용으로 남긴다.
object LayerColor {
    val cctv = Color(0xFFE11D48)
    val streetLamp = Color(0xFFF59E0B)
    val store = Color(0xFF10B981)
}

/**
 * 확대 레벨 — 웹과 방향이 **반대**다.
 * 웹 카카오 JS SDK 는 level 1 이 최대 확대라 숫자가 커질수록 넓게 보이고,
 * 안드로이드 v2 는 zoomLevel 숫자가 커질수록 확대된다.
 * 그래서 웹의 `level <= N` 조건은 여기서 `zoom >= N'` 이 된다.
 *
 * 두 축을 잇는 상수는 기기에서 실측해 정했다(2026-08-15, SM-S948N · 1440px · density 3.5).
 * 한 화면의 가로 폭을 재 보니 안드로이드는 한 단계마다 정확히 2배씩 움직였다:
 *
 *     zoom  13     14     15     16     17     18
 *     m/dp  12.25  6.13   3.06   1.53   0.75   0.38
 *
 * 웹은 layerStyle.js 에 강남역 실측치가 남아 있다 — 모바일 L4 가 375px 폭에서 0.75km,
 * 즉 **2.0 m/px** 다(세로 1.39km 로 계산해도 같은 값이 나온다).
 *
 * 여기서 알게 된 것: 두 축 모두 한 단계가 2배지만 **서로 정확히 겹치지는 않는다**.
 * 웹 L4(2.0)는 안드로이드 z15(3.06)와 z16(1.53) 사이에 떨어진다. 정수 zoom 으로는
 * 딱 맞출 수 없으므로 가까운 쪽인 z16 을 골랐다 — 조금 더 확대된 쪽이라 한 화면에 뜨는
 * 점이 웹보다 적고, 이는 '점이 지도를 덮지 않게 한다'는 웹의 결정과 같은 방향이다.
 * (z15 를 고르면 웹보다 1.5배 넓어져 점이 더 많아진다.)
 */
private const val WEB_LEVEL_TO_ZOOM = 20

private fun fromWebLevel(webLevel: Int) = WEB_LEVEL_TO_ZOOM - webLevel

/**
 * 시설 점(CCTV·편의점)을 그리는 최소 확대. 이보다 넓게 보면 아예 그리지 않는다.
 * 웹의 `FACILITY_MAX_LEVEL = 4` 와 같은 넓이다.
 *
 * 웹에서 모바일만 한 단계 넓혀 봤다가 되돌린 기록이 있다(2026-08-07) — 폰 화면은 세로로 길어
 * 한 단계만 넓혀도 덮는 면적이 데스크탑보다 커지고, 점이 지도를 덮어 길이 안 보였다.
 * 다시 넓히려면 점 크기·개수부터 손봐야 한다.
 */
val FACILITY_MIN_ZOOM = fromWebLevel(4)

/**
 * 편의점 상호를 띄우는 최소 확대. 웹의 `STORE_NAME_MAX_LEVEL = 2` 와 같다.
 * 이보다 넓게 보면 라벨 폭(상호 길이만큼 늘어난다)이 서로 겹쳐 지도의 다른 정보를 가린다.
 */
val STORE_NAME_MIN_ZOOM = fromWebLevel(2)

/** 지도를 열 때의 자리 — 웹 MapView 의 초기 center/level 과 같다. */
const val INITIAL_LATITUDE = 37.4979
const val INITIAL_LONGITUDE = 127.0276
val INITIAL_ZOOM = FACILITY_MIN_ZOOM

/** 상단 장소 검색에서 고른 곳으로 이동할 때의 확대(웹은 `setLevel(3)`). */
val SEARCH_ZOOM = fromWebLevel(3)

/** 편의점 = 카카오 카테고리 CS2. 백엔드 KakaoLocalService 가 쓰는 것과 같은 소스다. */
const val STORE_CATEGORY = "CS2"

/**
 * 카카오 카테고리 검색은 한 영역에서 15곳 × 3페이지 = 45곳까지만 준다(우리 한도가 아니다).
 * 45곳이 꽉 찼다는 건 실제로는 더 있다는 뜻이므로, 그 영역만 4등분해 다시 검색한다.
 * 꽉 차지 않은 영역은 더 쪼개지 않으므로 한산한 동네에서는 요청 수가 그대로다.
 */
const val STORE_PAGE_CAP = 45

/** 4² = 최대 16조각. 여기까지 쪼개도 넘치면 그 사실을 문구로 알린다. */
const val STORE_SPLIT_DEPTH = 2

/**
 * 경로 화면(웹 RoutePage)에서 고른 경로를 그리는 색.
 * 출발은 파랑, 도착은 빨강이고 선은 출발 색을 쓴다(strokeWeight 6 · opacity .9).
 */
object RouteColor {
    val start = Color(0xFF2563EB)
    val destination = Color(0xFFE11D48)

    /**
     * 안내가 시작된 뒤 지도 화면에 그리는 색은 따로다 — 웹 MapView.drawRouteOnMap 이
     * 초록 선(#00E676)에 초록 출발·빨강(#FF3B3B) 도착을 쓴다. 경로 화면에서 '고른 경로'와
     * 지도에서 '안내 중인 경로'를 색으로 구분하려는 것이라 웹 그대로 둔다.
     */
    val activeLine = Color(0xFF00E676)
    val activeDestination = Color(0xFFFF3B3B)

    /** 웹은 안내 중 출발 마커의 글자만 검정이다(초록 배경 위 흰 글씨는 읽히지 않는다). */
    val activeStartText = Color.Black
}

/** 경로 선 굵기(웹 strokeWeight 6). */
const val ROUTE_LINE_WIDTH_DP = 6f

/** 경로 위 안전시설 점 크기. 웹 RoutePage 는 지도 화면(11)보다 한 단계 작은 9 를 쓴다. */
const val ROUTE_DOT_SIZE_DP = 9f

/** 위험구역 원 색 — 웹 MapView 의 LEVEL_COLOR, tokens.js 의 LEVEL_HEX 와 같은 값이다. */
fun dangerLevelColor(level: String): Color = when (level) {
    "HIGH" -> Color(0xFFE11D48)
    "MEDIUM" -> Color(0xFFF59E0B)
    else -> Color(0xFF10B981)
}
