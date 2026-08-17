package com.example.safelight.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.ui.nav.AdminTab

/**
 * 관리자 콘솔. 웹의 다섯 화면(AdminDashboardPage · AdminReportPage · AdminUserPage ·
 * AdminDangerZonePage · AdminNoticePage)을 탭 하나씩에 담는다.
 *
 * 탭 상태를 NavHost 가 아니라 여기서 들고 있는 이유: 관리자 콘솔은 사용자 화면과 백스택을
 * 나눠 쓰지 않는다. 뒤로가기는 탭 사이를 오가는 게 아니라 콘솔을 통째로 닫는 것이어야 한다.
 */
@Composable
fun AdminRoot(selfUserId: Long?, onExit: () -> Unit, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(AdminTab.Dashboard) }
    // '신고' 배지 = 활성 위험구역들의 신고수 합계. 웹 AdminShell 과 같은 셈법이다
    // (서버가 허위 건을 빼고 다시 세므로, 허위로 처리하면 이 숫자가 줄어든다).
    var reportCount by remember { mutableIntStateOf(0) }
    var badgeRevision by remember { mutableIntStateOf(0) }

    val api = remember { Network.backend(SafeLightApi::class.java) }
    LaunchedEffect(badgeRevision) {
        val envelope = runCatching { api.getDangerZones() }.getOrNull()
        // 실패하면 배지를 건드리지 않는다 — 네트워크 오류로 '처리할 신고 없음'처럼 보이면 안 된다.
        if (envelope?.success == true) {
            reportCount = envelope.data.orEmpty().sumOf { it.reportCount }
        }
    }

    AdminShell(
        tab = tab,
        onSelectTab = { tab = it },
        onExit = onExit,
        onLogout = onLogout,
        reportCount = reportCount,
    ) {
        when (tab) {
            AdminTab.Dashboard -> AdminDashboardScreen(onOpenTab = { tab = it })
            AdminTab.Reports -> AdminReportScreen(onReportsChanged = { badgeRevision++ })
            AdminTab.Users -> AdminUserScreen(selfUserId = selfUserId)
            AdminTab.DangerZones -> AdminZoneScreen(onReportsChanged = { badgeRevision++ })
            AdminTab.Notices -> AdminNoticeScreen()
        }
    }
}
