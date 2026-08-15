package com.example.safelight.ui.theme


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 웹의 CSS 변수 묶음에 대응한다. Material3 ColorScheme 에 자리가 없는 토큰
 * (blueTint · textMuted · border · 상태색 · 지도 배경)을 담는다.
 *
 * 쓰는 쪽: `SafeLightTheme.colors.blueTint`
 */
@Immutable
data class SafeColors(
    val bluePrimary: Color,
    val blueDeep: Color,
    val blueTint: Color,
    val bg: Color,
    val surface: Color,
    val border: Color,
    val textStrong: Color,
    val textMuted: Color,
    val mapBg: Color,
    val authGrid: Color,
    val danger: Color,
    val warning: Color,
    val safe: Color,
    val info: Color,
)

private val LightSafeColors = SafeColors(
    bluePrimary = BluePrimary, blueDeep = BlueDeep, blueTint = BlueTint,
    bg = Bg, surface = Surface, border = Border,
    textStrong = TextStrong, textMuted = TextMuted, mapBg = MapBg, authGrid = AuthGrid,
    danger = Danger, warning = Warning, safe = Safe, info = Info,
)

private val DarkSafeColors = SafeColors(
    bluePrimary = BluePrimaryDark, blueDeep = BlueDeep, blueTint = BlueTintDark,
    bg = BgDark, surface = SurfaceDark, border = BorderDark,
    textStrong = TextStrongDark, textMuted = TextMutedDark, mapBg = MapBgDark, authGrid = AuthGridDark,
    danger = Danger, warning = Warning, safe = Safe, info = Info,
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BlueTint,
    onPrimaryContainer = BlueDeep,
    background = Bg,
    onBackground = TextStrong,
    surface = Surface,
    onSurface = TextStrong,
    surfaceVariant = BlueTint,
    onSurfaceVariant = TextMuted,
    outline = Border,
    error = Danger,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color.White,
    primaryContainer = BlueTintDark,
    onPrimaryContainer = Color.White,
    background = BgDark,
    onBackground = TextStrongDark,
    surface = SurfaceDark,
    onSurface = TextStrongDark,
    surfaceVariant = BlueTintDark,
    onSurfaceVariant = TextMutedDark,
    outline = BorderDark,
    error = Danger,
    onError = Color.White,
)

private val LocalSafeColors = staticCompositionLocalOf { LightSafeColors }

@Composable
fun SafeLightTheme(
    // 기기의 다크 모드를 따라가지 않는다. 웹의 야간 모드는 시스템 설정이 아니라
    // 사용자가 헤더에서 직접 켜는 토글(localStorage 'ls-night')이고 기본값은 밝은 화면이다.
    // isSystemInDarkTheme() 을 쓰면 폰이 다크 모드인 사람만 웹과 다른 화면을 보게 된다.
    // 야간 모드 토글을 만들 때 이 인자에 그 상태를 넘긴다.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Material You(다이내믹 컬러)는 쓰지 않는다. 기기 배경색이 White & Blue 아이덴티티를 덮어버린다.
    val safeColors = if (darkTheme) DarkSafeColors else LightSafeColors
    CompositionLocalProvider(LocalSafeColors provides safeColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}

object SafeLightTheme {
    val colors: SafeColors
        @Composable get() = LocalSafeColors.current
}
