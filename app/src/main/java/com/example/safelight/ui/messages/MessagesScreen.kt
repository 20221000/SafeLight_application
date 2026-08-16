package com.example.safelight.ui.messages

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme
import java.util.Locale

/** 닉네임에서 아바타 색을 정한다. 웹 MessagesPage 의 PALETTE 와 같은 값·같은 해시다. */
private val PALETTE = listOf(
    Color(0xFF2563EB) to Color(0xFF1E40AF),
    Color(0xFF0EA5E9) to Color(0xFF2563EB),
    Color(0xFF6366F1) to Color(0xFF4338CA),
    Color(0xFF0891B2) to Color(0xFF0E7490),
)

private fun paletteOf(name: String): Pair<Color, Color> {
    var h = 0
    // 웹과 같은 해시를 쓰려면 32비트 부호 없는 값으로 굴려야 한다(JS 의 >>> 0).
    for (ch in name) h = (h * 31 + ch.code)
    val index = (h.toLong() and 0xFFFFFFFFL) % PALETTE.size
    return PALETTE[index.toInt()]
}

/** `2026-08-16T21:04:33` 에서 시각만. */
private fun timeOf(iso: String): String = iso.drop(11).take(5)
private fun dayOf(iso: String): String = iso.take(10)

/**
 * 오늘 날짜(`2026-08-16`). `java.time` 은 API 26 부터라 minSdk 24 에서는 쓸 수 없다.
 * 서버가 주는 문자열이 기기 시간대 기준이므로 여기서도 기기 시간대로 만든다.
 */
private fun today(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())

/**
 * 쪽지함. 웹의 모바일 배치를 그대로 따른다 — 방 목록과 대화방을 오가고,
 * 새 대화는 친구를 고르는 바텀시트로 시작한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    openWith: Long?,
    onBack: () -> Unit,
    onUnreadChanged: () -> Unit,
    vm: MessagesViewModel = viewModel(),
) {
    val colors = SafeLightTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) { vm.start(openWith) }
    // 방금 읽은 것이 헤더 벨의 점에서 바로 빠지게 한다.
    LaunchedEffect(vm.readRevision) {
        if (vm.readRevision > 0) onUnreadChanged()
    }
    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.messageShown()
    }

    val room = vm.room

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        // 바깥 Scaffold 가 이미 상태바·탭바만큼 비켜 놨다(여기서 또 빼면 여백이 두 번 들어간다).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (room == null) {
                Row(
                    Modifier
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(colors.bluePrimary)
                        .clickable { vm.startPicking() }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(SafeIcons.Message, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    Text("새 쪽지", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (room == null) {
                ScreenHeader(title = "쪽지", onBack = onBack)
                if (vm.loading) {
                    EmptyText("불러오는 중...")
                } else if (vm.rooms.isEmpty()) {
                    EmptyText("주고받은 쪽지가 없습니다. '새 쪽지'로 친구와 대화를 시작해보세요.")
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(vm.rooms, key = { it.peerId }) { RoomCard(it) { vm.openRoom(it.peerId) } }
                    }
                }
            } else {
                RoomHeader(
                    name = room.peerName,
                    count = room.messages.size,
                    onBack = vm::closeRoom,
                    onDelete = { confirmDelete = true }.takeIf { room.messages.isNotEmpty() },
                )
                Thread(room = room, modifier = Modifier.weight(1f))
                InputBar(vm = vm, peerName = room.peerName)
            }
        }
    }

    if (vm.picking) {
        ModalBottomSheet(
            onDismissRequest = vm::stopPicking,
            sheetState = sheetState,
            containerColor = colors.surface,
        ) {
            FriendPicker(vm)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("'${room?.peerName}' 님과의 대화를 삭제할까요?") },
            text = { Text("내 쪽에서만 삭제되며 상대에게는 남습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteRoom()
                }) { Text("삭제", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
            containerColor = colors.surface,
            titleContentColor = colors.textStrong,
            textContentColor = colors.textMuted,
        )
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    val colors = SafeLightTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            Modifier.padding(start = 8.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SafeIcons.ArrowLeft, "뒤로", tint = colors.textStrong, modifier = Modifier.size(22.dp))
            }
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = colors.textStrong)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun RoomHeader(name: String, count: Int, onBack: () -> Unit, onDelete: (() -> Unit)?) {
    val colors = SafeLightTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            Modifier.padding(start = 6.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SafeIcons.ArrowLeft, "목록으로", tint = colors.textStrong, modifier = Modifier.size(22.dp))
            }
            Avatar(name, 38)
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { "(알 수 없음)" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("쪽지 ${count}개", fontSize = 11.5.sp, color = colors.textMuted, modifier = Modifier.padding(top = 1.dp))
            }
            if (onDelete != null) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(11.dp))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SafeIcons.Trash, "대화 삭제", tint = colors.danger, modifier = Modifier.size(17.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun RoomCard(room: ChatRoom, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    val last = room.messages.lastOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Avatar(room.peerName, 44)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    room.peerName.ifBlank { "(알 수 없음)" },
                    fontSize = 14.5.sp,
                    fontWeight = if (room.unread > 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = colors.textStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (room.unread > 0) {
                    Text(
                        "${room.unread}",
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.danger)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            // 여러 줄 쪽지는 한 줄로 눌러 보여준다 — 줄바꿈이 그대로 오면 목록이 들쭉날쭉해진다.
            val preview = (if (last?.mine == true) "나: " else "") +
                last?.content.orEmpty().replace(Regex("\\s*\\n+\\s*"), " ")
            Text(
                preview,
                fontSize = 12.5.sp,
                color = if (room.unread > 0) colors.textStrong else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            shortStamp(last?.createdAt.orEmpty()),
            fontSize = 11.sp,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 목록의 시간 — 오늘이면 HH:mm, 아니면 MM-DD. 웹 fmtShort 와 같다. */
private fun shortStamp(iso: String): String {
    if (iso.length < 10) return ""
    return if (dayOf(iso) == today()) timeOf(iso) else iso.substring(5, 10)
}

@Composable
private fun Thread(room: ChatRoom, modifier: Modifier = Modifier) {
    val colors = SafeLightTheme.colors
    val listState = rememberLazyListState()

    // 방을 열거나 새 쪽지가 붙으면 맨 아래로.
    LaunchedEffect(room.peerId, room.messages.size) {
        if (room.messages.isNotEmpty()) listState.scrollToItem(room.messages.lastIndex)
    }

    if (room.messages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyText("${room.peerName} 님과의 첫 쪽지를 보내보세요.")
        }
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(room.messages, key = { _, msg -> msg.messageId }) { index, msg ->
            val prev = room.messages.getOrNull(index - 1)
            Column {
                if (prev == null || dayOf(prev.createdAt) != dayOf(msg.createdAt)) {
                    DayDivider(dayOf(msg.createdAt))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (msg.mine) {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 7.dp)) {
                            if (msg.isRead) {
                                Text("읽음", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.bluePrimary)
                            }
                            Text(timeOf(msg.createdAt), fontSize = 10.5.sp, color = colors.textMuted)
                        }
                    }
                    Text(
                        msg.content,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomEnd = if (msg.mine) 5.dp else 16.dp,
                                    bottomStart = if (msg.mine) 16.dp else 5.dp,
                                )
                            )
                            .background(if (msg.mine) colors.bluePrimary else colors.surface)
                            .then(
                                if (msg.mine) Modifier
                                else Modifier.border(
                                    1.dp,
                                    colors.border,
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomEnd = 16.dp,
                                        bottomStart = 5.dp,
                                    ),
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        lineHeight = 23.sp,
                        color = if (msg.mine) Color.White else colors.textStrong,
                    )
                    if (!msg.mine) {
                        Text(
                            timeOf(msg.createdAt),
                            fontSize = 10.5.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(start = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDivider(day: String) {
    val colors = SafeLightTheme.colors
    val label = when {
        day.length < 10 -> ""
        day == today() -> "오늘"
        else -> "${day.substring(5, 7).trimStart('0')}월 ${day.substring(8, 10).trimStart('0')}일"
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
    }
}

@Composable
private fun InputBar(vm: MessagesViewModel, peerName: String) {
    val colors = SafeLightTheme.colors
    val canSend = vm.draft.isNotBlank() && !vm.sending
    Column(Modifier.fillMaxWidth().background(colors.surface).imePadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (vm.draft.isEmpty()) {
                    Text("$peerName 님에게 쪽지 보내기", fontSize = 13.5.sp, color = colors.textMuted, maxLines = 1)
                }
                BasicTextField(
                    value = vm.draft,
                    onValueChange = vm::onDraftChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.5.sp, color = colors.textStrong),
                    cursorBrush = SolidColor(colors.bluePrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.send() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canSend) colors.bluePrimary else colors.border)
                    .clickable(enabled = canSend) { vm.send() }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    SafeIcons.Send,
                    null,
                    tint = if (canSend) Color.White else colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    if (vm.sending) "전송 중" else "보내기",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSend) Color.White else colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FriendPicker(vm: MessagesViewModel) {
    val colors = SafeLightTheme.colors
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 22.dp)) {
        Text("새 쪽지", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textStrong)
        Text(
            "대화할 친구를 선택하세요",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
        )
        if (vm.friends.isEmpty()) {
            EmptyText("친구가 없어 쪽지를 보낼 수 없습니다. 먼저 친구를 추가해주세요.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.friends.forEach { friend ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bg)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .clickable { vm.openRoom(friend.friendUserId) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Avatar(friend.friendNickname, 38)
                        Text(
                            friend.friendNickname,
                            Modifier.weight(1f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(SafeIcons.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(SafeIcons.Info, null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
            Text("친구로 등록된 사용자에게만 쪽지를 보낼 수 있습니다.", fontSize = 11.5.sp, color = colors.textMuted)
        }
    }
}

@Composable
private fun Avatar(name: String, size: Int) {
    val (c1, c2) = paletteOf(name)
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).ifBlank { "?" },
            fontSize = (size * 0.38f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp),
        fontSize = 13.sp,
        color = SafeLightTheme.colors.textMuted,
        textAlign = TextAlign.Center,
    )
}
