package com.example.safelight.ui.nav

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.safelight.ui.icon.SafeIcons

/**
 * 웹 navItems.jsx 의 USER_NAV 과 1:1 로 맞춘다.
 * route 값은 웹의 경로(`/`, `/route`, ...)를 그대로 쓰되 앞 슬래시만 뺐다.
 * label 은 모바일 탭바 기준(shortLabel)이다.
 */
enum class UserTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Map("map", "지도", SafeIcons.Map),
    Route("route", "경로", SafeIcons.Route),
    Community("community", "커뮤니티", SafeIcons.Community),
    MyInfo("myinfo", "내 정보", SafeIcons.MyInfo),
}

/** 관리자 콘솔. 웹 adminNavItems.jsx 의 ADMIN_TABS(모바일 5개)와 같은 순서다. */
enum class AdminTab(val route: String, val label: String) {
    Dashboard("admin/dashboard", "대시보드"),
    Reports("admin/reports", "신고"),
    Users("admin/users", "사용자"),
    DangerZones("admin/dangerzones", "위험구역"),
    Notices("admin/notices", "공지"),
}
