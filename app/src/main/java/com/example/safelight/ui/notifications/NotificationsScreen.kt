package com.example.safelight.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.SessionUser
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.map.KakaoMapHost
import com.example.safelight.ui.theme.SafeLightTheme
import com.kakao.vectormap.LatLng
import java.util.Locale
import com.example.safelight.ui.common.EmptyText

/** 목록의 시각 — 오늘이면 HH:mm, 아니면 MM-DD. */
private fun shortStamp(iso: String): String {
    if (iso.length < 10) return ""
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
    return if (iso.take(10) == today) iso.substring(11, 16) else iso.substring(5, 10)
}

/** 상세의 시각 — `2026-08-16 21:04`. */
private fun fullStamp(iso: String): String =
    if (iso.length < 16) "-" else iso.take(16).replace('T', ' ')

/**
 * 알림함. 웹 NotificationsPage 의 모바일 배치를 옮겼다 — 목록과 상세를 오간다.
 *
 * 두 종류를 색으로 나눈다: 긴급은 빨강(사이렌), 쪽지는 파랑(봉투).
 */
@Composable
fun NotificationsScreen(
    user: SessionUser?,
    onBack: () -> Unit,
    onWriteMessage: (Long) -> Unit,
    onOpenFriends: () -> Unit,
    onUnreadChanged: () -> Unit,
    vm: NotificationsViewModel = viewModel(),
) {
    val colors = SafeLightTheme.colors
    LaunchedEffect(Unit) { vm.start() }
    // 방금 읽은 것이 헤더 벨의 점에서 바로 빠지게 한다.
    LaunchedEffect(vm.readRevision) {
        if (vm.readRevision > 0) onUnreadChanged()
    }

    val selected = vm.selected

    // 바깥 Scaffold 가 이미 상태바·탭바만큼 비켜 놨다(여기서 또 빼면 여백이 두 번 들어간다).
    Scaffold(
        containerColor = colors.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header(
                subtitle = when {
                    vm.loading -> "불러오는 중..."
                    vm.unreadCount > 0 -> "안 읽음 ${vm.unreadCount}건"
                    else -> "모두 읽었습니다"
                },
                showMarkAll = selected == null && vm.unreadCount > 0,
                onBack = { if (selected != null) vm.close() else onBack() },
                onMarkAll = vm::markAllRead,
            )

            if (selected == null) {
                if (vm.items.isEmpty()) {
                    EmptyText(if (vm.loading) "불러오는 중..." else "받은 알림이 없습니다.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(vm.items, key = { it.key }) { AlertRow(it) { vm.open(it) } }
                    }
                }
            } else {
                Detail(
                    vm = vm,
                    item = selected,
                    user = user,
                    onWriteMessage = onWriteMessage,
                    onOpenFriends = onOpenFriends,
                )
            }
        }
    }
}

@Composable
private fun Header(subtitle: String, showMarkAll: Boolean, onBack: () -> Unit, onMarkAll: () -> Unit) {
    val colors = SafeLightTheme.colors
    // 배경을 깔지 않는다. 웹 모바일은 이 머리말을 페이지 배경(bg) 위에 두고 아래 선만 긋는다.
    // surface 로 칠하면 바로 위의 앱 헤더(역시 흰색)와 이어 붙어, 화면 꼭대기부터 부제까지가
    // 165dp 짜리 흰 판 하나로 읽힌다 — 머리말 자체는 웹과 같은 높이(75dp)인데도 '칸이 크다'고
    // 보이던 게 이것 때문이다. 웹은 앱 헤더만 흰색이고 그 아래는 회색이라 둘이 갈라져 보인다.
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 웹과 같은 34 짜리 테두리 상자에 18 짜리 갈매기표다. 예전에는 테두리 없이 22 짜리
            // 굵은 화살표라, 흰 판 왼쪽에 큰 기호 하나만 덩그러니 놓인 모양이었다.
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SafeIcons.ChevronLeft, "뒤로", tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("알림", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textStrong)
                Text(subtitle, fontSize = 12.sp, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp))
            }
            if (showMarkAll) {
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        // 머리말이 회색 배경 위로 내려왔으니 웹처럼 흰 바탕을 깔아야 버튼으로 읽힌다.
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(9.dp))
                        .clickable(onClick = onMarkAll)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("모두 읽음", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun AlertRow(item: AlertItem, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    // 벨 아이콘과 같은 색 규칙 — 목록에서 파란 점이던 것이 다른 곳에서 빨간 점이 되면 매번 다시 읽어야 한다.
    val kindColor = if (item.emergency) colors.danger else colors.bluePrimary
    val tint = if (item.emergency) colors.danger.copy(alpha = .10f) else colors.blueTint
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (item.isRead) Color.Transparent else kindColor.copy(alpha = .045f))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 1.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (item.isRead) colors.bg else tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.emergency) SafeIcons.Siren else SafeIcons.Message,
                    null,
                    tint = if (item.isRead) colors.textMuted else kindColor,
                    modifier = Modifier.size(17.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        item.title,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 13.5.sp,
                        fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.ExtraBold,
                        color = colors.textStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 여러 통을 한 줄로 묶었으니 몇 통인지는 밝힌다. 안 그러면 나머지가 사라진 것처럼 보인다.
                    if (item.unreadCount > 1) {
                        Text(
                            "${item.unreadCount}",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(kindColor)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    } else if (!item.isRead) {
                        // 개수 배지가 이미 같은 뜻이라 둘이 겹칠 때는 점을 뺀다.
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(kindColor))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(shortStamp(item.createdAt), fontSize = 11.sp, color = colors.textMuted, maxLines = 1)
                }
                Text(
                    item.preview,
                    fontSize = 12.5.sp,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun Detail(
    vm: NotificationsViewModel,
    item: AlertItem,
    user: SessionUser?,
    onWriteMessage: (Long) -> Unit,
    onOpenFriends: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textStrong)
            Text(
                fullStamp(item.createdAt) + messageCountSuffix(item),
                fontSize = 12.sp,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // 쪽지는 줄바꿈을 살린다 — 사람이 쓴 글이라 문단이 뭉개지면 읽기 어렵다.
        Body(
            if (item.emergency) item.notification?.message.orEmpty().ifBlank { "내용이 없습니다." }
            else item.sender?.latest?.content.orEmpty().ifBlank { "내용이 없습니다." }
        )

        if (item.emergency) {
            // 알림은 친구의 SOS 로만 오므로 상대는 항상 친구다. 나 자신에게는 보낼 수 없으니 막아 둔다.
            val reporterId = item.notification?.reporterUserId
            if (reporterId != null && reporterId != user?.userId) {
                PersonAction(
                    name = item.notification.reporterNickname.ifBlank { "신고자" },
                    desc = "안전한지 바로 확인해보세요",
                    button = "쪽지 보내기",
                    onClick = { onWriteMessage(reporterId) },
                )
            }
            SharedLocation(vm = vm, onOpenFriends = onOpenFriends)
        } else {
            val sender = item.sender
            if (sender != null) {
                PersonAction(
                    name = sender.senderNickname.ifBlank { "친구" },
                    desc = if (sender.total > 1) "앞선 쪽지는 쪽지함에서 볼 수 있습니다"
                    else "대화 전체는 쪽지함에서 볼 수 있습니다",
                    button = "답장하기",
                    onClick = { onWriteMessage(sender.senderId) },
                )
            }
        }
    }
}

/** 안 읽음 수가 아니라 전체 통수로 적는다 — 열자마자 읽음이 되므로 안 읽음 기준이면 문구가 사라진다. */
private fun messageCountSuffix(item: AlertItem): String {
    val total = item.sender?.total ?: 0
    return if (total > 1) " · 받은 쪽지 ${total}통 중 마지막" else ""
}

@Composable
private fun Body(text: String) {
    val colors = SafeLightTheme.colors
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        fontSize = 13.5.sp,
        lineHeight = 23.sp,
        color = colors.textStrong,
    )
}

@Composable
private fun PersonAction(name: String, desc: String, button: String, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(desc, fontSize = 11.5.sp, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Row(
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.bluePrimary)
                .clickable(onClick = onClick)
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(SafeIcons.Message, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(button, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun SharedLocation(vm: NotificationsViewModel, onOpenFriends: () -> Unit) {
    val colors = SafeLightTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("신고 위치", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.textStrong)

        if (vm.sharedLoading) {
            Text("위치를 불러오는 중...", fontSize = 13.sp, color = colors.textMuted)
            return@Column
        }

        val error = vm.sharedError
        if (error.isNotBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.warning.copy(alpha = .08f))
                    .border(1.dp, colors.warning.copy(alpha = .28f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(error, fontSize = 12.5.sp, lineHeight = 20.sp, color = colors.textStrong)
                Text(
                    "정확한 위치는 신고자가 위치 공유를 허용한 친구에게만 보입니다.",
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // 친구 관리의 '상대 공유'에서 누가 허용했는지 바로 확인할 수 있다.
                Text(
                    "친구 관리에서 확인",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.bluePrimary,
                    modifier = Modifier.padding(top = 4.dp).clickable(onClick = onOpenFriends),
                )
            }
            return@Column
        }

        val shared = vm.shared ?: return@Column
        MiniMap(shared.latitude, shared.longitude, shared.reporterNickname)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                shared.reporterNickname.ifBlank { "-" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textStrong,
            )
            Badge(
                when (shared.reportStatus) {
                    "RESOLVED" -> "해결"
                    "FALSE" -> "오탐"
                    else -> "접수"
                },
                colors.bluePrimary,
                colors.blueTint,
            )
            when (shared.dangerLevel) {
                "HIGH" -> Badge("위험", colors.danger, colors.danger.copy(alpha = .10f))
                "MEDIUM" -> Badge("주의", colors.warning, colors.warning.copy(alpha = .13f))
                "LOW" -> Badge("관심", colors.safe, colors.safe.copy(alpha = .13f))
            }
            Spacer(Modifier.weight(1f))
            Text(fullStamp(shared.reportedAt), fontSize = 11.5.sp, color = colors.textMuted, maxLines = 1)
        }

        Text(
            String.format(Locale.US, "%.5f, %.5f", shared.latitude, shared.longitude),
            fontSize = 12.sp,
            color = colors.textMuted,
        )
        shared.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 13.sp, color = colors.textStrong)
        }
    }
}

/**
 * 신고 위치 미니 지도. 웹 MiniMap 과 같이 레벨 4 로 한 점만 보여준다
 * (안드로이드 확대는 방향이 반대라 `20 - 4 = 16` 이다 — README '웹과 다른 점' 참고).
 */
@Composable
private fun MiniMap(lat: Double, lng: Double, name: String) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
    ) {
        KakaoMapHost(
            modifier = Modifier.fillMaxSize(),
            initialPosition = LatLng.from(lat, lng),
            initialZoom = 16,
        ) { _, layers ->
            layers.drawSearchPin(LatLng.from(lat, lng), name.ifBlank { "신고 위치" })
        }
    }
}

@Composable
private fun Badge(text: String, color: Color, background: Color) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(7.dp)).background(background).padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
    )
}

