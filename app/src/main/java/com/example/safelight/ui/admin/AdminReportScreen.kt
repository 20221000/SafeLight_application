package com.example.safelight.ui.admin

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.net.EmergencyReportDto
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.map.KakaoMapHost
import com.example.safelight.ui.theme.SafeLightTheme
import com.kakao.vectormap.LatLng
import java.util.Calendar

/**
 * 신고 관리. 웹 AdminReportPage 의 모바일 배치를 옮겼다 —
 * 데스크탑 표 대신 카드 목록, 상태 필터는 건수를 단 칩, 처리는 아래에서 올라오는 시트.
 */
@Composable
fun AdminReportScreen(
    onReportsChanged: () -> Unit = {},
    vm: AdminReportViewModel = viewModel(),
) {
    val colors = SafeLightTheme.colors
    val snackbar = remember { SnackbarHostState() }

    var sheetReport by remember { mutableStateOf<EmergencyReportDto?>(null) }
    var confirmFalse by remember { mutableStateOf<EmergencyReportDto?>(null) }
    var confirmUnfalse by remember { mutableStateOf<EmergencyReportDto?>(null) }
    var locationTarget by remember { mutableStateOf<EmergencyReportDto?>(null) }

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.messageShown()
    }
    // 방금 처리한 건이 탭바 배지에서도 바로 빠지게 한다.
    LaunchedEffect(vm.reportsRevision) {
        if (vm.reportsRevision > 0) onReportsChanged()
    }

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        // 관리자 셸이 이미 상태바·탭바만큼 비켜 놨다.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vm.error?.let { item { AdminErrorBox("데이터를 불러오지 못했습니다: $it") } }

            if (vm.invalidRange) {
                item { AdminErrorBox("시작일이 종료일보다 늦습니다. 기간을 다시 골라주세요.") }
            }

            item {
                AdminSearchField(
                    value = vm.search,
                    onValueChange = vm::onSearchChange,
                    placeholder = "신고자 · 신고 내용 검색",
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DateBox("시작일", vm.startDate, Modifier.weight(1f), vm::pickStartDate)
                    Text("~", fontSize = 13.sp, color = colors.textMuted)
                    DateBox("종료일", vm.endDate, Modifier.weight(1f), vm::pickEndDate)
                    if (vm.hasFilter) {
                        AdminActionButton("초기화", onClick = vm::resetFilters)
                    }
                }
            }

            // 칩이 안 들어가면 다음 줄로 내린다. 가로 스크롤은 쓰지 않는다 —
            // 스크롤바가 없으면 화면 밖 칩을 발견할 방법이 없다.
            item {
                val counts = mapOf(
                    ReportFilter.All to vm.stats.total,
                    ReportFilter.Received to vm.stats.received,
                    ReportFilter.Resolved to vm.stats.resolved,
                    ReportFilter.False to vm.stats.falseCount,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ReportFilter.entries.forEach { entry ->
                        AdminChip(
                            label = entry.label,
                            count = counts[entry],
                            selected = vm.filter == entry,
                            onClick = { vm.selectFilter(entry) },
                        )
                    }
                }
            }

            if (vm.reports.isEmpty()) {
                item {
                    AdminEmptyCard(
                        when {
                            vm.loading -> "불러오는 중…"
                            vm.hasFilter -> "조건에 맞는 신고가 없습니다."
                            else -> "신고 데이터가 없습니다."
                        }
                    )
                }
            } else {
                items(vm.reports, key = { it.reportId }) { report ->
                    ReportCard(
                        report = report,
                        onResolve = { vm.setStatus(report, "RESOLVED") },
                        onMore = { sheetReport = report },
                        onLocation = { locationTarget = report },
                    )
                }
            }

            // 카드 목록이라 페이지 번호보다 이어 붙이는 쪽이 맞다.
            if (!vm.isLast) {
                item {
                    AdminActionButton(
                        label = if (vm.loading) "불러오는 중…"
                        else "더 보기 (${vm.reports.size} / ${vm.totalElements})",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = vm::loadMore,
                    )
                }
            }

            item {
                Text(
                    if (vm.searchTerm.isNotBlank())
                        "'${vm.searchTerm}' 검색 결과 ${vm.reports.size} / ${vm.totalElements}건"
                    else "${vm.reports.size} / ${vm.totalElements}건",
                    Modifier.fillMaxWidth(),
                    fontSize = 12.sp,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    sheetReport?.let { report ->
        AdminActionSheet(
            title = "신고 #ER-${report.reportId}",
            subtitle = "${report.nickname.ifBlank { "-" }} · ${fmtDateTime(report.reportedAt)}",
            actions = reportActions(
                report = report,
                onSetStatus = { vm.setStatus(report, it) },
                onMarkFalse = { confirmFalse = report },
                onCancelFalse = { confirmUnfalse = report },
            ),
            onClose = { sheetReport = null },
        )
    }

    // 허위신고는 신고자에게 벌점이 붙고 위험구역 집계까지 다시 도는 무거운 처리라
    // 되돌릴 수 있는 지금도 누르기 전에 한 번 묻는다.
    confirmFalse?.let { report ->
        ConfirmSheet(
            title = "신고 #ER-${report.reportId} 을(를) 허위신고로 확정합니다",
            message = "신고자의 허위신고 횟수가 1 올라가고, 누적 3회가 되면 자동으로 블랙리스트에 " +
                "들어갑니다. 이 신고로 잡혀 있던 위험구역도 다시 계산됩니다. " +
                "잘못 눌렀다면 같은 메뉴에서 취소할 수 있습니다.",
            confirmLabel = "허위신고로 확정",
            danger = true,
            onConfirm = { vm.markFalse(report); confirmFalse = null },
            onDismiss = { confirmFalse = null },
        )
    }

    confirmUnfalse?.let { report ->
        ConfirmSheet(
            title = "신고 #ER-${report.reportId} 의 허위신고를 취소합니다",
            message = "상태가 접수됨으로 돌아가고, 신고자의 허위신고 횟수가 1 내려갑니다. " +
                "그 결과 3회 미만이 되면 블랙리스트도 함께 풀립니다. " +
                "위험구역은 신고 후 24시간이 지났으면 다시 살아나지 않습니다.",
            confirmLabel = "허위신고 취소",
            danger = false,
            onConfirm = { vm.cancelFalse(report); confirmUnfalse = null },
            onDismiss = { confirmUnfalse = null },
        )
    }

    locationTarget?.let { report ->
        LocationDialog(report) { locationTarget = null }
    }
}

/**
 * 시트 항목 셋. 허위신고로 확정된 건은 상태 둘이 잠긴다 — 서버가 `PATCH /status` 를 400 으로 막는다.
 * 잠긴 이유 대신 무엇을 먼저 하면 되는지를 적고, 셋째 줄은 상태에 따라 '처리' ↔ '취소'로 뒤집는다
 * (회색으로 죽은 줄을 남기면 되돌릴 길이 없는 것처럼 읽힌다).
 */
private fun reportActions(
    report: EmergencyReportDto,
    onSetStatus: (String) -> Unit,
    onMarkFalse: () -> Unit,
    onCancelFalse: () -> Unit,
): List<SheetAction> {
    val lockedNote = "허위신고를 먼저 취소하세요".takeIf { report.isFalseReport }
    return listOf(
        SheetAction(
            label = "접수됨으로 되돌리기",
            tone = ActionTone.Primary,
            enabled = report.reportStatus != "RECEIVED" && !report.isFalseReport,
            caption = lockedNote,
            onClick = { onSetStatus("RECEIVED") },
        ),
        SheetAction(
            label = "해결완료로 변경",
            tone = ActionTone.Safe,
            enabled = report.reportStatus != "RESOLVED" && !report.isFalseReport,
            caption = lockedNote,
            onClick = { onSetStatus("RESOLVED") },
        ),
        if (report.isFalseReport) SheetAction(
            label = "허위신고 취소",
            tone = ActionTone.Primary,
            caption = "접수됨으로 되돌림 · 신고자 허위신고 -1 · 블랙리스트 해제",
            onClick = onCancelFalse,
        ) else SheetAction(
            label = "허위신고(오탐)로 처리",
            tone = ActionTone.Danger,
            caption = "신고자 허위신고 +1 · 누적 3회면 블랙리스트",
            onClick = onMarkFalse,
        ),
    )
}

@Composable
private fun ReportCard(
    report: EmergencyReportDto,
    onResolve: () -> Unit,
    onMore: () -> Unit,
    onLocation: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    val status = statusBadge(report.reportStatus)
    val level = report.dangerLevel?.let { levelBadge(it, colors) }

    AdminCard(accent = status.color) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "#ER-${report.reportId}",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textStrong,
                )
                AdminBadge(status)
                Box(Modifier.weight(1f))
                Text(fmtShort(report.reportedAt), fontSize = 11.5.sp, color = colors.textMuted)
            }

            Box(Modifier.fillMaxWidth().padding(top = 11.dp).height(1.dp).background(colors.border))

            Column(
                Modifier.padding(top = 11.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                DetailRow("신고자") {
                    Text(
                        report.nickname.ifBlank { "-" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textStrong,
                    )
                }
                DetailRow("발생위치") {
                    if (report.latitude != null && report.longitude != null) {
                        // 좌표 두 개로는 어디인지 알 수 없어 주소를 앞세운다.
                        // 좌표는 지우지 않는다 — 다른 지도에 찍어 보거나 신고끼리 대조할 때 쓴다.
                        Row(
                            Modifier.clickable(onClick = onLocation),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            LocationText(
                                latitude = report.latitude,
                                longitude = report.longitude,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                SafeIcons.MapPin,
                                "지도로 보기",
                                tint = colors.bluePrimary,
                                modifier = Modifier.padding(top = 2.dp).size(14.dp),
                            )
                        }
                    } else {
                        Text("-", fontSize = 13.sp, color = colors.textStrong)
                    }
                }
                if (report.dangerZoneId != null) {
                    DetailRow("연결 구역") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                "#${report.dangerZoneId}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.bluePrimary,
                            )
                            level?.let { AdminBadge(it) }
                        }
                    }
                }
                if (!report.description.isNullOrBlank()) {
                    DetailRow("내용") {
                        Text(
                            report.description,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = colors.textStrong,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (report.reportStatus == "RECEIVED" && !report.isFalseReport) {
                    AdminActionButton("해결 처리", tone = ActionTone.Safe, filled = true, onClick = onResolve)
                }
                Box(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(11.dp))
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⋮", fontSize = 20.sp, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    val colors = SafeLightTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            Modifier.width(66.dp),
            fontSize = 12.5.sp,
            color = colors.textMuted,
        )
        value()
    }
}

/** 날짜 한 칸. 안드로이드 기본 달력을 띄운다 — 웹의 `<input type=date>` 자리다. */
@Composable
private fun DateBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    val colors = SafeLightTheme.colors
    val context = LocalContext.current
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(11.dp))
            .clickable {
                val calendar = Calendar.getInstance()
                // 이미 고른 날이 있으면 그 날에서 시작한다.
                value.split("-").mapNotNull(String::toIntOrNull).takeIf { it.size == 3 }?.let {
                    calendar.set(it[0], it[1] - 1, it[2])
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onPick("%04d-%02d-%02d".format(year, month + 1, day)) },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH),
                ).show()
            }
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.ifBlank { label },
            fontSize = 13.sp,
            color = if (value.isBlank()) colors.textMuted else colors.textStrong,
            maxLines = 1,
        )
    }
}

/** 무게가 있는 결정을 묻는 창. 웹 ConfirmDialog 와 같은 문구를 쓴다. */
@Composable
private fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
        text = { Text(message, fontSize = 13.5.sp, lineHeight = 21.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) colors.danger else colors.bluePrimary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        containerColor = colors.surface,
        titleContentColor = colors.textStrong,
        textContentColor = colors.textMuted,
    )
}

@Composable
private fun LocationDialog(report: EmergencyReportDto, onClose: () -> Unit) {
    val colors = SafeLightTheme.colors
    val lat = report.latitude ?: return
    val lng = report.longitude ?: return
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("신고 #ER-${report.reportId} 위치", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "${report.nickname.ifBlank { "-" }} · ${fmtDateTime(report.reportedAt)}",
                    fontSize = 12.5.sp,
                    color = colors.textMuted,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
                ) {
                    KakaoMapHost(
                        modifier = Modifier.fillMaxSize(),
                        initialPosition = LatLng.from(lat, lng),
                        initialZoom = 16,
                    ) { _, layers ->
                        layers.drawSearchPin(LatLng.from(lat, lng), "신고 #ER-${report.reportId}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
        containerColor = colors.surface,
        titleContentColor = colors.textStrong,
        textContentColor = colors.textMuted,
    )
}
