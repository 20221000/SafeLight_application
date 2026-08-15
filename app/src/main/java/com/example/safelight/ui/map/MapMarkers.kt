package com.example.safelight.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser

/**
 * 지도 위 마커를 비트맵으로 그린다.
 *
 * 웹은 `CustomOverlay` 에 HTML 을 넣어 CSS 로 그렸지만 안드로이드 지도에는 HTML 오버레이가 없다.
 * `LabelStyle.from(Bitmap)` 만 받으므로 같은 모양을 Canvas 로 다시 그린다 —
 * 그래서 여기 있는 숫자는 전부 웹 MapView.jsx / layerStyle.js 의 CSS 값을 그대로 옮긴 것이다.
 * 값을 고칠 땐 웹의 해당 style 도 같이 고쳐야 두 화면이 갈리지 않는다.
 *
 * CSS px 를 그대로 쓰면 고해상도 폰에서 좁쌀만 하게 보이므로 [density] 를 곱해 실제 픽셀로 그린다.
 * 이미 픽셀 단위로 그렸으니 라벨 쪽에서는 `setApplyDpScale(false)` 로 추가 확대를 막는다.
 */
class MapMarkers(context: Context) {

    private val density = context.resources.displayMetrics.density

    private fun Float.px() = this * density
    private fun Int.px() = this * density

    /** 아이콘 원본은 24x24 뷰박스다(웹 Icon.jsx). */
    private fun iconPath(pathData: String, sizePx: Float): Path {
        val path = PathParser.createPathFromPathData(pathData)
        val scale = sizePx / 24f
        path.transform(Matrix().apply { setScale(scale, scale) })
        return path
    }

    private fun textPaint(sizeSp: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizeSp.px()
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)   // font-weight:700
    }

    // ── 시설 점 ────────────────────────────────────────────────────────────────
    // 웹 layerStyle.js 의 dotContent():
    //   width/height 11px · border-radius 50% · border 2px solid #fff
    //   box-shadow 0 0 0 1px rgba(15,23,42,.2)
    // 흰 테두리를 둘러 어떤 지도 배경에서도 색이 살아 있게 한다.
    // [sizeDp] 는 웹 dotContent 의 두 번째 인자와 같다 — 경로 화면은 9 를 쓴다(지도 화면은 11).
    fun dot(color: Color, sizeDp: Float = 11f): Bitmap {
        val inner = sizeDp.px() / 2f       // 색 있는 알맹이
        val white = inner + 2f.px()        // 흰 테두리 2px
        val ring = white + 1f.px()         // box-shadow 1px
        val size = (ring * 2).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c = ring

        paint.color = AndroidColor.argb(51, 15, 23, 42)   // rgba(15,23,42,.2)
        canvas.drawCircle(c, c, ring, paint)
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(c, c, white, paint)
        paint.color = color.toArgb()
        canvas.drawCircle(c, c, inner, paint)
        return bitmap
    }

    // ── 편의점 상호 알약 ────────────────────────────────────────────────────────
    // 웹: background:{store 색} · color:#fff · padding:3px 8px · border-radius:12px
    //     font-size:11px · font-weight:700 · border:1.5px solid #fff
    //     box-shadow:0 2px 6px rgba(15,23,42,.28) · 아이콘 11px + gap 4px
    fun storePill(name: String): Bitmap = pill(
        text = name,
        background = LayerColor.store,
        cornerRadiusPx = 12f.px(),
        iconPathData = STORE_ICON,
        iconSizePx = 11f.px(),
        iconGapPx = 4f.px(),
        borderPx = 1.5f.px(),
        shadowAlpha = 71,                   // .28
    )

    // ── 위험구역 배지 ──────────────────────────────────────────────────────────
    // 웹: background:{레벨색} · padding:3px 8px · border-radius:6px · font-size:11px
    //     font-weight:700 · box-shadow:0 2px 6px rgba(0,0,0,.3) · 아이콘 12px
    // 테두리는 없다(편의점 알약과 달리 웹에도 border 가 없다).
    fun dangerBadge(level: String): Bitmap = pill(
        text = level,
        background = dangerLevelColor(level),
        cornerRadiusPx = 6f.px(),
        iconPathData = ALERT_TRIANGLE_ICON,
        iconSizePx = 12f.px(),
        iconGapPx = 4f.px(),
        borderPx = 0f,
        shadowAlpha = 77,                   // .3
        // 웹은 yAnchor:1.5 — 배지 높이의 1.5배 되는 지점이 좌표에 붙으므로 배지가 원 중심 위로 뜬다.
        // 안드로이드 라벨의 앵커는 비트맵 안에서만 잡히니, 아래에 배지 높이의 0.5배만큼
        // 투명 여백을 두고 비트맵 맨 아래를 앵커로 삼아 같은 위치를 만든다.
        bottomSpacerRatio = 0.5f,
    )

    // ── 검색 결과 핀 ───────────────────────────────────────────────────────────
    // 웹: background:#2563EB · padding:5px 11px · border-radius:16px · font-size:12px
    //     font-weight:700 · box-shadow:0 3px 10px rgba(37,99,235,.4) · 아이콘 map-pin 13px
    //     아래에 2×9px 꼬리, yAnchor:1 (꼬리 끝이 좌표에 붙는다)
    fun searchPin(name: String): Bitmap = pill(
        text = name,
        background = SEARCH_PIN_COLOR,
        cornerRadiusPx = 16f.px(),
        iconPathData = MAP_PIN_ICON,
        iconSizePx = 13f.px(),
        iconGapPx = 4f.px(),
        borderPx = 0f,
        shadowAlpha = 102,               // .4
        textSizeSp = 12f,
        paddingHPx = 11f.px(),
        paddingVPx = 5f.px(),
        tailHeightPx = 9f.px(),
    )

    /** 아이콘 + 글자가 든 둥근 알약. 웹의 CustomOverlay content 를 그대로 옮긴 모양이다. */
    private fun pill(
        text: String,
        background: Color,
        cornerRadiusPx: Float,
        iconPathData: String,
        iconSizePx: Float,
        iconGapPx: Float,
        borderPx: Float,
        shadowAlpha: Int,
        textSizeSp: Float = 11f,
        paddingHPx: Float = 8f.px(),
        paddingVPx: Float = 3f.px(),
        bottomSpacerRatio: Float = 0f,
        /** 0 이 아니면 알약 아래에 세로 꼬리를 그린다(검색 핀). 비트맵 맨 아래가 꼬리 끝이다. */
        tailHeightPx: Float = 0f,
    ): Bitmap {
        val paddingH = paddingHPx
        val paddingV = paddingVPx
        val paint = textPaint(textSizeSp, AndroidColor.WHITE)

        val textWidth = paint.measureText(text)
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val contentHeight = maxOf(textHeight, iconSizePx)

        val pillWidth = paddingH * 2 + iconSizePx + iconGapPx + textWidth + borderPx * 2
        val pillHeight = paddingV * 2 + contentHeight + borderPx * 2

        // 그림자(0 2px 6px)가 잘리지 않게 사방에 여백을 둔다.
        val blur = 6f.px()
        val shadowDy = 2f.px()
        val pad = blur + shadowDy

        val width = (pillWidth + pad * 2).toInt()
        // 꼬리가 있으면 아래쪽 그림자 여백 자리를 꼬리가 대신 쓴다 — 그래야 비트맵 맨 아래가 정확히 꼬리 끝이 된다.
        val height = (pillHeight + pad * 2 + pillHeight * bottomSpacerRatio + tailHeightPx).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val rect = RectF(pad, pad, pad + pillWidth, pad + pillHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = background.toArgb()
            setShadowLayer(blur, 0f, shadowDy, AndroidColor.argb(shadowAlpha, 15, 23, 42))
        }
        if (borderPx > 0f) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, bgPaint)
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, borderPaint)
            rect.inset(borderPx, borderPx)
            bgPaint.clearShadowLayer()
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, bgPaint)

        // 아이콘 — 웹은 stroke 만 있고 fill 은 없다(fill="none" stroke="currentColor").
        val iconLeft = rect.left + paddingH
        val iconTop = rect.top + (rect.height() - iconSizePx) / 2f
        canvas.save()
        canvas.translate(iconLeft, iconTop)
        canvas.drawPath(
            iconPath(iconPathData, iconSizePx),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = AndroidColor.WHITE
                strokeWidth = 2f * (iconSizePx / 24f)   // 원본 뷰박스 기준 strokeWidth:2
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            },
        )
        canvas.restore()

        val baseline = rect.top + (rect.height() - textHeight) / 2f - metrics.ascent
        canvas.drawText(text, iconLeft + iconSizePx + iconGapPx, baseline, paint)

        if (tailHeightPx > 0f) {
            val tailWidth = 2f.px()
            canvas.drawRect(
                (width - tailWidth) / 2f,
                rect.bottom,
                (width + tailWidth) / 2f,
                height.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background.toArgb() },
            )
        }
        return bitmap
    }

    // ── 출발·도착 마커 ─────────────────────────────────────────────────────────
    // 웹 RoutePage.addMarker / MapView.drawRouteOnMap:
    //   36×36 원 · border 2px solid #fff · box-shadow 0 2px 6px rgba(0,0,0,.25)
    //   글자 11px · font-weight 700 · yAnchor 1 (원 아래 끝이 좌표에 붙는다)
    fun endpoint(label: String, background: Color, textColor: Color): Bitmap {
        val radius = 36f.px() / 2f
        val border = 2f.px()
        val blur = 6f.px()
        val shadowDy = 2f.px()
        val pad = blur + shadowDy
        val size = ((radius + pad) * 2).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f

        // 흰 테두리를 그림자와 함께 먼저 깔고, 그 안에 색 원을 얹는다.
        canvas.drawCircle(
            c, c, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                setShadowLayer(blur, 0f, shadowDy, AndroidColor.argb(64, 0, 0, 0))
            },
        )
        canvas.drawCircle(
            c, c, radius - border,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background.toArgb() },
        )

        val paint = textPaint(11f, textColor.toArgb())
        val metrics = paint.fontMetrics
        canvas.drawText(
            label,
            c - paint.measureText(label) / 2f,
            c - (metrics.ascent + metrics.descent) / 2f,
            paint,
        )
        return bitmap
    }

    // ── 내 위치 ────────────────────────────────────────────────────────────────
    // 웹: 24px rgba(66,133,244,.2) 후광 + 14px #4285F4 알맹이(2px 흰 테두리)
    //     + box-shadow 0 2px 6px rgba(0,0,0,.3)
    // 웹에는 후광이 2.5배로 퍼지는 pulse 애니메이션이 있다. 지도 라벨은 애니메이터가 따로 있어
    // 여기서는 정지 상태만 그린다(움직임이 필요해지면 LabelAnimator 로 붙인다).
    fun myLocation(): Bitmap {
        val halo = 24f.px() / 2f
        val white = 14f.px() / 2f
        val inner = white - 2f.px()
        val size = (halo * 2).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c = halo

        paint.color = AndroidColor.argb(51, 66, 133, 244)   // rgba(66,133,244,.2)
        canvas.drawCircle(c, c, halo, paint)
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(c, c, white, paint)
        paint.color = AndroidColor.rgb(66, 133, 244)
        canvas.drawCircle(c, c, inner, paint)
        return bitmap
    }

    private companion object {
        /** 웹 MapView 의 검색 마커 색(#2563EB = --blue-primary). */
        val SEARCH_PIN_COLOR = Color(0xFF2563EB)

        // 웹 Icon.jsx / iconSvg.js 의 path 문자열 그대로다.
        const val MAP_PIN_ICON =
            "M12 21s7-6.4 7-11a7 7 0 1 0-14 0c0 4.6 7 11 7 11z" +
                "M9.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"
        const val STORE_ICON =
            "M6 4h12l3.5 5q-1.9 2.4-3.8 0q-1.9 2.4-3.8 0q-1.9 2.4-3.8 0q-1.9 2.4-3.8 0q-1.9 2.4-3.8 0L6 4z" +
                "M4.6 10.6V20 M19.4 10.6V20 M9.4 20v-5.6h5.2V20"
        const val ALERT_TRIANGLE_ICON =
            "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h16.9a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" +
                "M12 9v4 M12 17h.01"
    }
}
