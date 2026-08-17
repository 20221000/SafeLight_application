package com.example.safelight.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.net.EmergencyReportDto
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.nav.AdminTab
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 관리자 대시보드. 웹 AdminDashboardPage 의 모바일 배치를 그대로 옮겼다 —
 * KPI 2열 · 상태 분포 · 사용자/구역 요약 줄 · 최근 긴급신고.
 */
@Composable
fun AdminDashboardScreen(
    onOpenTab: (AdminTab) -> Unit,
    vm: AdminDashboardViewModel = viewModel(),
) {
    val colors = SafeLightTheme.colors
    LaunchedEffect(Unit) { vm.start() }

    val dash = if (vm.loading) "-" else null

    LazyColumn(
        Modifier.fillMaxSize().background(colors.bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vm.error?.let { item { AdminErrorBox("데이터를 불러오지 못했습니다: $it") } }

        // KPI 2열. 웹의 grid 를 두 줄짜리 Row 로 편다.
        item {
            val cells = listOf(
                Triple("총 신고", vm.stats.total, colors.bluePrimary),
                Triple("접수", vm.stats.received, colors.warning),
                Triple("해결", vm.stats.resolved, colors.safe),
                Triple("오탐", vm.stats.falseCount, colors.danger),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cells.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (label, value, dot) ->
                            KpiCard(
                                label = label,
                                value = dash ?: "$value",
                                dotColor = dot,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        item { StatusDistribution(vm.stats, loading = vm.loading) }

        // 사용자·위험구역 요약. 누르면 그 화면으로 넘어간다.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("전체 사용자", dash ?: "${vm.totalUsers}명") { onOpenTab(AdminTab.Users) }
                MiniStat("활성 위험구역", dash ?: "${vm.zones.size}개") { onOpenTab(AdminTab.DangerZones) }
                MiniStat("블랙리스트", dash ?: "${vm.blacklistCount}명") { onOpenTab(AdminTab.Users) }
                MiniStat("전체 게시글", dash ?: "${vm.totalPosts}개") { onOpenTab(AdminTab.Notices) }
                MiniStat("오늘 작성글", dash ?: "${vm.todayPosts}개") { onOpenTab(AdminTab.Notices) }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("최근 긴급신고", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textStrong)
                Box(Modifier.weight(1f))
                Text(
                    "신고 관리 →",
                    Modifier.clickable { onOpenTab(AdminTab.Reports) },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.bluePrimary,
                )
            }
        }

        if (vm.recentReports.isEmpty()) {
            item { AdminEmptyCard(if (vm.loading) "불러오는 중…" else "최근 신고가 없습니다.") }
        } else {
            items(vm.recentReports, key = { it.reportId }) { report ->
                RecentReportRow(report) { onOpenTab(AdminTab.Reports) }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, dotColor: Color, modifier: Modifier = Modifier) {
    val colors = SafeLightTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 15.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                Modifier.weight(1f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textMuted,
                maxLines = 1,
            )
            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        }
        Text(
            value,
            Modifier.padding(top = 6.dp),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textStrong,
        )
    }
}

/** 접수·해결·오탐이 전체에서 차지하는 몫. 숫자 셋만으로는 비율이 읽히지 않는다. */
@Composable
private fun StatusDistribution(stats: ReportStats, loading: Boolean) {
    val colors = SafeLightTheme.colors
    val parts = listOf(
        Triple("접수", stats.received, colors.warning),
        Triple("해결", stats.resolved, colors.safe),
        Triple("오탐", stats.falseCount, colors.danger),
    )
    val sum = parts.sumOf { it.second }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Text("상태 분포", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textStrong)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(9.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.bg),
        ) {
            if (sum > 0) {
                parts.forEach { (_, value, color) ->
                    if (value > 0) {
                        Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color))
                    }
                }
            }
        }
        parts.forEach { (label, value, color) ->
            Row(
                Modifier.fillMaxWidth().padding(top = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(label, Modifier.weight(1f), fontSize = 13.sp, color = colors.textMuted)
                Text(
                    if (loading) "-" else "$value",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textStrong,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textMuted,
        )
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = colors.textStrong)
        Icon(
            SafeIcons.ChevronRight,
            null,
            tint = colors.textMuted,
            modifier = Modifier.padding(start = 6.dp).size(16.dp),
        )
    }
}

@Composable
private fun RecentReportRow(report: EmergencyReportDto, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    val status = statusBadge(report.reportStatus)
    AdminCard(accent = status.color, modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                "#ER-${report.reportId}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textStrong,
            )
            AdminBadge(status)
            Text(
                report.nickname.ifBlank { "-" },
                Modifier.weight(1f),
                fontSize = 12.5.sp,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(fmtShort(report.reportedAt), fontSize = 11.sp, color = colors.textMuted)
        }
    }
}
