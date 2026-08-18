package com.example.safelight.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 앱 전체 기본 글자 모양. Material3 는 이 bodyLarge 를 `LocalTextStyle` 로 깔아 두므로,
 * `Text(fontSize = 10.5.sp)` 처럼 style 을 안 주고 크기만 바꾼 글자는 **여기 값을 그대로 물려받는다.**
 *
 * 세 가지를 웹에 맞춘다.
 *
 * 1) lineHeight 를 `1.5.em` 으로 — 글자 크기에 비례한다(웹 App.css 의 `line-height: 1.5`).
 *    Material 기본값은 `24.sp` **고정**이라, 크기를 줄인 글자일수록 상자가 글자보다 훨씬 커진다.
 *    10.5 짜리 라벨이 24 높이 상자에 담기면 두 가지가 한꺼번에 어긋난다 —
 *    상자를 기준으로 가운데를 맞추니 글자가 위로 쏠려 보이고,
 *    그 상자가 배경(알약)이 되거나 세로로 쌓이면 남는 여백만큼 옆 요소를 밀어낸다.
 *
 * 2) letterSpacing 0 — 웹은 자간을 벌리지 않는다(Material 기본 `0.5.sp`).
 *
 * 3) includeFontPadding 끄기 — 안드로이드가 글꼴 위아래에 덧대는 여백이다. CSS 상자에는 없다.
 */
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 1.5.em,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
)
