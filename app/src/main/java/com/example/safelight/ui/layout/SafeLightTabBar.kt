package com.example.safelight.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.ui.nav.UserTab
import com.example.safelight.ui.theme.SafeLightTheme

/** 탭바 높이. 웹 MobileTabBar 의 `TAB_BAR_HEIGHT = 56`. */
val TAB_BAR_HEIGHT = 56.dp

/**
 * 사용자 하단 탭바. 웹 MobileTabBar 를 옮긴 것이고 높이도 웹과 같은 56 이다.
 *
 * Material3 NavigationBar 를 쓰지 않는다. 그쪽은 높이가 80dp 로 고정이라 웹보다 24dp 두꺼워지고,
 * 지도가 그만큼 짧아지면서 SOS 버튼과 줌 컨트롤이 다 같이 위로 밀린다. 선택 항목 뒤에 그리는
 * 알약 인디케이터도 웹에는 없다 — 웹은 아이콘과 글자 색만 파랗게 바뀐다.
 * (관리자 콘솔의 AdminTabBar 도 같은 이유로 직접 그린다.)
 *
 * 위쪽 경계선이 있어야 한다. 탭바와 바텀시트는 둘 다 surface(흰색)라 선이 없으면 두 면이
 * 한 덩어리로 보여서, 시트를 어디까지 내렸는지 읽을 수가 없다.
 */
@Composable
fun SafeLightTabBar(current: String?, onSelect: (UserTab) -> Unit) {
    val colors = SafeLightTheme.colors
    // 여백은 탭 줄 바깥에 둔다. 고정 높이 56 안쪽에 넣으면 그만큼 줄이 눌려 글자가 잘린다.
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
                .height(TAB_BAR_HEIGHT),
        ) {
            UserTab.entries.forEach { tab ->
                // 게시글 상세·글쓰기처럼 탭 안에서 더 들어간 화면에서도 그 탭에 불이 켜져 있어야 한다
                // (웹도 UserShell 에 active="community" 를 그대로 넘긴다).
                val on = current == tab.route || current?.startsWith("${tab.route}/") == true
                val tint = if (on) colors.bluePrimary else colors.textMuted
                // 물결(ripple)을 끈다 — 웹에는 없고, 56 안에서는 원이 글자까지 덮어 번져 보인다.
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(tab) },
                        ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(22.dp))
                    Text(
                        tab.label,
                        fontSize = 10.5.sp,
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
