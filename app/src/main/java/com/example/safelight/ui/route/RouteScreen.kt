package com.example.safelight.ui.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.net.BookmarkDto
import com.example.safelight.data.net.RouteHistoryDto
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.layout.DragSheet
import com.example.safelight.ui.layout.rememberDragSheetState
import com.example.safelight.ui.map.FACILITY_MIN_ZOOM
import com.example.safelight.ui.map.KakaoMapHost
import com.example.safelight.ui.map.LayerColor
import com.example.safelight.ui.map.MapLayers
import com.example.safelight.ui.map.RouteColor
import com.example.safelight.ui.map.currentBounds
import com.example.safelight.ui.search.SearchedPlace
import com.example.safelight.ui.theme.SafeLightTheme
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory

private const val TAG = "RouteScreen"

/** 경로 전체를 담을 때의 여백(웹 setBounds 의 24). */
private const val ROUTE_FIT_PADDING_DP = 24

/**
 * 웹 RoutePage.jsx 의 모바일 화면. 지도 위에 드래그 바텀시트가 얹힌다.
 *
 * 데스크탑의 좌측 360px 패널은 옮기지 않았다 — 웹에서도 모바일에서는 시트로 갈아탄다.
 * 선택 경로의 안전도 카드도 마찬가지다(웹 모바일에서는 시트가 같은 내용을 이미 보여줘서 감춘다).
 */
@Composable
fun RouteScreen(
    pendingPlace: SearchedPlace?,
    onPendingPlaceHandled: () -> Unit,
    onStartGuidance: (ActiveRoute) -> Unit,
    modifier: Modifier = Modifier,
    vm: RouteViewModel = viewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = SafeLightTheme.colors
    val sheet = rememberDragSheetState()
    val snackbar = remember { SnackbarHostState() }
    val mapHolder = remember { arrayOfNulls<KakaoMap>(1) }
    val layersHolder = remember { arrayOfNulls<MapLayers>(1) }
    var mapReady by remember { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) requestCurrentLocation(context, vm::onCurrentLocation) }

    // 출발지가 '현재 위치'로 돌아올 때마다 위치를 다시 잡는다(웹의 startMode effect).
    LaunchedEffect(vm.startMode) {
        if (vm.startMode != StartMode.Current) return@LaunchedEffect
        if (context.hasLocationPermission()) requestCurrentLocation(context, vm::onCurrentLocation)
        else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // 시트가 지도 아래쪽을 덮는다. 그만큼을 지도에 알려 주지 않으면 출발·도착 마커가 시트 뒤로 숨는다.
    // 웹은 setCenter 뒤에 시트 높이의 절반만큼 panBy 로 밀지만, 안드로이드에는 지도 자체에
    // 여백을 알리는 setPadding 이 있어 카메라 이동·경로 맞추기가 모두 알아서 보정된다.
    // 시트를 mid 위로 올린 동안은 어차피 지도가 거의 안 보이므로 mid 까지만 반영한다(웹과 같은 규칙).
    val mapBottomPaddingPx = sheet.peekPx.toInt()
    LaunchedEffect(mapBottomPaddingPx, mapReady) {
        mapHolder[0]?.setPadding(0, 0, 0, mapBottomPaddingPx)
    }

    // 뷰모델이 '여기를 보여 달라'고 하면 옮긴다(현재 위치 확인·출발지/도착지 선택).
    LaunchedEffect(vm.cameraTarget, mapReady) {
        val target = vm.cameraTarget ?: return@LaunchedEffect
        val map = mapHolder[0] ?: return@LaunchedEffect
        map.moveCamera(CameraUpdateFactory.newCenterPosition(target.toLatLng(), FACILITY_MIN_ZOOM))
        vm.cameraTargetHandled()
    }

    // 배경 시설 — 지도 화면과 같은 규칙으로, 화면 안에 있는 것만 그린다.
    // CCTV 만 그리던 시절에는 빨간 점만 깔려서, 정작 이 화면이 말하는 '가로등·편의점이 많아
    // 안전한 길'의 근거가 지도에 안 보였다. 세 레이어를 다 깐다.
    LaunchedEffect(vm.visibleCctv, mapReady) {
        layersHolder[0]?.drawCctv(vm.visibleCctv, small = true)
    }

    LaunchedEffect(vm.visibleLamps, mapReady) {
        layersHolder[0]?.drawLamps(vm.visibleLamps, small = true)
    }

    LaunchedEffect(vm.visibleStores, mapReady) {
        layersHolder[0]?.drawStores(vm.visibleStores, small = true)
    }

    // 출발·도착 마커와 선택한 경로. 경로가 없어도 고른 곳은 바로 보여준다.
    LaunchedEffect(vm.selectedStart, vm.selectedDest, vm.selectedRoute, mapReady) {
        val layers = layersHolder[0] ?: return@LaunchedEffect
        val route = vm.selectedRoute?.route
        layers.drawRoute(
            path = route?.path.orEmpty(),
            start = vm.selectedStart?.toLatLng(),
            destination = vm.selectedDest?.toLatLng(),
            cctvLocations = route?.cctvLocations.orEmpty(),
            storeLocations = route?.storeLocations.orEmpty(),
            lampLocations = route?.securityLightLocations.orEmpty(),
        )
        val points = route?.path.orEmpty()
        if (points.isNotEmpty()) {
            mapHolder[0]?.moveCamera(
                CameraUpdateFactory.fitMapPoints(
                    points.map { LatLng.from(it.latitude, it.longitude) }.toTypedArray(),
                    with(density) { ROUTE_FIT_PADDING_DP.dp.roundToPx() },
                ),
            )
        }
    }

    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.messageShown()
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { sheet.containerHeightPx = it.height.toFloat() },
    ) {
        KakaoMapHost(
            initialZoom = FACILITY_MIN_ZOOM,
            onCameraIdle = { map -> map.currentBounds()?.let { vm.onCameraIdle(it, map.zoomLevel) } },
            onReady = { kakaoMap, layers ->
                mapHolder[0] = kakaoMap
                layersHolder[0] = layers
                mapReady = true
            },
        )

        // ── 지도 컨트롤 (우하단) ─────────────────────────────────────────────
        // 시트 위로 올라오도록 시트 높이만큼 띄우되 mid 까지만이다 — 다 올린 시트는 어차피
        // 지도를 덮으므로 버튼까지 따라 올리면 화면 위로 밀려 나간다(웹 visibleOffset 과 같다).
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp)
                .padding(bottom = with(density) { sheet.peekPx.toDp() } + 16.dp),
            horizontalAlignment = Alignment.End,
            // 웹은 확대/축소 묶음을 offset+16 에, 현재 위치 버튼을 offset+104 에 둔다.
            // 묶음이 81 높이(40+구분선+40)라 둘 사이는 8 이다 — 24 로 벌리면 현재 위치 버튼만
            // 따로 떠 있는 것처럼 보인다.
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapControlSurface {
                MapControlButton({
                    val here = vm.currentLocation ?: return@MapControlButton
                    mapHolder[0]?.moveCamera(CameraUpdateFactory.newCenterPosition(here.toLatLng()))
                }) {
                    Icon(
                        SafeIcons.Crosshair, "현재 위치로",
                        tint = colors.bluePrimary, modifier = Modifier.size(18.dp),
                    )
                }
            }
            MapControlSurface {
                Column {
                    MapControlButton({ mapHolder[0]?.moveCamera(CameraUpdateFactory.zoomIn()) }) {
                        Text("+", fontSize = 19.sp, color = colors.textStrong)
                    }
                    Box(Modifier.size(width = 40.dp, height = 1.dp).background(colors.border))
                    MapControlButton({ mapHolder[0]?.moveCamera(CameraUpdateFactory.zoomOut()) }) {
                        Text("−", fontSize = 19.sp, color = colors.textStrong)
                    }
                }
            }
        }

        // ── 상단 검색에서 고른 장소 → 출발/도착 선택 팝업 ──────────────────────
        if (pendingPlace != null) {
            PendingPlaceCard(
                place = pendingPlace,
                onSetStart = {
                    vm.selectStartMode(StartMode.Search)
                    vm.selectStart(pendingPlace)
                    onPendingPlaceHandled()
                },
                onSetDest = {
                    vm.selectDestMode(DestMode.Search)
                    vm.selectDest(pendingPlace)
                    onPendingPlaceHandled()
                },
                onClose = onPendingPlaceHandled,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp, start = 12.dp, end = 12.dp),
            )
        }

        DragSheet(
            state = sheet,
            modifier = Modifier.align(Alignment.BottomCenter),
            head = {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp)) {
                    Text(
                        "안전 경로 안내",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textStrong,
                    )
                    Text(
                        "CCTV·가로등·편의점 밀집도로 안전한 길을 찾습니다",
                        fontSize = 12.5.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            },
            body = {
                // 위 12 는 제목 블록과 첫 카드 사이 몫이다. 웹은 머리말과 카드들이 한 컬럼에
                // gap:12 로 묶여 있어서, 머리말 자신의 아래 여백 10 뒤에 12 가 더 붙는다.
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StartCard(vm)
                    DestCard(vm)
                    BookmarkCard(vm)
                    if (!vm.isSearched) {
                        PrimaryButton(
                            text = if (vm.loading) "경로 탐색 중..." else "안전 경로 찾기",
                            icon = if (vm.loading) null else SafeIcons.Search,
                            enabled = !vm.loading,
                            onClick = vm::searchRoute,
                        )
                    } else {
                        ResultCard(vm, onStartGuidance)
                    }
                }
            },
        )

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

// ── ① 출발지 ────────────────────────────────────────────────────────────────

@Composable
private fun StartCard(vm: RouteViewModel) {
    val colors = SafeLightTheme.colors
    RouteCard("① 출발지") {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeButton(
                "현재 위치", SafeIcons.MapPin,
                active = vm.startMode == StartMode.Current,
                modifier = Modifier.weight(1f),
            ) { vm.selectStartMode(StartMode.Current) }
            ModeButton(
                "직접 검색", SafeIcons.Search,
                active = vm.startMode == StartMode.Search,
                modifier = Modifier.weight(1f),
            ) { vm.selectStartMode(StartMode.Search) }
        }

        if (vm.startMode == StartMode.Current) {
            val here = vm.currentLocation
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.border, RoundedCornerShape(11.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (here != null) colors.safe else colors.warning),
                )
                if (here == null) {
                    Text("현재 위치를 가져오는 중...", fontSize = 13.sp, color = colors.textMuted)
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "현재 위치 확인됨",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.safe,
                        )
                        Text(
                            vm.startAddress.ifBlank {
                                "%.4f · %.4f".format(here.latitude, here.longitude)
                            },
                            fontSize = 11.5.sp,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    // GPS 가 실내 등에서 틀릴 수 있어 직접 고칠 길을 남겨 둔다.
                    Row(
                        Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(9.dp))
                            .clickable { vm.editStart() }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(SafeIcons.Edit, null, tint = colors.bluePrimary, modifier = Modifier.size(13.dp))
                        Text(
                            "수정",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.bluePrimary,
                        )
                    }
                }
            }
        } else {
            RouteTextField(
                value = vm.startQuery,
                onValueChange = vm::onStartQueryChange,
                placeholder = "출발지 검색...",
            )
            if (vm.startResults.isNotEmpty()) {
                PlaceResultList(vm.startResults, RouteColor.start) { vm.selectStart(it) }
            }
        }
    }
}

// ── ② 도착지 ────────────────────────────────────────────────────────────────

@Composable
private fun DestCard(vm: RouteViewModel) {
    val colors = SafeLightTheme.colors
    RouteCard("② 도착지") {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            TabButton("최근", SafeIcons.Clock, vm.destMode == DestMode.Recent, Modifier.weight(1f)) {
                vm.selectDestMode(DestMode.Recent)
            }
            TabButton("검색", SafeIcons.Search, vm.destMode == DestMode.Search, Modifier.weight(1f)) {
                vm.selectDestMode(DestMode.Search)
            }
        }

        if (vm.destMode == DestMode.Recent) {
            if (vm.recentRoutes.isEmpty()) {
                EmptyText("최근 검색한 경로가 없습니다.")
            } else {
                vm.recentRoutes.forEach { history ->
                    RecentRow(
                        history = history,
                        label = vm.recentLabels[history.routeHistoryId] ?: history.routeName,
                        onClick = { vm.openRecentRoute(history) },
                        onDelete = { vm.deleteRecentRoute(history.routeHistoryId) },
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(9.dp))
                        .clickable { vm.clearRecentRoutes() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "전체 삭제",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textMuted,
                    )
                }
            }
        } else {
            RouteTextField(
                value = vm.destQuery,
                onValueChange = vm::onDestQueryChange,
                placeholder = "도착지 검색...",
            )
            if (vm.destResults.isNotEmpty()) {
                PlaceResultList(vm.destResults, RouteColor.destination) { vm.selectDest(it) }
            }
        }
    }
}

@Composable
private fun RecentRow(
    history: RouteHistoryDto,
    label: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SafeIcons.Clock, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(formatSearchedAt(history.searchedAt), fontSize = 11.sp, color = colors.textMuted)
        }
        Icon(
            SafeIcons.Close, "삭제",
            tint = colors.textMuted,
            modifier = Modifier.size(16.dp).clickable(onClick = onDelete),
        )
    }
}

/** '2026-08-06T08:35:12' → '08-06 08:35' (웹 fmtSearchedAt 과 같다). */
private fun formatSearchedAt(iso: String): String =
    if (iso.length < 16) "" else iso.take(16).drop(5).replace('T', ' ')

// ── 북마크 ──────────────────────────────────────────────────────────────────

@Composable
private fun BookmarkCard(vm: RouteViewModel) {
    val colors = SafeLightTheme.colors
    val visible = vm.visibleBookmarks
    RouteCard(
        title = "북마크",
        titleIcon = SafeIcons.Star,
        right = {
            if (vm.bookmarks.isNotEmpty()) {
                Text(
                    if (vm.bookmarkQuery.isBlank()) "${vm.bookmarks.size}개"
                    else "${visible.size} / ${vm.bookmarks.size}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textMuted,
                )
            }
        },
    ) {
        if (vm.bookmarks.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("저장된 북마크가 없습니다.", fontSize = 13.sp, color = colors.textMuted)
                Text(
                    "경로를 찾은 뒤 ‘북마크 저장’을 눌러보세요.",
                    fontSize = 11.5.sp,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            return@RouteCard
        }

        RouteTextField(
            value = vm.bookmarkQuery,
            onValueChange = vm::onBookmarkQueryChange,
            placeholder = "북마크 검색...",
        )
        Row(
            Modifier.padding(top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SortChip("최신순", SafeIcons.Clock, vm.bookmarkSort == BookmarkSort.Recent) {
                vm.selectBookmarkSort(BookmarkSort.Recent)
            }
            SortChip("가나다순", null, vm.bookmarkSort == BookmarkSort.Name) {
                vm.selectBookmarkSort(BookmarkSort.Name)
            }
        }
        if (visible.isEmpty()) {
            EmptyText("검색 결과가 없습니다.")
        } else {
            visible.forEach { bookmark ->
                BookmarkRow(
                    bookmark = bookmark,
                    busy = vm.bookmarkBusyId == bookmark.id,
                    locked = vm.bookmarkBusyId != null,
                    onClick = { vm.openBookmark(bookmark) },
                    onDelete = { vm.deleteBookmark(bookmark.id) },
                )
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkDto,
    busy: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (busy) colors.blueTint else Color.Transparent)
            // 다른 북마크를 불러오는 중이면 흐리게 해서 지금은 못 누른다는 걸 보인다.
            .alphaIf(locked && !busy, 0.5f)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SafeIcons.Star, null, tint = colors.bluePrimary, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bookmark.routeName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 북마크 응답에는 safetyScore 합계만 있고 CCTV/가로등/편의점 내역은 없다.
            Text(
                if (busy) "경로를 불러오는 중…" else "안전 점수 ${bookmark.safetyScore}점 · 눌러서 경로 보기",
                fontSize = 11.sp,
                color = if (busy) colors.bluePrimary else colors.textMuted,
            )
        }
        Icon(
            SafeIcons.Close, "북마크 삭제",
            tint = colors.textMuted,
            modifier = Modifier
                .size(16.dp)
                .clickable(enabled = !locked, onClick = onDelete),
        )
    }
}

// ── ③ 추천 경로 ─────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(vm: RouteViewModel, onStartGuidance: (ActiveRoute) -> Unit) {
    val colors = SafeLightTheme.colors
    RouteCard("③ 추천 경로") {
        // 막대 길이는 절대 점수가 아니라 **1순위 경로 대비**로 그린다.
        //
        // safetyScore 는 시설 개수의 가중합이라(CCTV×3 + 편의점×3 + 보안등×1 + 치안시설×4)
        // 100 이 만점이 아니다 — 도심 경로는 수백 점, 외곽은 한 자리도 나온다. 100 을 기준으로 그리면
        // 도심에서는 세 경로가 다 꽉 차고 외곽에서는 다 비어서, 어느 쪽이든 경로 사이 차이가 안 보인다.
        // 1순위를 100% 로 두면 '2순위가 1순위의 몇 할인지'가 바로 읽힌다.
        //
        // routes 는 안전 점수 내림차순(rank 1 이 곧 첫 항목)이다.
        val topScore = vm.routes.firstOrNull()?.safetyScore ?: 0

        vm.routes.forEach { ranked ->
            val selected = vm.selectedRoute?.rank == ranked.rank
            // 전부 0점이면 채울 것이 없다(0 으로 나누는 것도 막는다).
            val barRatio =
                if (topScore > 0) (ranked.safetyScore.toFloat() / topScore).coerceIn(0f, 1f) else 0f
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) colors.blueTint else colors.bg)
                    .border(
                        1.dp,
                        if (selected) colors.bluePrimary else colors.border,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { vm.selectRoute(ranked) }
                    .padding(13.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "경로 ${ranked.rank}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textStrong,
                        modifier = Modifier.weight(1f),
                    )
                    if (ranked.rank == 1) {
                        Text(
                            "추천",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.bluePrimary)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        "안전 점수 ${ranked.safetyScore}점",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor(ranked.safetyScore),
                    )
                }
                // 점수 막대. 웹과 같이 1순위 경로를 가득 찬 것으로 본다.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.border),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(barRatio)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(scoreColor(ranked.safetyScore)),
                    )
                }
                // 합계만 보면 무엇이 많아서 높은지 알 수 없어 내역을 같이 보여준다.
                // 셋이 한 줄에 안 들어가면 접는다 — 보안등 수는 네 자리까지 간다(웹도 flexWrap).
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    FacilityCount(LayerColor.cctv, "CCTV ${ranked.cctvCount}")
                    FacilityCount(LayerColor.streetLamp, "가로등 ${ranked.lampCount}")
                    FacilityCount(LayerColor.store, "편의점 ${ranked.storeCount}")
                }
                if (ranked.route.description.isNotBlank()) {
                    Text(
                        ranked.route.description,
                        fontSize = 12.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        // 웹은 이 세 버튼을 gap 8 짜리 한 묶음으로 두고 마지막 경로 카드와 8 을 띄운다
        // (카드 자신의 아래 여백 8 과 합쳐 16).
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // '안전 경로 찾기'(48/12/15)보다 한 단계 낮은 44/11/14 다. 이쪽은 결과 카드 **안**의
            // 버튼이라 검색 버튼과 같은 덩치로 서면 카드가 버튼에 눌린다. 그림자도 없다.
            PrimaryButton(
                text = "지도에서 경로 보기",
                icon = SafeIcons.Play,
                height = 44.dp,
                cornerRadius = 11.dp,
                fontSize = 14.sp,
                iconSize = 15.dp,
                elevated = false,
                onClick = {
                    val route = vm.selectedRoute ?: return@PrimaryButton
                    onStartGuidance(
                        ActiveRoute(
                            path = route.route.path,
                            start = vm.selectedStart,
                            destination = vm.selectedDest,
                            safetyScore = route.safetyScore,
                        ),
                    )
                },
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlineButton("북마크 저장", SafeIcons.Star, colors.bluePrimary, Modifier.weight(1f), vm::saveBookmark)
                OutlineButton("다시 검색", SafeIcons.Refresh, colors.textMuted, Modifier.weight(1f), vm::resetSearch)
            }
        }
    }
}

@Composable
private fun FacilityCount(dotColor: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Text(text, fontSize = 11.5.sp, color = SafeLightTheme.colors.textMuted)
    }
}

/**
 * 웹 RoutePage 의 scoreColor — 60점 이상 초록, 30점 이상 주황, 그 아래는 빨강.
 *
 * safetyScore 에 가중치가 붙으면서(CCTV×3 + 편의점×3 + 보안등×1 + 치안시설×4)
 * 같은 경로라도 값이 3배 가까이 커져 기준을 같이 올렸다(예전 20/10).
 */
@Composable
private fun scoreColor(score: Int): Color = with(SafeLightTheme.colors) {
    when {
        score >= 60 -> safe
        score >= 30 -> warning
        else -> danger
    }
}

// ── 지도 위 팝업 ────────────────────────────────────────────────────────────

@Composable
private fun PendingPlaceCard(
    place: SearchedPlace,
    onSetStart: () -> Unit,
    onSetDest: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SafeLightTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(SafeIcons.MapPin, null, tint = colors.bluePrimary, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    place.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textStrong,
                )
                if (place.address.isNotBlank()) {
                    Text(
                        place.address,
                        fontSize = 11.5.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                SafeIcons.Close, "닫기",
                tint = colors.textMuted,
                modifier = Modifier.size(17.dp).clickable(onClick = onClose),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledButton("출발지로 설정", RouteColor.start, Modifier.weight(1f), onSetStart)
            FilledButton("도착지로 설정", RouteColor.destination, Modifier.weight(1f), onSetDest)
        }
    }
}

// ── 공용 조각 ───────────────────────────────────────────────────────────────

@Composable
private fun RouteCard(
    title: String,
    titleIcon: ImageVector? = null,
    right: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (titleIcon != null) {
                Icon(titleIcon, null, tint = colors.bluePrimary, modifier = Modifier.size(15.dp))
            }
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textStrong,
                modifier = Modifier.weight(1f),
            )
            right?.invoke()
        }
        content()
    }
}

@Composable
private fun ModeButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) colors.bluePrimary else colors.bg)
            .then(
                if (active) Modifier
                else Modifier.border(1.dp, colors.border, RoundedCornerShape(10.dp)),
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val tint = if (active) Color.White else colors.textMuted
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = tint,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TabButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    val tint = if (active) colors.bluePrimary else colors.textMuted
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                label,
                fontSize = 13.sp,
                color = tint,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        }
        // 웹은 탭 줄 전체에 1px 경계선을 깔고, 고른 탭에만 그 위로 2px 파란 막대를 덧댄다.
        // 안 고른 탭까지 2 로 그리면 카드 한가운데 굵은 회색 줄이 그어진 것처럼 보인다.
        Box(Modifier.fillMaxWidth().height(2.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (active) 2.dp else 1.dp)
                    .background(if (active) colors.bluePrimary else colors.border),
            )
        }
    }
}

@Composable
private fun SortChip(label: String, icon: ImageVector?, active: Boolean, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    val tint = if (active) colors.bluePrimary else colors.textMuted
    Row(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) colors.blueTint else colors.bg)
            .then(
                if (active) Modifier
                else Modifier.border(1.dp, colors.border, RoundedCornerShape(8.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = tint,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun RouteTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            // 아래 4 는 웹 inputStyle 의 marginBottom: 4 다. 이게 없으면 바로 밑에 오는
            // 후보 목록(marginTop 4)과 북마크 정렬 칩(margin 6)이 웹보다 붙어 버린다.
            .padding(bottom = 4.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = colors.textMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = colors.textStrong),
            cursorBrush = SolidColor(colors.bluePrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlaceResultList(
    places: List<SearchedPlace>,
    pinColor: Color,
    onPick: (SearchedPlace) -> Unit,
) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
    ) {
        places.forEachIndexed { index, place ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(place) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(SafeIcons.MapPin, null, tint = pinColor, modifier = Modifier.size(15.dp))
                Column {
                    Text(
                        place.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textStrong,
                    )
                    Text(
                        place.address,
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (index != places.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = SafeLightTheme.colors.textMuted)
    }
}

/** 기본값은 '안전 경로 찾기' 버튼(웹 48/radius 12/15sp + 파란 그림자)이다. */
@Composable
private fun PrimaryButton(
    text: String,
    icon: ImageVector?,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    cornerRadius: Dp = 12.dp,
    fontSize: TextUnit = 15.sp,
    iconSize: Dp = 16.dp,
    elevated: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            // 웹 `0 6px 16px rgba(37,99,235,.28)`. 회색 그림자가 파란 면 아래 깔리면 탁해져서
            // 색을 버튼과 같은 파랑으로 준다.
            .then(
                if (elevated) {
                    Modifier.shadow(9.dp, shape, spotColor = colors.bluePrimary, ambientColor = colors.bluePrimary)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(colors.bluePrimary.copy(alpha = if (enabled) 1f else 0.7f))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun FilledButton(text: String, background: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun OutlineButton(
    text: String,
    icon: ImageVector,
    contentColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .border(
                1.dp,
                if (contentColor == colors.bluePrimary) colors.bluePrimary else colors.border,
                RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(15.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
    }
}

@Composable
private fun MapControlSurface(content: @Composable () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(11.dp)),
    ) { content() }
}

@Composable
private fun MapControlButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun Modifier.alphaIf(condition: Boolean, alpha: Float) =
    if (condition) this.then(Modifier.alpha(alpha)) else this

private fun Context.hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")   // 호출 전에 hasLocationPermission 으로 확인한다
private fun requestCurrentLocation(context: Context, onLocation: (Double, Double) -> Unit) {
    if (!context.hasLocationPermission()) return
    LocationServices.getFusedLocationProviderClient(context).lastLocation
        .addOnSuccessListener { location ->
            if (location != null) onLocation(location.latitude, location.longitude)
        }
        .addOnFailureListener { Log.w(TAG, "위치 조회 실패", it) }
}
