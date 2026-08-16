package com.example.safelight.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.safelight.ui.auth.AuthViewModel
import com.example.safelight.ui.auth.MyInfoScreen
import com.example.safelight.ui.community.CommunityScreen
import com.example.safelight.ui.community.CommunityViewModel
import com.example.safelight.ui.community.PostDetailScreen
import com.example.safelight.ui.community.PostWriteScreen
import com.example.safelight.ui.layout.SafeLightHeader
import com.example.safelight.ui.map.MapCameraState
import com.example.safelight.ui.map.MapScreen
import com.example.safelight.ui.route.ActiveRoute
import com.example.safelight.ui.route.RouteScreen
import com.example.safelight.ui.search.PlaceSearchViewModel
import com.example.safelight.ui.search.SearchedPlace
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 웹 MobileShell 의 자리 — 헤더 + 본문 + 하단 탭바.
 */
@Composable
fun SafeLightRoot(night: Boolean, onToggleNight: () -> Unit) {
    val navController = rememberNavController()
    // 탭을 오가도 지도가 보던 자리를 잃지 않도록, 화면보다 오래 사는 여기에 카메라를 둔다.
    val mapCamera = remember { MapCameraState() }
    // 검색은 헤더(모든 탭 공용)에서 하고 결과는 지도가 받는다 — 그래서 상태가 둘의 공통 부모인 여기 있다.
    var searchTarget by remember { mutableStateOf<SearchedPlace?>(null) }
    // 경로 화면에서는 고른 장소를 지도로 보내지 않고 '출발지/도착지 중 무엇으로 쓸지' 를 먼저 묻는다.
    var routePendingPlace by remember { mutableStateOf<SearchedPlace?>(null) }
    // 안내 중인 경로. 화면보다 오래 살아야 탭을 옮겼다 돌아와도 안내가 유지된다(웹은 sessionStorage).
    var activeRoute by remember { mutableStateOf<ActiveRoute?>(null) }
    var askCancelRoute by remember { mutableStateOf(false) }
    val searchVm: PlaceSearchViewModel = viewModel()
    // 로그인 상태는 헤더(알림 벨)와 내 정보 탭이 같이 본다 — 그래서 여기서 한 번만 만든다.
    val authVm: AuthViewModel = viewModel()
    // 커뮤니티 목록 상태(탭·정렬·페이지)는 글을 열었다 돌아와도 그대로여야 한다.
    // 글쓰기·삭제 뒤에 목록을 다시 읽는 것도 여기서 부른다.
    val communityVm: CommunityViewModel = viewModel()
    // 글을 고치고 상세로 돌아왔을 때 다시 읽게 하는 표식. 값이 바뀌는 것 자체가 신호다.
    var communityRevision by remember { mutableStateOf(0) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun goToTab(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goToMapTab() = goToTab(UserTab.Map.route)

    fun pickPlace(place: SearchedPlace) {
        searchVm.onPicked(place)
        // 경로 화면에서 검색했으면 그 화면에 남는다 — 출발지로 쓸지 도착지로 쓸지 물어야 하기 때문이다.
        if (currentRoute == UserTab.Route.route) {
            routePendingPlace = place
            return
        }
        searchTarget = place
        // 웹은 장소를 고르면 지도 페이지로 이동한다. 다른 탭에서 검색했어도 결과를 지도에서 보여준다.
        goToMapTab()
    }

    Scaffold(
        containerColor = SafeLightTheme.colors.bg,
        topBar = {
            SafeLightHeader(
                query = searchVm.query,
                results = searchVm.results,
                onQueryChange = searchVm::onQueryChange,
                onSubmit = { searchVm.onSubmit(::pickPlace) },
                onPickPlace = ::pickPlace,
                night = night,
                onToggleNight = onToggleNight,
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SafeLightTheme.colors.surface) {
                UserTab.entries.forEach { tab ->
                    NavigationBarItem(
                        // 게시글 상세·글쓰기처럼 탭 안에서 더 들어간 화면에서도 그 탭에 불이 켜져 있어야 한다
                        // (웹도 UserShell 에 active="community" 를 그대로 넘긴다).
                        selected = currentRoute == tab.route ||
                            currentRoute?.startsWith("${tab.route}/") == true,
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
            composable(UserTab.Map.route) {
                MapScreen(
                    camera = mapCamera,
                    searchTarget = searchTarget,
                    activeRoute = activeRoute,
                    onCancelRoute = { askCancelRoute = true },
                )
            }
            composable(UserTab.Route.route) {
                RouteScreen(
                    pendingPlace = routePendingPlace,
                    onPendingPlaceHandled = { routePendingPlace = null },
                    onStartGuidance = { route ->
                        activeRoute = route
                        goToMapTab()
                    },
                )
            }
            composable(UserTab.Community.route) {
                CommunityScreen(
                    loggedIn = authVm.user != null,
                    onOpenPost = { postId ->
                        // 웹도 목록에서 글을 여는 순간 조회수를 올린다.
                        communityVm.countView(postId)
                        navController.navigate("community/post/$postId")
                    },
                    onWrite = { navController.navigate("community/write") },
                    // 로그인 화면은 '내 정보' 탭이 들고 있다.
                    onGoLogin = { goToTab(UserTab.MyInfo.route) },
                    vm = communityVm,
                )
            }
            composable(
                "community/post/{postId}",
                arguments = listOf(navArgument("postId") { type = NavType.LongType }),
            ) { entry ->
                val postId = entry.arguments?.getLong("postId") ?: 0L
                PostDetailScreen(
                    postId = postId,
                    user = authVm.user,
                    reloadKey = communityRevision,
                    onBack = {
                        // 글이 지워졌거나 댓글이 달렸을 수 있으니 목록도 다시 읽는다.
                        communityVm.refresh()
                        navController.popBackStack()
                    },
                    onEdit = { navController.navigate("community/edit/$it") },
                    onGoLogin = { goToTab(UserTab.MyInfo.route) },
                )
            }
            composable("community/write") {
                PostWriteScreen(
                    postId = null,
                    user = authVm.user,
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        communityVm.refresh()
                        navController.popBackStack()
                    },
                )
            }
            composable(
                "community/edit/{postId}",
                arguments = listOf(navArgument("postId") { type = NavType.LongType }),
            ) { entry ->
                PostWriteScreen(
                    postId = entry.arguments?.getLong("postId"),
                    user = authVm.user,
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        communityVm.refresh()
                        communityRevision++
                        navController.popBackStack()
                    },
                )
            }
            composable(UserTab.MyInfo.route) { MyInfoScreen(authVm) }
        }
    }

    // 안내 취소는 되돌릴 수 없어서(경로를 다시 받아야 한다) 한 번 더 묻는다. 웹 ConfirmDialog 와 같은 문구다.
    if (askCancelRoute) {
        AlertDialog(
            onDismissRequest = { askCancelRoute = false },
            title = { Text("경로 안내를 취소하겠습니까?") },
            text = { Text("지도에 표시된 안전 경로가 사라집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    activeRoute = null
                    askCancelRoute = false
                }) { Text("안내 취소") }
            },
            dismissButton = {
                TextButton(onClick = { askCancelRoute = false }) { Text("계속 안내") }
            },
            containerColor = SafeLightTheme.colors.surface,
            titleContentColor = SafeLightTheme.colors.textStrong,
            textContentColor = SafeLightTheme.colors.textMuted,
        )
    }
}
