package com.example.safelight.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.safelight.ui.map.MapCameraState
import com.example.safelight.ui.map.MapScreen
import com.example.safelight.ui.placeholder.PlaceholderScreen
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 웹 MobileShell 의 자리 — 본문 + 하단 탭바.
 * 헤더(로고·검색·알림·야간모드)는 화면마다 다르게 붙으므로 여기서 그리지 않는다.
 */
@Composable
fun SafeLightRoot() {
    val navController = rememberNavController()
    // 탭을 오가도 지도가 보던 자리를 잃지 않도록, 화면보다 오래 사는 여기에 카메라를 둔다.
    val mapCamera = remember { MapCameraState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = SafeLightTheme.colors.bg,
        bottomBar = {
            NavigationBar(containerColor = SafeLightTheme.colors.surface) {
                UserTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute == tab.route) return@NavigationBarItem
                            navController.navigate(tab.route) {
                                // 탭을 오갈 때 백스택이 무한히 쌓이지 않게 시작 지점까지 정리한다.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SafeLightTheme.colors.bluePrimary,
                            selectedTextColor = SafeLightTheme.colors.bluePrimary,
                            indicatorColor = SafeLightTheme.colors.blueTint,
                            unselectedIconColor = SafeLightTheme.colors.textMuted,
                            unselectedTextColor = SafeLightTheme.colors.textMuted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = UserTab.Map.route,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(UserTab.Map.route) { MapScreen(mapCamera) }
            composable(UserTab.Route.route) { PlaceholderScreen("경로 안내", "백엔드 /routes 를 붙일 자리") }
            composable(UserTab.Community.route) { PlaceholderScreen("커뮤니티", "게시글 목록·작성을 붙일 자리") }
            composable(UserTab.MyInfo.route) { PlaceholderScreen("내 정보", "로그인·프로필을 붙일 자리") }
        }
    }
}
