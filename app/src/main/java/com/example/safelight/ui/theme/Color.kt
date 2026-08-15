package com.example.safelight.ui.theme

import androidx.compose.ui.graphics.Color

// 웹(App.css)의 CSS 변수를 그대로 옮긴 값이다. 한쪽만 바꾸면 웹과 앱의 색이 갈린다.
// Material3 의 primary/surface 로는 blue-tint · text-muted · map-* 같은 토큰을 표현할 수 없어
// SafeColors 로 따로 들고 다닌다(Theme.kt 의 LocalSafeColors).

// --- 라이트 (:root) ---
val BluePrimary = Color(0xFF2563EB)
val BlueDeep = Color(0xFF1E40AF)
val BlueTint = Color(0xFFEFF4FF)
val Bg = Color(0xFFF7F9FC)
val Surface = Color(0xFFFFFFFF)
val Border = Color(0xFFE4E9F2)
val TextStrong = Color(0xFF0F172A)
val TextMuted = Color(0xFF64748B)
val MapBg = Color(0xFFE8EEF6)
val AuthGrid = Color(0xFFEEF2F8)   // 로그인·회원가입 배경 격자선

// --- 다크 (.ls-dark = 야간 모드) ---
val BluePrimaryDark = Color(0xFF3B82F6)
val BlueTintDark = Color(0xFF16233F)
val BgDark = Color(0xFF0B1220)
val SurfaceDark = Color(0xFF111A2E)
val BorderDark = Color(0xFF1F2C48)
val TextStrongDark = Color(0xFFF1F5F9)
val TextMutedDark = Color(0xFF94A3B8)
val MapBgDark = Color(0xFF0E1626)
// 밝은 격자선을 그대로 두면 어두운 배경 위에 흰 줄이 그대로 남는다. 배경보다 살짝 밝은 정도로만.
val AuthGridDark = Color(0xFF131C2E)

// --- 상태색 (라이트·다크 공통) ---
// tokens.js 의 LEVEL_HEX 와 같은 값이다 — 위험구역 원·배지 색이 여기서 나온다.
val Danger = Color(0xFFE11D48)   // HIGH
val Warning = Color(0xFFF59E0B)  // MEDIUM
val Safe = Color(0xFF10B981)     // LOW
val Info = Color(0xFF2563EB)
