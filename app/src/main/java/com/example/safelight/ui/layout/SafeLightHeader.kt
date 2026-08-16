package com.example.safelight.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.search.SearchedPlace
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 웹 MobileShell 의 헤더를 옮긴 것이다 — 로고 + 장소 검색 + 알림 벨 + 야간 모드.
 *
 * 알림 벨은 웹과 같이 로그인한 사용자에게만 보인다.
 * 관리자 콘솔·로그인 버튼은 웹에서도 좁은 헤더에 넣지 않고 '내 정보'로 옮겼다.
 */

/** 로고·검색창·야간모드를 같은 높이로 맞춰 헤더 리듬을 통일한다(웹 MobileShell 의 CTRL). */
private val CTRL = 38.dp
private val CORNER = RoundedCornerShape(11.dp)

@Composable
fun SafeLightHeader(
    query: String,
    results: List<SearchedPlace>,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPickPlace: (SearchedPlace) -> Unit,
    night: Boolean,
    onToggleNight: () -> Unit,
    loggedIn: Boolean,
    unreadEmergency: Int,
    unreadMessage: Int,
    onOpenNotifications: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    // 검색 결과 목록이 본문(지도) 위로 떠야 한다.
    Box(Modifier.zIndex(20f)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Logo()
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSubmit = onSubmit,
                    modifier = Modifier.weight(1f),
                )
                if (loggedIn) {
                    NotificationBell(
                        emergency = unreadEmergency,
                        message = unreadMessage,
                        onClick = onOpenNotifications,
                    )
                }
                NightModeButton(night = night, onToggle = onToggleNight)
            }

            if (results.isNotEmpty()) {
                // 웹은 입력칸 아래 6px 띄워 겹쳐 띄운다. 여기서는 헤더가 본문 위에 있으므로
                // 같은 간격을 두고 헤더 안에서 아래로 늘린다.
                Box(Modifier.height(6.dp))
                SearchResults(results = results, onPick = onPickPlace)
            }
        }
        // 헤더 아래 경계선. 결과 목록이 떠 있을 때는 목록이 헤더의 일부처럼 보여야 하므로 맨 아래에 그린다.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border),
        )
    }
}

@Composable
private fun Logo() {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .size(CTRL)
            .clip(CORNER)
            .background(colors.bluePrimary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SafeIcons.Shield,
            contentDescription = "Safe Light",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 알림 벨. 누르면 알림함으로 간다.
 *
 * 개수 뱃지가 아니라 점을 쓰는 이유는, 긴급과 쪽지가 섞이면 숫자 하나로는
 * "지금 급한 일인지"를 알 수 없기 때문이다. 급한 건 색으로 먼저 보여야 한다.
 *   긴급만 → 벨이 빨개지고 빨간 점 · 쪽지만 → 벨은 그대로, 파란 점만 · 둘 다 → 빨간 점 아래 파란 점
 */
@Composable
private fun NotificationBell(emergency: Int, message: Int, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    val urgent = emergency > 0 // 벨 색을 바꾸는 건 긴급뿐이다
    val hasAny = urgent || message > 0
    val label = if (!hasAny) "알림" else listOfNotNull(
        "긴급 알림 ${emergency}건".takeIf { urgent },
        "쪽지 ${message}건".takeIf { message > 0 },
    ).joinToString(" · ")

    Box(Modifier.size(CTRL)) {
        Box(
            Modifier
                .size(CTRL)
                .clip(CORNER)
                .background(colors.surface)
                .border(1.dp, if (urgent) colors.danger else colors.border, CORNER)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SafeIcons.Bell,
                contentDescription = label,
                tint = if (urgent) colors.danger else colors.textMuted,
                modifier = Modifier.size(17.dp),
            )
        }
        if (hasAny) {
            Column(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (urgent) Dot(colors.danger)
                if (message > 0) Dot(colors.bluePrimary)
            }
        }
    }
}

/** 점 하나. 어느 배경에서도 뭉개지지 않게 표면색 테두리를 두른다. */
@Composable
private fun Dot(color: Color) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SafeLightTheme.colors
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier
            .height(CTRL)
            .clip(CORNER)
            // 헤더가 흰색이라 배경은 bg 를 써야 입력 필드로 읽힌다(흰색이면 옆의 흰 버튼과 한 덩어리로 보인다).
            .background(colors.bg)
            .border(1.dp, colors.border, CORNER)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            SafeIcons.Search,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(17.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("장소 검색", fontSize = 13.5.sp, color = colors.textMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.5.sp, color = colors.textStrong),
                cursorBrush = SolidColor(colors.bluePrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSubmit()
                    focusManager.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchResults(results: List<SearchedPlace>, onPick: (SearchedPlace) -> Unit) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CORNER)
            .background(colors.surface)
            .border(1.dp, colors.border, CORNER),
    ) {
        results.forEachIndexed { index, place ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(place) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    place.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textStrong,
                )
                if (place.address.isNotBlank()) {
                    Text(place.address, fontSize = 11.5.sp, color = colors.textMuted)
                }
            }
            if (index < results.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
        }
    }
}

@Composable
private fun NightModeButton(night: Boolean, onToggle: () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .size(CTRL)
            .clip(CORNER)
            .background(if (night) colors.bluePrimary else colors.surface)
            .border(1.dp, if (night) colors.bluePrimary else colors.border, CORNER)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SafeIcons.Moon,
            contentDescription = "야간 모드",
            tint = if (night) Color.White else colors.textMuted,
            modifier = Modifier.size(17.dp),
        )
    }
}
