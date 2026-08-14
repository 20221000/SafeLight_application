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
private const val STROKE_WIDTH = 1.9f

private fun strokeIcon(name: String, vararg pathData: String): ImageVector =
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
                strokeLineWidth = STROKE_WIDTH,
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
}
