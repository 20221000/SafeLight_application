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

    // 아래는 커뮤니티 화면(웹 CommunityPage · PostDetailPage · PostWritePage)에서 쓰는 것들이다.

    /** 조회수 — 눈 */
    val Eye: ImageVector by lazy {
        strokeIcon(
            "Eye",
            "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        )
    }

    /** 좋아요 — 하트 */
    val Heart: ImageVector by lazy {
        strokeIcon(
            "Heart",
            "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21.2l7.8-7.8 1-1a5.5 5.5 0 0 0 0-7.8z",
        )
    }

    /** 댓글 — 말풍선. 하단 탭의 [Community] 와 달리 웹 'message' path 그대로다. */
    val Message: ImageVector by lazy {
        strokeIcon("Message", "M21 11.5a8 8 0 0 1-8.5 7.9L4 21l1.6-4.2A8 8 0 1 1 21 11.5z")
    }

    /** 인기글 — 불꽃 */
    val Flame: ImageVector by lazy {
        strokeIcon(
            "Flame",
            "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1.1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z",
        )
    }

    /** 조회 실패 — 경고 삼각형 */
    val AlertTriangle: ImageVector by lazy {
        strokeIcon(
            "AlertTriangle",
            "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h16.9a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z",
            "M12 9v4",
            "M12 17h.01",
        )
    }

    /** 첨부파일 — 클립 */
    val Paperclip: ImageVector by lazy {
        strokeIcon(
            "Paperclip",
            "M21.4 11.1l-9.2 9.2a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5",
        )
    }

    /** 파일 */
    val File: ImageVector by lazy {
        strokeIcon("File", "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6")
    }

    /** 공유 — 사슬 */
    val Link: ImageVector by lazy {
        strokeIcon(
            "Link",
            "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.8 1.7",
            "M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7",
        )
    }

    /** 글쓰기 FAB — 더하기. 웹은 이 자리만 굵기 2.2 다. */
    val Plus: ImageVector by lazy {
        strokeIcon("Plus", "M12 5v14", "M5 12h14", strokeWidth = 2.2f)
    }

    /** 뒤로 — 왼쪽 꺾쇠. 웹 '목록으로' 버튼과 같은 굵기 2 다. */
    val ChevronLeft: ImageVector by lazy {
        strokeIcon("ChevronLeft", "M15 18l-6-6 6-6", strokeWidth = 2f)
    }

    /** 정렬 드롭다운 — 아래 꺾쇠 */
    val ChevronDown: ImageVector by lazy {
        strokeIcon("ChevronDown", "M6 9l6 6 6-6", strokeWidth = 2.4f)
    }

    /** 삭제 — 휴지통 */
    val Trash: ImageVector by lazy {
        strokeIcon(
            "Trash",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
            "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6",
            strokeWidth = 2f,
        )
    }

    /** 더 들어가는 줄 끝의 오른쪽 꺾쇠. 웹 '내 정보' 카드 줄들과 같은 굵기 2 다. */
    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight", "M9 6l6 6-6 6", strokeWidth = 2f)
    }

    /** 로그아웃 — 문 밖으로 나가는 화살표 */
    val LogOut: ImageVector by lazy {
        strokeIcon(
            "LogOut",
            "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4",
            "M10 17l5-5-5-5",
            "M15 12H3",
            strokeWidth = 2f,
        )
    }

    /** 화면 제목 왼쪽의 뒤로가기 화살표. 웹 쪽지함·알림함 헤더와 같다. */
    val ArrowLeft: ImageVector by lazy {
        strokeIcon("ArrowLeft", "M19 12H5", "M12 19l-7-7 7-7", strokeWidth = 2f)
    }

    /** 보내기 — 종이비행기 */
    val Send: ImageVector by lazy {
        strokeIcon("Send", "M4 12l16-8-6 16-2.5-6.5z", strokeWidth = 2.2f)
    }

    /** 안내 — 동그라미 안의 느낌표 */
    val Info: ImageVector by lazy {
        strokeIcon(
            "Info",
            "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
            "M12 11v5",
            "M12 8h.01",
            strokeWidth = 2f,
        )
    }

    /** 긴급 신고 사이렌. 웹 Icon.jsx 의 'siren' 과 같은 경로다. */
    val Siren: ImageVector by lazy {
        strokeIcon(
            "Siren",
            "M7 18v-6a5 5 0 0 1 10 0v6",
            "M4 21h16",
            "M12 2v1.5",
            "M21 12h-1.5",
            "M4.5 12H3",
            "M18.4 5.6l-1 1",
            "M5.6 5.6l1 1",
        )
    }

    /** 일반 화면으로 — 집 */
    val Home: ImageVector by lazy {
        strokeIcon(
            "Home",
            "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
            "M9 22V12h6v10",
            strokeWidth = 2f,
        )
    }

    // ── 관리자 콘솔 탭 ──────────────────────────────────────────────────────────
    // 웹 adminNavItems.jsx 의 ADMIN_ICONS 와 같은 그림이다.
    // `<rect rx>` 은 path 문법에 없어서 모서리를 호로 편 것이라 길지만 좌표는 원본 그대로다.

    /** 대시보드 — 크기가 다른 네 칸 */
    val AdminDashboard: ImageVector by lazy {
        strokeIcon(
            "AdminDashboard",
            "M4.5 3h4a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-6a1.5 1.5 0 0 1 1.5-1.5z",
            "M15.5 3h4a1.5 1.5 0 0 1 1.5 1.5v2a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-2a1.5 1.5 0 0 1 1.5-1.5z",
            "M15.5 12h4a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-6a1.5 1.5 0 0 1 1.5-1.5z",
            "M4.5 16h4a1.5 1.5 0 0 1 1.5 1.5v2a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-2a1.5 1.5 0 0 1 1.5-1.5z",
        )
    }

    /** 신고 관리 — 접힌 문서 */
    val AdminReports: ImageVector by lazy {
        strokeIcon(
            "AdminReports",
            "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z",
            "M14 2v6h6",
            "M9 13h6",
            "M9 17h4",
        )
    }

    /** 사용자 관리 — 두 사람 */
    val AdminUsers: ImageVector by lazy {
        strokeIcon(
            "AdminUsers",
            "M5.6 8a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0",
            "M3.5 20a5.5 5.5 0 0 1 11 0",
            "M16 11a3 3 0 0 0 0-6",
            "M18.5 20a5.5 5.5 0 0 0-3-4.9",
        )
    }

    /** 공지 — 확성기 */
    val AdminNotices: ImageVector by lazy {
        strokeIcon(
            "AdminNotices",
            "M3 11l18-5v12L3 14v-3z",
            "M11.6 16.8A3 3 0 0 1 6 15.5",
        )
    }

    /** 알림 벨 */
    val Bell: ImageVector by lazy {
        strokeIcon(
            "Bell",
            "M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9",
            "M13.7 21a2 2 0 0 1-3.4 0",
        )
    }
}
