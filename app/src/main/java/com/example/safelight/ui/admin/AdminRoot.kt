package com.example.safelight.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.ui.nav.AdminTab
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 관리자 콘솔. 지금은 **틀만** 있다 — 헤더·탭 이동·본문 자리까지 잡아 두고,
 * 다섯 화면의 내용은 웹(AdminDashboardPage · AdminReportPage · AdminUserPage ·
 * AdminDangerZonePage · AdminNoticePage)에서 차례로 옮긴다.
 *
 * 탭 상태를 NavHost 가 아니라 여기서 들고 있는 이유: 관리자 콘솔은 사용자 화면과 백스택을
 * 나눠 쓰지 않는다. 뒤로가기는 탭 사이를 오가는 게 아니라 콘솔을 통째로 닫는 것이어야 한다.
 */
@Composable
fun AdminRoot(onExit: () -> Unit, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(AdminTab.Dashboard) }

    AdminShell(
        tab = tab,
        onSelectTab = { tab = it },
        onExit = onExit,
        onLogout = onLogout,
        // 미처리 신고 수는 신고 관리 화면을 옮길 때 함께 붙인다.
        reportCount = 0,
    ) {
        AdminSectionPlaceholder(tab)
    }
}

/** 아직 옮기지 않은 관리자 화면 자리. 어떤 탭인지와 무엇이 들어올지를 밝혀 둔다. */
@Composable
private fun AdminSectionPlaceholder(tab: AdminTab) {
    val colors = SafeLightTheme.colors
    val note = when (tab) {
        AdminTab.Dashboard -> "신고·사용자·위험구역 요약 카드와 최근 신고 목록이 들어옵니다."
        AdminTab.Reports -> "접수된 긴급 신고 목록과 상태 변경·허위 처리가 들어옵니다."
        AdminTab.Users -> "회원 목록과 블랙리스트·권한 관리가 들어옵니다."
        AdminTab.DangerZones -> "위험 구역 지도와 등급 변경이 들어옵니다."
        AdminTab.Notices -> "공지 작성·수정·삭제가 들어옵니다."
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.blueTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tab.icon, null, tint = colors.bluePrimary, modifier = Modifier.size(26.dp))
        }
        Text(
            tab.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textStrong,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            note,
            Modifier.fillMaxWidth().padding(top = 6.dp),
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            "아직 옮기지 않은 화면입니다.",
            fontSize = 12.sp,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
