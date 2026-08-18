package com.example.safelight.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.safelight.ui.admin.AdminRoot
import com.example.safelight.ui.auth.AuthViewModel
import com.example.safelight.ui.auth.MyInfoScreen
import com.example.safelight.ui.community.CommunityScreen
import com.example.safelight.ui.community.CommunityViewModel
import com.example.safelight.ui.community.PostDetailScreen
import com.example.safelight.ui.community.PostWriteScreen
import com.example.safelight.ui.friends.FriendsScreen
import com.example.safelight.ui.layout.SafeLightHeader
import com.example.safelight.ui.layout.SafeLightTabBar
import com.example.safelight.ui.messages.MessagesScreen
import com.example.safelight.ui.notifications.NotificationsScreen
import com.example.safelight.ui.notifications.UNREAD_POLL_MS
import com.example.safelight.ui.notifications.UnreadViewModel
import com.example.safelight.ui.map.MapCameraState
import com.example.safelight.ui.map.MapScreen
import com.example.safelight.ui.route.ActiveRoute
import com.example.safelight.ui.route.RouteScreen
import com.example.safelight.ui.search.PlaceSearchViewModel
import com.example.safelight.ui.search.SearchedPlace
import com.example.safelight.ui.theme.SafeLightTheme
import kotlinx.coroutines.delay

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
    // 헤더 벨의 안 읽음 수. 알림함·쪽지함에서 읽고 나오면 그쪽이 다시 세라고 알려준다.
    val unreadVm: UnreadViewModel = viewModel()
    // 관리자 콘솔은 사용자 화면과 셸 자체가 다르다(웹도 AdminShell 로 통째로 갈아탄다).
    // 그래서 NavHost 의 목적지가 아니라 이 화면을 덮는 별도 상태로 둔다.
    var adminOpen by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 화면을 보고 있는 동안에만 다시 센다. 켜 두기만 한 앱이 서버를 계속 두드릴 이유가 없다.
    // (웹은 focus·visibilitychange 로 같은 일을 한다.)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(authVm.user) {
        if (authVm.user == null) {
            unreadVm.clear()
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                unreadVm.refresh()
                delay(UNREAD_POLL_MS)
            }
        }
    }

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

    // 콘솔이 열려 있으면 사용자 화면을 통째로 덮는다. 관리자가 아니게 되면(로그아웃 등) 저절로 닫힌다.
    if (adminOpen && authVm.user?.isAdmin == true) {
        // 뒤로가기는 탭 사이를 오가는 게 아니라 콘솔을 닫는 것이어야 한다.
        BackHandler { adminOpen = false }
        AdminRoot(
            // 백엔드가 자기 자신의 권한·블랙리스트 변경을 막으므로 사용자 관리에서 미리 잠근다.
            selfUserId = authVm.user?.userId,
            onExit = { adminOpen = false },
            onLogout = {
                authVm.logout()
                adminOpen = false
            },
        )
        return
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
                // 벨은 웹과 같이 로그인한 사용자에게만 보인다.
                loggedIn = authVm.user != null,
                unreadEmergency = unreadVm.emergency,
                unreadMessage = unreadVm.message,
                onOpenNotifications = { navController.navigate("myinfo/notifications") },
            )
        },
        bottomBar = {
            SafeLightTabBar(current = currentRoute) { tab ->
                if (currentRoute == tab.route) return@SafeLightTabBar
                navController.navigate(tab.route) {
                    // 탭을 오갈 때 백스택이 무한히 쌓이지 않게 시작 지점까지 정리한다.
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
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
                    // SOS 는 계정에 붙는다 — 담당자가 연락하고 친구에게 위치가 가야 하기 때문이다.
                    loggedIn = authVm.user != null,
                    onNeedLogin = { goToTab(UserTab.MyInfo.route) },
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
            composable(UserTab.MyInfo.route) {
                MyInfoScreen(
                    vm = authVm,
                    onOpenPost = { postId ->
                        communityVm.countView(postId)
                        navController.navigate("community/post/$postId")
                    },
                    onOpenNotifications = { navController.navigate("myinfo/notifications") },
                    onOpenMessages = { navController.navigate("myinfo/messages") },
                    onOpenFriends = { navController.navigate("myinfo/friends") },
                    onOpenAdmin = { adminOpen = true },
                )
            }
            composable("myinfo/friends") {
                FriendsScreen(
                    onBack = { navController.popBackStack() },
                    // 쪽지는 받는 사람을 미리 고른 채로 쪽지함을 연다(웹도 state 로 넘긴다).
                    onWriteMessage = { navController.navigate("myinfo/messages?to=$it") },
                )
            }
            composable(
                "myinfo/messages?to={to}",
                arguments = listOf(
                    navArgument("to") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { entry ->
                MessagesScreen(
                    openWith = entry.arguments?.getLong("to"),
                    onBack = { navController.popBackStack() },
                    onUnreadChanged = unreadVm::refresh,
                )
            }
            composable("myinfo/notifications") {
                NotificationsScreen(
                    user = authVm.user,
                    onBack = { navController.popBackStack() },
                    onWriteMessage = { navController.navigate("myinfo/messages?to=$it") },
                    onOpenFriends = { navController.navigate("myinfo/friends") },
                    onUnreadChanged = unreadVm::refresh,
                )
            }
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
