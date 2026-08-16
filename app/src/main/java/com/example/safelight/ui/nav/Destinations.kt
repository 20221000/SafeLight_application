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

/**
 * 관리자 콘솔. 웹 adminNavItems.jsx 의 ADMIN_TABS(모바일 5개)와 같은 순서·같은 짧은 이름이다.
 *
 * 데스크탑 웹은 사이드바에서 '모니터링'과 '관리' 두 묶음으로 나누지만,
 * 좁은 화면에서는 웹도 묶음 없이 다섯 개를 한 줄에 편다.
 *
 * [title]·[subtitle] 은 각 화면의 헤더 문구다([AdminShell] 이 쓴다).
 */
enum class AdminTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
) {
    Dashboard(
        "admin/dashboard", "대시보드", SafeIcons.AdminDashboard,
        "대시보드", "서비스 현황을 한눈에 봅니다",
    ),
    Reports(
        "admin/reports", "신고", SafeIcons.AdminReports,
        "신고 관리", "접수된 긴급 신고를 처리합니다",
    ),
    Users(
        "admin/users", "사용자", SafeIcons.AdminUsers,
        "사용자 관리", "회원과 블랙리스트를 관리합니다",
    ),
    DangerZones(
        "admin/dangerzones", "위험구역", SafeIcons.MapPin,
        "위험 구역", "위험 구역과 등급을 관리합니다",
    ),
    Notices(
        "admin/notices", "공지", SafeIcons.AdminNotices,
        "공지", "공지사항을 올리고 고칩니다",
    ),
}
