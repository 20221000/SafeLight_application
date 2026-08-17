package com.example.safelight.ui.map

import android.Manifest
import android.annotation.SuppressLint
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
import com.example.safelight.ui.route.ActiveRoute
import com.example.safelight.ui.search.SearchedPlace
import com.example.safelight.ui.theme.SafeLightTheme
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory

private const val TAG = "MapScreen"

// 웹 MapView.jsx 의 모바일 치수(useIsMobile 분기의 값)를 그대로 옮겼다.
private val CHIP_HEIGHT = 26.dp
private val NOTICE_HEIGHT = 24.dp
private val EDGE = 12.dp        // left: isMobile ? 12 : 16
private val TOP = 16.dp

/** 경로 전체를 화면에 담을 때의 여백(웹 setBounds 의 32/24). */
private const val ROUTE_FIT_PADDING_DP = 32

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
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            moveToMyLocation(
                context, mapHolder[0], layersHolder[0],
                recenter = true, onLocation = vm::onLocationKnown,
            )
        }
    }

    // 뷰모델이 새로 계산할 때마다 지도에 다시 그린다(웹의 useEffect 자리).
    LaunchedEffect(vm.visibleCctv, mapReady) {
        layersHolder[0]?.drawCctv(vm.visibleCctv)
    }
    LaunchedEffect(vm.visibleStores, vm.showStoreNames, mapReady) {
        layersHolder[0]?.drawStores(vm.visibleStores, vm.showStoreNames)
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
            onReady = { kakaoMap, layers ->
                mapHolder[0] = kakaoMap
                layersHolder[0] = layers
                mapReady = true
                if (context.hasLocationPermission()) {
                    // 위치 조회는 느리게 끝난다. 그동안 사용자가 보던 화면을 덮어쓰지 않도록
                    // 저장된 카메라가 초기값 그대로일 때만 내 위치로 옮긴다.
                    // 안내 중이면 경로를 화면에 맞추는 쪽이 먼저라 아예 옮기지 않는다.
                    val untouched = camera.latitude == INITIAL_LATITUDE &&
                        camera.longitude == INITIAL_LONGITUDE
                    moveToMyLocation(
                        context, kakaoMap, layers,
                        recenter = untouched && activeRoute == null,
                        onLocation = vm::onLocationKnown,
                    )
                } else {
                    locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
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
            // 가로등은 아직 데이터가 없다 — 켜도 표시할 게 없으므로 잠가둔다.
            LayerChip("streetLamp", SafeIcons.StreetLamp, "가로등", false, vm::toggleFilter, pending = true)
            // safeZone = 편의점(안전거점).
            LayerChip("safeZone", SafeIcons.Store, "편의점", vm.filters.safeZone, vm::toggleFilter)
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
            listOf(vm.cctvNotice to LayerColor.cctv, vm.storeNotice to LayerColor.store)
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
                        if (context.hasLocationPermission()) {
                            moveToMyLocation(
                                context, mapHolder[0], layersHolder[0],
                                recenter = true, onLocation = vm::onLocationKnown,
                            )
                        } else {
                            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                ) {
                    Icon(
                        SafeIcons.Crosshair,
                        contentDescription = "현재 위치로",
                        tint = SafeLightTheme.colors.bluePrimary,
                        modifier = Modifier.size(18.dp),
                    )
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
                    Text(
                        "반경 500m" + (region?.let { " · $it" } ?: ""),
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
                    "근처 위험 구역",
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
                        "주변에 등록된 위험 구역이 없습니다.",
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
        // safetyScore 는 CCTV + 편의점 합계다(RouteService.analyzeSafetyData).
        Text(
            "경로 안내 중 · 안전시설 ${safetyScore}곳 경유",
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

/** 웹의 흰 카드(surface + border + radius 11 + shadow)를 쓴 지도 컨트롤 껍데기. */
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

private fun android.content.Context.hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** 경로 전체가 한 화면에 들어오게 카메라를 맞춘다(웹 `setBounds`). [paddingPx] 는 화면 픽셀이다. */
private fun KakaoMap.fitToRoute(route: ActiveRoute, paddingPx: Int) {
    if (route.path.isEmpty()) return
    val points = route.path.map { LatLng.from(it.latitude, it.longitude) }.toTypedArray()
    moveCamera(CameraUpdateFactory.fitMapPoints(points, paddingPx))
}

/**
 * 내 위치 표시. [recenter] 면 그 자리로 지도를 옮긴다 —
 * 사용자가 이미 다른 곳을 보고 있을 때 화면을 뺏지 않기 위해 마커만 찍는 경우와 나눈다.
 */
@SuppressLint("MissingPermission")   // 호출 전에 hasLocationPermission 으로 확인한다
private fun moveToMyLocation(
    context: android.content.Context,
    map: KakaoMap?,
    layers: MapLayers?,
    recenter: Boolean,
    onLocation: (Double, Double) -> Unit = { _, _ -> },
) {
    if (map == null || !context.hasLocationPermission()) return
    LocationServices.getFusedLocationProviderClient(context).lastLocation
        .addOnSuccessListener { location ->
            if (location == null) return@addOnSuccessListener
            val here = LatLng.from(location.latitude, location.longitude)
            layers?.drawMyLocation(here)
            if (recenter) map.moveCamera(CameraUpdateFactory.newCenterPosition(here, FACILITY_MIN_ZOOM))
            onLocation(location.latitude, location.longitude)
        }
        .addOnFailureListener { Log.w(TAG, "위치 조회 실패", it) }
}
