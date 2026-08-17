package com.example.safelight.ui.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.net.DangerZoneDto
import com.example.safelight.data.net.EmergencyReportDto
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.map.KakaoMapHost
import com.example.safelight.ui.map.MapLayers
import com.example.safelight.ui.theme.SafeLightTheme
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/** 지도가 구역 하나를 비출 때의 확대 수준. 반경 수백 미터가 화면에 들어오는 정도다. */
private const val ZONE_ZOOM = 16

/**
 * 위험 구역. 웹 AdminDangerZonePage 의 모바일 배치를 옮겼다 —
 * 지도를 위에 붙박이로 두고 목록만 흘러간다. 카드를 훑는 내내 지도가 보여야
 * "지금 고른 구역이 어디였지"를 확인하러 되돌아갈 일이 없다.
 */
@Composable
fun AdminZoneScreen(
    onReportsChanged: () -> Unit = {},
    vm: AdminZoneViewModel = viewModel(),
) {
    val colors = SafeLightTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var levelSheetZone by remember { mutableStateOf<DangerZoneDto?>(null) }
    var confirmDeactivate by remember { mutableStateOf<DangerZoneDto?>(null) }
    var confirmFalse by remember { mutableStateOf<EmergencyReportDto?>(null) }
    var confirmUnfalse by remember { mutableStateOf<EmergencyReportDto?>(null) }

    val mapHolder = remember { arrayOfNulls<KakaoMap>(1) }
    val layersHolder = remember { arrayOfNulls<MapLayers>(1) }
    var mapReady by remember { mutableStateOf(false) }
    // 전체 보기는 처음 한 번뿐이다. 그 뒤로 카메라를 건드리면 보고 있던 자리가 튄다.
    var fitted by remember { mutableStateOf(false) }
    val fitPadding = with(LocalDensity.current) { 24.dp.roundToPx() }

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.messageShown()
    }
    LaunchedEffect(vm.reportsRevision) {
        if (vm.reportsRevision > 0) onReportsChanged()
    }
    // 고른 구역은 테두리를 굵게, 채움을 진하게 그린다(웹과 같다). 그래서 focusId 도 키다.
    LaunchedEffect(vm.zones, vm.focusId, mapReady) {
        layersHolder[0]?.drawDangerZones(vm.zones, selectedId = vm.focusId)
    }
    // 처음 한 번은 구역 전체가 담기게 맞춘다. 기본 위치에서 시작하면 구역이 화면 밖일 때
    // 카드를 누르기 전까지 빈 지도만 보인다.
    LaunchedEffect(vm.zones, mapReady) {
        if (fitted || vm.zones.isEmpty()) return@LaunchedEffect
        val map = mapHolder[0] ?: return@LaunchedEffect
        fitted = true
        map.moveCamera(CameraUpdateFactory.fitMapPoints(vm.zones.cornerPoints(), fitPadding))
    }
    // seq 까지 키로 삼아야 같은 구역을 다시 눌러도 지도가 되돌아온다.
    LaunchedEffect(vm.focusSeq, mapReady) {
        val zone = vm.zones.firstOrNull { it.dangerZoneId == vm.focusId } ?: return@LaunchedEffect
        mapHolder[0]?.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(zone.centerLatitude, zone.centerLongitude),
                ZONE_ZOOM,
            ),
        )
    }

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                    .height(220.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
            ) {
                KakaoMapHost(
                    modifier = Modifier.fillMaxSize(),
                    onReady = { map, layers ->
                        mapHolder[0] = map
                        layersHolder[0] = layers
                        // 지도에서 원을 눌러도 구역이 골라진다(웹은 circle 의 click 이벤트를 쓴다).
                        // 카카오 v2 의 폴리곤에는 클릭 이벤트가 없어서 누른 좌표로 직접 찾는다.
                        map.setOnMapClickListener { _, position, _, _ ->
                            vm.zones.zoneAt(position)?.let { vm.focus(it.dangerZoneId) }
                        }
                        mapReady = true
                    },
                )
                // 구역이 없으면 지도만 덩그러니 남아 '아직 안 왔나'와 '없다'가 구분되지 않는다.
                // 글자를 지도 위에 바로 얹으면 상호·도로명과 겹쳐 읽히지 않아 알약을 깔아 준다.
                if (vm.zones.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().background(colors.bg.copy(alpha = .72f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (vm.loading) "불러오는 중…" else "활성 위험구역이 없습니다.",
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            Text(
                if (vm.loading) "불러오는 중…"
                else "활성 ${vm.zones.size} · HIGH ${vm.highCount} · 이번 주 신규 ${vm.newThisWeek}",
                Modifier.padding(horizontal = 16.dp),
                fontSize = 12.5.sp,
                color = colors.textMuted,
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                vm.error?.let { item { AdminErrorBox("데이터를 불러오지 못했습니다: $it") } }

                if (vm.zones.isEmpty()) {
                    item {
                        AdminEmptyCard(if (vm.loading) "불러오는 중…" else "활성 위험구역이 없습니다.")
                    }
                } else {
                    items(vm.zones, key = { it.dangerZoneId }) { zone ->
                        ZoneCard(
                            zone = zone,
                            focused = vm.focusId == zone.dangerZoneId,
                            onFocus = { vm.focus(zone.dangerZoneId) },
                            onDetail = { vm.openDetail(zone) },
                            onLevel = { levelSheetZone = zone },
                            onDeactivate = { confirmDeactivate = zone },
                        )
                    }
                }
            }
        }
    }

    levelSheetZone?.let { zone ->
        AdminActionSheet(
            title = "위험구역 #${zone.dangerZoneId} 위험도",
            subtitle = "현재 ${zone.dangerLevel} · 반경 ${zone.radius.toInt()}m",
            actions = DANGER_LEVELS.map { level ->
                SheetAction(
                    label = level,
                    tone = when (level) {
                        "HIGH" -> ActionTone.Danger
                        "LOW" -> ActionTone.Safe
                        else -> ActionTone.Default
                    },
                    enabled = level != zone.dangerLevel,
                    caption = "지금 등급입니다".takeIf { level == zone.dangerLevel },
                    onClick = { vm.changeLevel(zone, level) },
                )
            },
            onClose = { levelSheetZone = null },
        )
    }

    confirmDeactivate?.let { zone ->
        AdminConfirmDialog(
            title = "위험구역 #${zone.dangerZoneId} 을(를) 비활성화할까요?",
            message = "목록과 지도에서 사라집니다. 이 구역에 걸린 신고 기록은 그대로 남습니다.",
            confirmLabel = "비활성화",
            danger = true,
            onConfirm = { vm.deactivate(zone); confirmDeactivate = null },
            onDismiss = { confirmDeactivate = null },
        )
    }

    // 신고 관리 탭과 같은 처리라 같은 문구로 묻는다. 여기서만 그냥 나가면
    // 어느 쪽이 진짜 무거운 처리인지 알 수 없다.
    confirmFalse?.let { report ->
        AdminConfirmDialog(
            title = markFalseTitle(report.reportId),
            message = MARK_FALSE_MESSAGE,
            confirmLabel = "허위신고로 확정",
            danger = true,
            dismissLabel = "닫기",
            onConfirm = { vm.markFalse(report); confirmFalse = null },
            onDismiss = { confirmFalse = null },
        )
    }

    confirmUnfalse?.let { report ->
        AdminConfirmDialog(
            title = cancelFalseTitle(report.reportId),
            message = CANCEL_FALSE_MESSAGE,
            confirmLabel = "허위신고 취소",
            dismissLabel = "닫기",
            onConfirm = { vm.cancelFalse(report); confirmUnfalse = null },
            onDismiss = { confirmUnfalse = null },
        )
    }

    vm.detailZone?.let { zone ->
        ZoneDetailDialog(
            zone = zone,
            vm = vm,
            onMarkFalse = { confirmFalse = it },
            onCancelFalse = { confirmUnfalse = it },
        )
    }
}

@Composable
private fun ZoneCard(
    zone: DangerZoneDto,
    focused: Boolean,
    onFocus: () -> Unit,
    onDetail: () -> Unit,
    onLevel: () -> Unit,
    onDeactivate: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    val level = levelBadge(zone.dangerLevel, colors)
    val remain = remainText(zone.expiredAt)

    AdminCard(borderColor = if (focused) colors.bluePrimary else null) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            // 위쪽을 누르면 지도가 이 구역으로 옮겨간다. 신고 내역은 아래 버튼으로 따로 연다 —
            // 창이 뜨면 지도가 가려진다.
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onFocus),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ZoneThumb(zone.radius, level.color)
                Column(Modifier.weight(1f)) {
                    Text(
                        "위험구역 #${zone.dangerZoneId}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textStrong,
                    )
                    Text(
                        "%.3f, %.3f".format(zone.centerLatitude, zone.centerLongitude),
                        Modifier.padding(top = 3.dp),
                        fontSize = 11.5.sp,
                        color = colors.textMuted,
                    )
                    Row(
                        Modifier.padding(top = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        AdminBadge(level)
                        Text("반경 ${zone.radius.toInt()}m", fontSize = 11.5.sp, color = colors.textMuted)
                        Text("신고 ${zone.reportCount}", fontSize = 11.5.sp, color = colors.textMuted)
                    }
                    // 24시간이 지나면 목록에서 사라진다. 남은 시간을 미리 알려 둔다.
                    if (remain != null) {
                        Text(
                            remain,
                            Modifier.padding(top = 4.dp),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpiringSoon(zone.expiredAt)) colors.warning else colors.textMuted,
                        )
                    }
                }
                // 화살표는 '다음 화면으로'로 읽힌다. 이 버튼이 하는 일은 지도 이동이라 핀으로 둔다.
                Icon(
                    SafeIcons.MapPin,
                    "지도에서 보기",
                    tint = if (focused) colors.bluePrimary else colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }

            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(colors.border))

            Row(
                Modifier.padding(top = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdminActionButton("신고 내역", Modifier.weight(1f), onClick = onDetail)
                AdminActionButton("위험도 갱신", Modifier.weight(1f), onClick = onLevel)
                AdminActionButton(
                    "비활성화",
                    Modifier.weight(1f),
                    tone = ActionTone.Danger,
                    onClick = onDeactivate,
                )
            }
        }
    }
}

// ── 지도 위의 구역 찾기 ───────────────────────────────────────────────────────
// 위도 1도는 어디서나 약 111km, 경도 1도는 위도가 높을수록 좁아진다(cos 보정).
// 서울 안에서 몇 백 미터를 재는 데는 이 근사로 충분하다.

private const val METERS_PER_DEGREE = 111_320.0

/** 구역 전체가 담기는 네 귀퉁이. `fitMapPoints` 가 이 점들을 다 넣을 배율을 골라 준다. */
private fun List<DangerZoneDto>.cornerPoints(): Array<LatLng> = flatMap { zone ->
    val latDelta = zone.radius / METERS_PER_DEGREE
    val lngDelta = latDelta / max(0.1, cos(Math.toRadians(zone.centerLatitude)))
    listOf(
        LatLng.from(zone.centerLatitude + latDelta, zone.centerLongitude + lngDelta),
        LatLng.from(zone.centerLatitude - latDelta, zone.centerLongitude - lngDelta),
    )
}.toTypedArray()

/**
 * 누른 자리에 있는 구역. 원이 겹쳐 있으면 중심이 더 가까운 쪽을 고른다 —
 * 큰 구역 안에 작은 구역이 들어 있을 때 큰 쪽만 잡히면 작은 쪽은 영영 못 고른다.
 */
private fun List<DangerZoneDto>.zoneAt(point: LatLng): DangerZoneDto? {
    val scale = max(0.1, cos(Math.toRadians(point.latitude)))
    return mapNotNull { zone ->
        val dLat = (zone.centerLatitude - point.latitude) * METERS_PER_DEGREE
        val dLng = (zone.centerLongitude - point.longitude) * METERS_PER_DEGREE * scale
        val distance = hypot(dLat, dLng)
        if (distance <= zone.radius) zone to distance else null
    }.minByOrNull { it.second }?.first
}

/** 반경 크기를 눈으로 견주는 썸네일. 실지도 대신 비율만 보여준다(웹의 미니맵 자리). */
@Composable
private fun ZoneThumb(radius: Double, color: androidx.compose.ui.graphics.Color) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(72.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            // 반경 그대로 그리면 큰 구역이 칸을 넘는다. 10~30 사이로 눌러 담는다(웹과 같은 식).
            val r = (10.0 + radius / 8.0).coerceIn(10.0, 30.0).toFloat() * density
            drawCircle(color.copy(alpha = .12f), radius = r, center = center)
            drawCircle(
                color.copy(alpha = .5f),
                radius = r,
                center = center,
                style = Stroke(
                    width = 1.5f * density,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * density, 3f * density)),
                ),
            )
            drawCircle(color, radius = 4.5f * density, center = center)
        }
    }
}

@Composable
private fun ZoneDetailDialog(
    zone: DangerZoneDto,
    vm: AdminZoneViewModel,
    onMarkFalse: (EmergencyReportDto) -> Unit,
    onCancelFalse: (EmergencyReportDto) -> Unit,
) {
    val colors = SafeLightTheme.colors
    AlertDialog(
        onDismissRequest = vm::closeDetail,
        title = {
            Text(
                "위험구역 #${zone.dangerZoneId} · 신고 내역",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column {
                Text(
                    "${zone.dangerLevel} · 반경 ${zone.radius.toInt()}m · 신고 ${zone.reportCount}건" +
                        (remainText(zone.expiredAt)?.let { " · $it" } ?: ""),
                    fontSize = 12.sp,
                    color = colors.textMuted,
                )
                when {
                    vm.detailLoading -> Text(
                        "신고 내역 불러오는 중…",
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        fontSize = 13.sp,
                        color = colors.textMuted,
                    )

                    vm.detailReports.isEmpty() -> Text(
                        "이 구역에 접수된 신고가 없습니다.",
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        fontSize = 13.sp,
                        color = colors.textMuted,
                    )

                    else -> LazyColumn(
                        Modifier.padding(top = 12.dp).height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(vm.detailReports, key = { it.reportId }) { report ->
                            DetailReportRow(report, vm, onMarkFalse, onCancelFalse)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::closeDetail) { Text("닫기") } },
        containerColor = colors.surface,
        titleContentColor = colors.textStrong,
        textContentColor = colors.textMuted,
    )
}

@Composable
private fun DetailReportRow(
    report: EmergencyReportDto,
    vm: AdminZoneViewModel,
    onMarkFalse: (EmergencyReportDto) -> Unit,
    onCancelFalse: (EmergencyReportDto) -> Unit,
) {
    val colors = SafeLightTheme.colors
    val status = statusBadge(report.reportStatus)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "#ER-${report.reportId}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textStrong,
            )
            AdminBadge(status)
            Text(
                report.nickname.ifBlank { "-" },
                Modifier.weight(1f),
                fontSize = 12.sp,
                color = colors.textMuted,
            )
            Text(fmtShort(report.reportedAt), fontSize = 11.sp, color = colors.textMuted)
        }
        if (!report.description.isNullOrBlank()) {
            Text(
                report.description,
                Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = colors.textStrong,
            )
        }
        if (report.latitude != null && report.longitude != null) {
            LocationText(
                latitude = report.latitude,
                longitude = report.longitude,
                modifier = Modifier.padding(top = 6.dp),
                addressSize = 12.0,
            )
        }
        report.nearestCctv?.let {
            Text(
                "인근 CCTV: ${it.cctvName.ifBlank { "#${it.cctvId}" }}",
                Modifier.padding(top = 4.dp),
                fontSize = 11.sp,
                color = colors.textMuted,
            )
        }
        // 허위 확정 건은 상태 둘이 잠긴다 — 서버가 PATCH /status 를 400 으로 막는다.
        // 셋째 버튼이 '취소'로 뒤집혀 되돌릴 길이 늘 남는다.
        Row(
            Modifier.padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (report.reportStatus != "RECEIVED" && !report.isFalseReport) {
                SmallChipButton("접수됨", colors.warning) { vm.setReportStatus(report, "RECEIVED") }
            }
            if (report.reportStatus != "RESOLVED" && !report.isFalseReport) {
                SmallChipButton("해결완료", colors.safe) { vm.setReportStatus(report, "RESOLVED") }
            }
            if (report.isFalseReport) {
                SmallChipButton("허위신고 취소", colors.bluePrimary) { onCancelFalse(report) }
            } else {
                SmallChipButton("허위신고 처리", colors.danger) { onMarkFalse(report) }
            }
        }
    }
}

@Composable
private fun SmallChipButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = .12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}
