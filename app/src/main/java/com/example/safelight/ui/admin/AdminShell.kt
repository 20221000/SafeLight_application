package com.example.safelight.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.nav.AdminTab
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 관리자 콘솔의 틀. 웹 MobileAdminShell 을 옮긴 것이다 —
 * 컴팩트 헤더 + 스크롤 본문 + 하단 탭바 5개.
 *
 * 사용자 화면의 헤더·탭바를 쓰지 않고 화면을 통째로 덮는다(웹도 셸 자체가 갈린다).
 * 헤더의 '일반 화면으로'·'로그아웃'은 목업에 없지만, 없으면 관리자가 빠져나갈 방법이 사라져 남겨 둔다.
 */
@Composable
fun AdminShell(
    tab: AdminTab,
    onSelectTab: (AdminTab) -> Unit,
    onExit: () -> Unit,
    onLogout: () -> Unit,
    /** 신고 탭의 빨간 배지. 미처리 신고 수가 들어온다. */
    reportCount: Int = 0,
    content: @Composable () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        AdminHeader(
            title = tab.title,
            subtitle = tab.subtitle,
            onExit = onExit,
            onLogout = onLogout,
        )
        Box(Modifier.weight(1f)) { content() }
        AdminTabBar(current = tab, onSelect = onSelectTab, reportCount = reportCount)
    }
}

@Composable
private fun AdminHeader(title: String, subtitle: String, onExit: () -> Unit, onLogout: () -> Unit) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    fontSize = 11.5.sp,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            // 서비스 상태 칩. 웹 모바일 목업(AM1)에서 '정상' 한 마디로 줄인 자리다.
            Row(
                Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.safe.copy(alpha = .10f))
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.safe))
                Text("정상", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.safe)
            }
            HeaderIconButton(SafeIcons.Home, "일반 화면으로", onExit)
            HeaderIconButton(SafeIcons.LogOut, "로그아웃", onLogout)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = colors.textMuted, modifier = Modifier.size(17.dp))
    }
}

/** 데스크탑 웹의 220px 사이드바를 대신하는 하단 탭바. 높이도 웹과 같은 56 이다. */
@Composable
private fun AdminTabBar(current: AdminTab, onSelect: (AdminTab) -> Unit, reportCount: Int) {
    val colors = SafeLightTheme.colors
    // 여백은 탭 줄 **바깥**에 둔다. 고정 높이 56 안쪽에 넣으면 그만큼 줄이 눌려 글자가 잘린다.
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            AdminTab.entries.forEach { entry ->
                val on = entry == current
                val tint = if (on) colors.bluePrimary else colors.textMuted
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(entry) },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Icon(entry.icon, null, tint = tint, modifier = Modifier.size(21.dp))
                        // 신고 탭에만 미처리 건수를 얹는다(웹 사이드바 배지와 같은 숫자다).
                        if (entry == AdminTab.Reports && reportCount > 0) {
                            Text(
                                if (reportCount > 99) "99+" else "$reportCount",
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-6).dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.danger)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                        }
                    }
                    Text(
                        entry.label,
                        fontSize = 10.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
