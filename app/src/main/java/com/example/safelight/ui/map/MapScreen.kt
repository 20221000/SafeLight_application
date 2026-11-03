package com.example.safelight.ui.map

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.net.DangerZoneDto
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.layout.DragSheet
import com.example.safelight.ui.layout.DragSheetState
import com.example.safelight.ui.layout.rememberDragSheetState
import com.example.safelight.ui.sos.SosButton
import com.example.safelight.ui.route.ActiveRoute
import com.example.safelight.ui.search.SearchedPlace
import com.example.safelight.ui.theme.SafeLightTheme
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.example.safelight.ui.common.MapControlButton
import com.example.safelight.ui.common.MapControlSurface
import com.example.safelight.ui.common.LOCATION_PERMISSIONS
import com.example.safelight.ui.common.TRACK_INTERVAL_MS
import com.example.safelight.ui.common.checkLocationSettings
import com.example.safelight.ui.common.hasLocationPermission
import com.example.safelight.ui.common.isLocationEnabled
import com.example.safelight.ui.common.rememberMyLocation
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kakao.vectormap.camera.CameraAnimation

// 웹 MapView.jsx 의 모바일 치수(useIsMobile 분기의 값)를 그대로 옮겼다.
private val CHIP_HEIGHT = 26.dp
private val NOTICE_HEIGHT = 24.dp
private val EDGE = 12.dp        // left: isMobile ? 12 : 16
private val TOP = 16.dp

/** 경로 전체를 화면에 담을 때의 여백(웹 setBounds 의 32/24). */
private const val ROUTE_FIT_PADDING_DP = 32

/**
 * 내 위치 점과 따라가는 카메라가 새 위치로 미끄러지는 시간. 위치가 오는 간격([TRACK_INTERVAL_MS])과
 * 같게 둔다 — 짧으면 움직였다 멈췄다를 반복하고(예전 400ms), 길면 실제 위치보다 늦게 따라온다.
 */
private val FOLLOW_ANIMATION_MS = TRACK_INTERVAL_MS.toInt()

/**
 * 웹 MapView.jsx 의 자리. 지도 + 좌상단 레이어 칩 + 안내 문구 + 우하단 줌·현재위치 버튼.
 */
@Composable
fun MapScreen(
    camera: MapCameraState,
    modifier: Modifier = Modifier,
    searchTarget: SearchedPlace? = null,
    activeRoute: ActiveRoute? = null,
    onCancelRoute: () -> Unit = {},
    /** SOS 는 계정에 붙는다. 로그인하지 않았으면 누를 때 로그인 화면으로 보낸다(웹과 같다). */
    loggedIn: Boolean = true,
    onNeedLogin: () -> Unit = {},
    vm: MapViewModel = viewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val sheet = rememberDragSheetState()
    // 화면을 떠날 때 카메라를 읽어 저장해야 하므로 준비된 지도를 붙들어 둔다.
    val mapHolder = remember { arrayOfNulls<KakaoMap>(1) }
    val layersHolder = remember { arrayOfNulls<MapLayers>(1) }
    // 지도가 준비됐다는 걸 알리는 상태. 위 두 배열은 그냥 배열이라 값을 넣어도 아무도 다시 그리지 않는다 —
    // 아래 effect 들이 이 값을 키로 삼아야 지도가 늦게 준비돼도 한 번 더 그린다.
    var mapReady by remember { mutableStateOf(false) }

    // 탭을 떠나면 여기서 마지막 위치를 받아 적어둔다. 다시 들어오면 그 자리에서 시작한다.
    DisposableEffect(Unit) {
        onDispose {
            mapHolder[0]?.cameraPosition?.let { pos ->
                camera.latitude = pos.position.latitude
                camera.longitude = pos.position.longitude
                camera.zoom = pos.zoomLevel
            }
        }
    }

    // 위치 권한. 웹은 브라우저가 물어보지만 안드로이드는 앱이 직접 띄워야 한다.
    // 권한을 받는 순간 아래 rememberMyLocation 이 위치를 받기 시작한다.
    // 정밀·대략 위치를 함께 묻고, 둘 중 하나라도 허용이면 쓴다(LOCATION_PERMISSIONS 주석 참고).
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> hasLocationPermission = result.values.any { it } }

    // 휴대폰 위치(GPS)가 꺼져 있으면 권한이 있어도 위치가 오지 않는다. 예전에는 점이 그냥 안 나왔다.
    // 권한을 받으면 한 번 시스템의 '위치 사용' 창을 띄우고, 거절하면 안내 줄로 알린다.
    var locationOn by remember { mutableStateOf(context.isLocationEnabled()) }
    val locationSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { locationOn = context.isLocationEnabled() }
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
        context.checkLocationSettings { needed ->
            locationSettings.launch(IntentSenderRequest.Builder(needed.resolution).build())
        }
    }
    // 설정 앱에서 위치나 권한을 바꾸고 돌아올 수 있다 — 화면에 돌아올 때마다 다시 확인한다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationOn = context.isLocationEnabled()
                hasLocationPermission = context.hasLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 내 위치는 계속 받는다. 따라가기(follow)가 켜져 있으면 지도가 나를 따라오고,
    // 지도를 손으로 끌면 꺼진다. '현재 위치' 버튼이 다시 켠다(웹과 같은 방식).
    val myLocation = rememberMyLocation(enabled = hasLocationPermission)
    // 처음부터 따라가는 건 아무것도 안 보던 상태로 들어왔을 때뿐이다.
    // 탭을 떠났다 오면(저장된 카메라) 보던 자리를, 검색·경로로 들어오면 그쪽을 먼저 보여준다.
    var follow by remember {
        mutableStateOf(
            camera.latitude == INITIAL_LATITUDE && camera.longitude == INITIAL_LONGITUDE &&
                activeRoute == null && searchTarget == null,
        )
    }
    // 위치를 못 받는 이유를 레이어 안내 줄 맨 위에 띄운다(웹과 같은 문구).
    val locationNotice = when {
        !hasLocationPermission -> "위치 권한이 꺼져 있어 내 위치를 표시할 수 없습니다"
        !locationOn -> "내 위치를 확인할 수 없습니다 · 휴대폰 위치(GPS)를 켜 주세요"
        myLocation == null -> "내 위치를 찾는 중입니다…"
        else -> ""
    }
    // 버튼은 실제로 따라가고 있을 때만 채운다 — 위치가 없는데 '따라가는 중'으로 보이면 헷갈린다.
    val following = follow && myLocation != null && hasLocationPermission && locationOn
    // 첫 위치에서만 확대 단계를 시설이 보이는 높이로 맞춘다. 그 뒤로는 사용자가 정한 확대를 지킨다.
    val centeredOnce = remember { booleanArrayOf(false) }
    LaunchedEffect(myLocation, mapReady, follow) {
        val (latitude, longitude) = myLocation ?: return@LaunchedEffect
        val map = mapHolder[0] ?: return@LaunchedEffect
        val here = LatLng.from(latitude, longitude)
        // 점과 카메라를 같은 시간 동안 함께 옮긴다. 다음 위치가 올 즈음 이동이 끝나서 걷는 대로 이어져 보인다.
        layersHolder[0]?.drawMyLocation(here, glideMs = FOLLOW_ANIMATION_MS)
        vm.onLocationKnown(latitude, longitude)
        if (!follow) return@LaunchedEffect
        if (!centeredOnce[0]) {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(here, FACILITY_MIN_ZOOM))
            centeredOnce[0] = true
        } else {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(here), CameraAnimation.from(FOLLOW_ANIMATION_MS))
        }
    }

    // 뷰모델이 새로 계산할 때마다 지도에 다시 그린다(웹의 useEffect 자리).
    LaunchedEffect(vm.visibleCctv, mapReady) {
        layersHolder[0]?.drawCctv(vm.visibleCctv)
    }
    LaunchedEffect(vm.visibleLamps, mapReady) {
        layersHolder[0]?.drawLamps(vm.visibleLamps)
    }
    LaunchedEffect(vm.visibleStores, mapReady) {
        layersHolder[0]?.drawStores(vm.visibleStores)
    }
    LaunchedEffect(vm.visiblePolice, mapReady) {
        layersHolder[0]?.drawPolice(vm.visiblePolice)
    }
    LaunchedEffect(vm.dangerZones, mapReady) {
        layersHolder[0]?.drawDangerZones(vm.dangerZones)
    }

    // 시트가 지도 아래를 덮는다. 그만큼을 지도에 알려 두면 검색 핀·경로 맞추기가 알아서
    // 보정된다 — 알리지 않으면 찍은 핀이 시트 뒤에 숨는다(경로 화면과 같은 처리다).
    val mapBottomPaddingPx = sheet.peekPx.toInt()
    LaunchedEffect(mapBottomPaddingPx, mapReady) {
        mapHolder[0]?.setPadding(0, 0, 0, mapBottomPaddingPx)
    }

    // 헤더에서 장소를 고르면 그 자리로 옮기고 핀을 꽂는다(웹 MapView 의 searchTarget effect).
    LaunchedEffect(searchTarget, mapReady) {
        val place = searchTarget ?: return@LaunchedEffect
        val map = mapHolder[0] ?: return@LaunchedEffect
        follow = false   // 검색한 곳을 보는 중이다 — 내 위치로 끌고 가지 않는다.
        val position = LatLng.from(place.latitude, place.longitude)
        map.moveCamera(CameraUpdateFactory.newCenterPosition(position, SEARCH_ZOOM))
        layersHolder[0]?.drawSearchPin(position, place.name)
    }

    // 경로 안내에서 넘어온 경로. 취소되면(activeRoute == null) 그려둔 선과 마커를 지운다.
    LaunchedEffect(activeRoute, mapReady) {
        val layers = layersHolder[0] ?: return@LaunchedEffect
        val route = activeRoute
        if (route == null) {
            layers.drawRoute(emptyList())
            return@LaunchedEffect
        }
        layers.drawRoute(
            path = route.path,
            start = route.start?.toLatLng(),
            destination = route.destination?.toLatLng(),
            lineColor = RouteColor.activeLine,
            startColor = RouteColor.activeLine,
            startTextColor = RouteColor.activeStartText,
            destinationColor = RouteColor.activeDestination,
        )
        // 경로 전체를 화면에 맞춘다. 따라가기가 켜져 있으면 다음 위치에 바로 내 위치로 끌려간다.
        follow = false
        mapHolder[0]?.fitToRoute(route, (ROUTE_FIT_PADDING_DP * density).toInt())
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { sheet.containerHeightPx = it.height.toFloat() },
    ) {
        KakaoMapHost(
            initialPosition = LatLng.from(camera.latitude, camera.longitude),
            initialZoom = camera.zoom,
            onCameraIdle = { map -> map.currentBounds()?.let { vm.onCameraIdle(it, map.zoomLevel) } },
            onUserDrag = { follow = false },
            onReady = { kakaoMap, layers ->
                mapHolder[0] = kakaoMap
                layersHolder[0] = layers
                mapReady = true
                // 위치는 rememberMyLocation 이 받아 오고, 위 effect 가 점과 카메라를 맞춘다.
                if (!hasLocationPermission) locationPermission.launch(LOCATION_PERMISSIONS)
            },
        )

        // ── 경로 안내 배너 ──────────────────────────────────────────────────
        // 안내 중 나갈 방법이 이것뿐이라 눌러야 한다는 걸 알 수 있게 ✕ 를 함께 둔다.
        // 모바일은 좌상단 레이어 칩과 같은 높이에 놓으면 겹치므로 칩 아래로 내린다(웹과 같다).
        if (activeRoute != null) {
            RouteBanner(
                safetyScore = activeRoute.safetyScore,
                onClick = onCancelRoute,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = TOP + CHIP_HEIGHT + 10.dp),
            )
        }

        // ── 레이어 칩 (좌상단) ───────────────────────────────────────────────
        Row(
            modifier = Modifier.padding(start = EDGE, top = TOP),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LayerChip("cctv", SafeIcons.Cctv, "CCTV", vm.filters.cctv, vm::toggleFilter)
            LayerChip("streetLamp", SafeIcons.StreetLamp, "가로등", vm.filters.streetLamp, vm::toggleFilter)
            LayerChip("store", SafeIcons.Store, "편의점", vm.filters.store, vm::toggleFilter)
            LayerChip("police", SafeIcons.Shield, "치안시설", vm.filters.police, vm::toggleFilter)
        }

        // ── 레이어 안내 ─────────────────────────────────────────────────────
        // 몇 곳을 찾았는지, 왜 안 보이는지(너무 넓게 봄), 결과가 잘렸는지 알린다.
        // 앞의 색 점이 지도 위 점 색과 같아서 어느 레이어 얘기인지 바로 읽힌다.
        Column(
            modifier = Modifier.padding(
                start = EDGE,
                // 안내 배너가 뜨면 그만큼 아래로 밀린다 — 배너와 겹치면 둘 다 못 읽는다.
                top = TOP + CHIP_HEIGHT + 8.dp + if (activeRoute != null) 38.dp else 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(
                locationNotice to SafeLightTheme.colors.bluePrimary,
                vm.cctvNotice to LayerColor.cctv,
                vm.lampNotice to LayerColor.streetLamp,
                vm.storeNotice to LayerColor.store,
                vm.policeNotice to LayerColor.police,
            )
                .filter { it.first.isNotEmpty() }
                .forEach { (text, color) -> LayerNotice(text, color) }
        }

        // ── 현재 위치 버튼 · 줌 컨트롤 (우하단) ────────────────────────────────
        // 시트가 가리는 만큼 위로 피한다. 시트를 다 올려도 mid 까지만 따라 올라간다
        // (웹의 --ls-sheet-peek 과 같은 값이다).
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp)
                .padding(bottom = with(LocalDensity.current) { sheet.peekPx.toDp() } + 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(28.dp),   // 웹: 현재위치 bottom 108 / 줌 bottom 20
        ) {
            MapControlSurface {
                MapControlButton(
                    onClick = {
                        if (!context.hasLocationPermission()) {
                            locationPermission.launch(LOCATION_PERMISSIONS)
                            return@MapControlButton
                        }
                        // GPS 가 꺼져 있으면 '위치 사용' 창을 다시 띄운다(처음에 거절했어도 여기서 켤 수 있다).
                        if (!locationOn) {
                            context.checkLocationSettings { needed ->
                                locationSettings.launch(IntentSenderRequest.Builder(needed.resolution).build())
                            }
                        }
                        // 위치는 이미 계속 받고 있다. 따라가기를 켜고 지금 위치로 옮기기만 하면 된다.
                        // 아직 첫 위치가 안 왔으면 켜 두기만 한다 — 위치가 오는 대로 따라간다.
                        follow = true
                        val (latitude, longitude) = myLocation ?: return@MapControlButton
                        mapHolder[0]?.moveCamera(
                            CameraUpdateFactory.newCenterPosition(LatLng.from(latitude, longitude)),
                        )
                    },
                ) {
                    // 따라가기 중이면 버튼을 채워서 켜져 있다는 걸 보여준다(웹과 같다).
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(if (following) SafeLightTheme.colors.bluePrimary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            SafeIcons.Crosshair,
                            contentDescription = if (following) "내 위치 따라가는 중" else "현재 위치로",
                            tint = if (following) Color.White else SafeLightTheme.colors.bluePrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            MapControlSurface {
                Column {
                    MapControlButton(onClick = { mapHolder[0]?.moveCamera(CameraUpdateFactory.zoomIn()) }) {
                        Text("+", fontSize = 19.sp, color = SafeLightTheme.colors.textStrong)
                    }
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 1.dp)
                            .background(SafeLightTheme.colors.border),
                    )
                    MapControlButton(onClick = { mapHolder[0]?.moveCamera(CameraUpdateFactory.zoomOut()) }) {
                        Text("−", fontSize = 19.sp, color = SafeLightTheme.colors.textStrong)
                    }
                }
            }
        }

        // ── 내 주변 안전 현황 (바텀시트) ─────────────────────────────────────
        // 웹은 MobileShell 이 rightDrawer(RightPanel)를 시트로 내려준다. 데스크탑의 320px
        // 우측 패널 자리이며, 지도만 있고 이 시트가 없으면 활성 위험구역이 몇 건인지
        // 목록으로는 볼 방법이 없다(지도 위 원을 눈으로 세는 수밖에 없다).
        SafetySheet(
            sheet = sheet,
            zones = vm.dangerZones,
            region = vm.regionName,
            loading = vm.zonesLoading,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // ── 긴급 SOS ────────────────────────────────────────────────────────
        // 지도 중앙 하단의 상시 버튼. 시트가 가리는 만큼 위로 피하되 mid 까지만 따라 올라간다.
        //
        // 시트보다 **뒤에** 그린다. 앞에 두면 시트를 끝까지 올렸을 때 SOS 가 그 밑에 깔려
        // 사라진다 — 위급할 때 누를 것이 화면에서 없어지는 셈이라 이 화면에서 제일 나쁜 상태다.
        // 웹도 같은 이유로 SOS 에 z-index 100, 시트에 25 를 줘서 시트 위에 띄운다.
        SosButton(
            loggedIn = loggedIn,
            onNeedLogin = onNeedLogin,
            bottomPadding = with(LocalDensity.current) { sheet.peekPx.toDp() },
            // 접수 직후 위험구역을 다시 읽는다 — 내가 만든 구역이 30초 뒤에 나타나면 안 된다.
            onReported = vm::refreshDangerZones,
        )
    }
}

/** 웹 RightPanel 의 내용. 머리말(제목 블록)이 시트의 mid 높이가 된다. */
@Composable
private fun SafetySheet(
    sheet: DragSheetState,
    zones: List<DangerZoneDto>,
    region: String?,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SafeLightTheme.colors
    DragSheet(
        state = sheet,
        modifier = modifier,
        head = {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "내 주변 안전 현황",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textStrong,
                    )
                    // 거리로 거르지 않는다. GET /danger-zones 는 활성 구역을 전부 주고 위치도 반경도 받지 않아
                    // '반경 500m'·'근처' 라고 쓰면 몇 km 밖 구역이 그 말 밑에 그대로 나온다(웹과 같이 고쳤다).
                    Text(
                        region ?: "위치 확인 중",
                        fontSize = 11.5.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.safe.copy(alpha = .12f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.safe))
                    Text(
                        if (loading) "갱신중" else "LIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.safe,
                    )
                }
            }
        },
        body = {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "활성 위험 구역",
                    Modifier.weight(1f),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textStrong,
                )
                Text("${zones.size}건", fontSize = 11.sp, color = colors.textMuted)
            }
            Column(
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (zones.isEmpty()) {
                    Text(
                        "지금 활성된 위험 구역이 없습니다.",
                        Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        fontSize = 12.5.sp,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                zones.forEach { zone -> ZoneRow(zone) }
            }
        },
    )
}

@Composable
private fun ZoneRow(zone: DangerZoneDto) {
    val colors = SafeLightTheme.colors
    val level = dangerLevelColor(zone.dangerLevel)
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape),
    ) {
        // 위험도는 왼쪽 색 띠로 먼저 읽힌다(웹의 borderLeft 4px).
        Box(Modifier.width(4.dp).fillMaxHeight().background(level))
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "위험구역 #${zone.dangerZoneId}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textStrong,
                    )
                    Text(
                        "%.4f, %.4f".format(zone.centerLatitude, zone.centerLongitude),
                        Modifier.padding(top = 2.dp),
                        fontSize = 11.sp,
                        color = colors.textMuted,
                    )
                }
                Text(
                    zone.dangerLevel,
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(level.copy(alpha = .12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = level,
                )
            }
            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(SafeIcons.MapPin, null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
                Text(
                    "신고 ${zone.reportCount}건 · 반경 ${zone.radius.toInt()}m",
                    fontSize = 11.5.sp,
                    color = colors.textMuted,
                )
            }
        }
    }
}

/** 안내 중임을 알리는 파란 알약. 누르면 안내를 취소한다(웹과 같다). */
@Composable
private fun RouteBanner(safetyScore: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SafeLightTheme.colors.bluePrimary)
            .clickable(onClick = onClick)
            .padding(start = 13.dp, end = 11.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SafeIcons.Compass, null, tint = Color.White, modifier = Modifier.size(13.dp))
        // safetyScore 는 개수가 아니라 가중 점수다 — CCTV×3 + 편의점×3 + 보안등×1 + 치안시설×4
        // (RouteService.calculateWeightedSafetyScore). '곳'이라고 적으면 시설 수로 오해한다.
        Text(
            "경로 안내 중 · 안전 점수 ${safetyScore}점",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Box(
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SafeIcons.Close, "경로 안내 취소", tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}

/**
 * 레이어 켬/끔 칩. 웹과 같이 켜면 파란 배경 + 흰 글씨, 끄면 흰 배경 + 회색 글씨다.
 * [pending] 은 데이터가 없어 잠긴 레이어(가로등) — 반투명으로 눌리지 않음을 알린다.
 */
@Composable
private fun LayerChip(
    key: String,
    icon: ImageVector,
    label: String,
    on: Boolean,
    onToggle: (String) -> Unit,
    pending: Boolean = false,
) {
    val colors = SafeLightTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .height(CHIP_HEIGHT)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) colors.bluePrimary else colors.surface)
            .then(
                if (on) Modifier
                else Modifier.border(1.dp, colors.border, RoundedCornerShape(20.dp)),
            )
            .clickable(enabled = !pending) { onToggle(key) }
            .padding(horizontal = 9.dp),
    ) {
        val tint = when {
            pending -> colors.textMuted.copy(alpha = 0.5f)
            on -> Color.White
            else -> colors.textMuted
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun LayerNotice(text: String, dotColor: Color) {
    val colors = SafeLightTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(NOTICE_HEIGHT)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
    }
}

/** 경로 전체가 한 화면에 들어오게 카메라를 맞춘다(웹 `setBounds`). [paddingPx] 는 화면 픽셀이다. */
private fun KakaoMap.fitToRoute(route: ActiveRoute, paddingPx: Int) {
    if (route.path.isEmpty()) return
    val points = route.path.map { LatLng.from(it.latitude, it.longitude) }.toTypedArray()
    moveCamera(CameraUpdateFactory.fitMapPoints(points, paddingPx))
}
