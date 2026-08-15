package com.example.safelight.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 웹 navItems.jsx 의 SVG 아이콘을 그대로 옮긴 것이다.
 * 원본은 24x24 뷰박스에 fill=none · stroke=currentColor · strokeWidth=1.9 · 둥근 캡이다.
 *
 * `<circle cx cy r>` 은 path 문법에 없어서 두 개의 반원 호로 폈다:
 *   M(cx-r) cy  a r,r 0 1,0 2r,0  a r,r 0 1,0 -2r,0
 *
 * 색은 Icon(tint=) 이 ColorFilter 로 덮으므로 여기선 검정으로 둔다.
 */
/** 하단 탭 아이콘의 굵기(navItems.jsx). 헤더 아이콘은 웹에서 2 라서 따로 넘긴다. */
private const val STROKE_WIDTH = 1.9f

private fun strokeIcon(
    name: String,
    vararg pathData: String,
    strokeWidth: Float = STROKE_WIDTH,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pathData.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object SafeIcons {
    /** 지도 — 핀 + 가운데 원 */
    val Map: ImageVector by lazy {
        strokeIcon(
            "Map",
            "M12 21s7-6.4 7-11a7 7 0 1 0-14 0c0 4.6 7 11 7 11z",
            "M9.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
        )
    }

    /** 경로 안내 — 두 점을 잇는 곡선 */
    val Route: ImageVector by lazy {
        strokeIcon(
            "Route",
            "M3.6 19a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0",
            "M15.6 5a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0",
            "M8.2 18.2C13 17 15 13 15.6 7.2",
        )
    }

    /** 커뮤니티 — 말풍선 */
    val Community: ImageVector by lazy {
        strokeIcon(
            "Community",
            "M21 11.5a8 8 0 0 1-8.5 7.9L4 21l1.6-4.2A8 8 0 1 1 21 11.5z",
        )
    }

    /** 내 정보 — 사람 */
    val MyInfo: ImageVector by lazy {
        strokeIcon(
            "MyInfo",
            "M8.4 8a3.6 3.6 0 1 0 7.2 0a3.6 3.6 0 1 0 -7.2 0",
            "M4.5 20a7.5 7.5 0 0 1 15 0",
        )
    }

    // 아래 셋은 지도 레이어 칩용이다. 웹 Icon.jsx 의 같은 이름 아이콘과 path 가 같다.

    /** CCTV — 벽걸이 감시카메라: 기울어진 본체 + 앞쪽 렌즈 후드 + 벽 브래킷 */
    val Cctv: ImageVector by lazy {
        strokeIcon(
            "Cctv",
            "M3.2 9.6 15.4 4.2 17.6 9.2 5.4 14.6Z",
            "M15.4 4.2 18.4 2.9 20.6 7.9 17.6 9.2",
            "M3.6 14.8v6.4",
            "M3.6 18h4c.9 0 1.6-.7 1.6-1.6v-3.5",
        )
    }

    /** 가로등 — 뾰족 지붕 + 처마 + 등피(사다리꼴), 가운데 세로선이 유리 칸막이 겸 기둥 */
    val StreetLamp: ImageVector by lazy {
        strokeIcon(
            "StreetLamp",
            "M12 2 7 7.2h10z",
            "M5.4 7.2h13.2",
            "M8 7.2 8.9 17.6h6.2L16 7.2",
            "M12 7.2v14.6",
        )
    }

    /** 편의점 — 차양(아래쪽 물결) + 양쪽 벽 + 출입구 */
    val Store: ImageVector by lazy {
        strokeIcon(
            "Store",
            "M6 4h12l3.5 5q-1.9 2.4-3.8 0q-1.9 2.4-3.8 0q-1.9 2.4-3.8 0q-1.9 2.4-3.8 0q-1.9 2.4-3.8 0L6 4z",
            "M4.6 10.6V20",
            "M19.4 10.6V20",
            "M9.4 20v-5.6h5.2V20",
        )
    }

    /** 현재 위치로 — 조준점(웹 MapView 의 인라인 SVG) */
    val Crosshair: ImageVector by lazy {
        strokeIcon(
            "Crosshair",
            "M8.8 12a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0",
            "M12 2v3M12 19v3M2 12h3M19 12h3",
        )
    }

    // 아래 셋은 헤더용이다. 웹 MobileShell / PlaceSearchBox 의 인라인 SVG 와 같고 굵기도 2 다.

    /** 로고 — 방패 + 체크 */
    val Shield: ImageVector by lazy {
        strokeIcon(
            "Shield",
            "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
            "M9 12l2 2 4-4",
            strokeWidth = 2f,
        )
    }

    /** 장소 검색 — 돋보기 */
    val Search: ImageVector by lazy {
        strokeIcon(
            "Search",
            "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0",
            "M21 21L16.5 16.5",
            strokeWidth = 2f,
        )
    }

    /** 야간 모드 — 초승달 */
    val Moon: ImageVector by lazy {
        strokeIcon(
            "Moon",
            "M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z",
            strokeWidth = 2f,
        )
    }

    // 아래는 로그인·회원가입 입력칸 아이콘이다(웹 LoginPage / RegisterPage 의 인라인 SVG).
    // 원본의 <rect x y w h rx> 는 path 문법에 없어서 모서리를 호로 편 것 말고는 같다.

    /** 아이디 — 사람 */
    val User: ImageVector by lazy {
        strokeIcon(
            "User",
            "M8.6 8a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0",
            "M5 20a7 7 0 0 1 14 0",
            strokeWidth = 2f,
        )
    }

    /** 닉네임 — 해시태그 */
    val Tag: ImageVector by lazy {
        strokeIcon(
            "Tag",
            "M4 9h16M4 15h16M10 3L8 21M16 3l-2 18",
            strokeWidth = 2f,
        )
    }

    /** 이메일 — 봉투 */
    val Mail: ImageVector by lazy {
        strokeIcon(
            "Mail",
            "M5 5H19A2 2 0 0 1 21 7V17A2 2 0 0 1 19 19H5A2 2 0 0 1 3 17V7A2 2 0 0 1 5 5Z",
            "M3 7l9 6 9-6",
            strokeWidth = 2f,
        )
    }

    /** 비밀번호 — 자물쇠 */
    val Lock: ImageVector by lazy {
        strokeIcon(
            "Lock",
            "M6 10H18A2 2 0 0 1 20 12V18A2 2 0 0 1 18 20H6A2 2 0 0 1 4 18V12A2 2 0 0 1 6 10Z",
            "M8 10V7a4 4 0 0 1 8 0v3",
            strokeWidth = 2f,
        )
    }

    /** 가입 완료 — 체크. 원본은 굵기 3 이다. */
    val Check: ImageVector by lazy {
        strokeIcon("Check", "M20 6L9 17l-5-5", strokeWidth = 3f)
    }

    // 아래는 경로 안내 화면(웹 RoutePage)에서 쓰는 것들이다. 웹 Icon.jsx 의 PATHS 와 같은 path 이며
    // 굵기도 그쪽 기본값(1.9)을 따른다.

    /** 위치 — 핀 + 가운데 원. 웹 'map-pin'. [Map] 과 같은 모양이라 별칭으로 둔다. */
    val MapPin: ImageVector get() = Map

    /** 최근 기록 — 시계 */
    val Clock: ImageVector by lazy {
        strokeIcon(
            "Clock",
            "M2.5 12a9.5 9.5 0 1 0 19 0a9.5 9.5 0 1 0 -19 0",
            "M12 7v5l3.5 2",
        )
    }

    /** 북마크 — 별 */
    val Star: ImageVector by lazy {
        strokeIcon("Star", "M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.9L12 17.8l-6.2 3.2L7 14.1l-5-4.9 6.9-1z")
    }

    /** 닫기·삭제 — X */
    val Close: ImageVector by lazy {
        strokeIcon("Close", "M18 6 6 18", "M6 6l12 12")
    }

    /** 수정 — 연필 */
    val Edit: ImageVector by lazy {
        strokeIcon(
            "Edit",
            "M11 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5",
            "M18.4 2.6a2 2 0 0 1 2.8 2.8L12.5 14 9 15l1-3.5z",
        )
    }

    /** 다시 검색 — 회전 화살표 */
    val Refresh: ImageVector by lazy {
        strokeIcon(
            "Refresh",
            "M3 12a9 9 0 0 1 9-9 9.8 9.8 0 0 1 6.7 2.7L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.8 9.8 0 0 1-6.7-2.7L3 16",
            "M3 21v-5h5",
        )
    }

    /** 안내 시작 — 삼각형. 웹도 fill 없이 선으로만 그린다. */
    val Play: ImageVector by lazy {
        strokeIcon("Play", "M7 5v14l11-7z")
    }

    /** 안내 중 배너 — 나침반 */
    val Compass: ImageVector by lazy {
        strokeIcon(
            "Compass",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            // 원본은 <polygon> 이라 마지막에 Z 로 닫아 준다.
            "M16.2 7.8 14.1 14.1 7.8 16.2 9.9 9.9Z",
        )
    }
}
