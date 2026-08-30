package com.example.safelight.ui.friends

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.net.FriendDto
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme
import com.example.safelight.ui.common.EmptyText

/**
 * 친구 관리. 웹 FriendsPage 의 모바일 배치를 그대로 옮겼다 —
 * 이름/버튼 줄과 공유 설정 줄로 나눠 좁은 화면에서도 두 방향이 다 보이게 한다.
 */
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onWriteMessage: (Long) -> Unit,
    vm: FriendsViewModel = viewModel(),
) {
    val colors = SafeLightTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var confirmRemove by remember { mutableStateOf<FriendDto?>(null) }

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.messageShown()
    }

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        // 바깥 Scaffold 가 이미 상태바·탭바만큼 비켜 놨다(여기서 또 빼면 여백이 두 번 들어간다).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.clickable(onClick = onBack).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(SafeIcons.ChevronLeft, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    Text("내 정보로", fontSize = 14.sp, color = colors.textMuted)
                }
            }

            item {
                Column {
                    Text("친구 관리", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.textStrong)
                    Text(
                        "긴급 위치 공유를 허용할 친구를 관리하세요",
                        fontSize = 13.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // 공유 설정은 한쪽만 켜도 되는 단방향이다. 두 값이 뭘 뜻하는지 여기서 한 번 설명한다.
                    Text(
                        shareExplanation(colors.textStrong),
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item { AddFriendRow(vm) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FriendTab.entries.forEach { tab ->
                        TabChip(
                            label = tab.label,
                            count = vm.count(tab),
                            selected = vm.tab == tab,
                            onClick = { vm.selectTab(tab) },
                        )
                    }
                }
            }

            when {
                vm.loading -> item { ListCard { EmptyText("불러오는 중...", vertical = 40.dp) } }

                vm.tab == FriendTab.Friends && vm.friends.isEmpty() ->
                    item { ListCard { EmptyText("아직 친구가 없습니다. 위에서 아이디로 요청을 보내보세요.", vertical = 40.dp) } }

                vm.tab == FriendTab.Received && vm.received.isEmpty() ->
                    item { ListCard { EmptyText("받은 친구 요청이 없습니다.", vertical = 40.dp) } }

                vm.tab == FriendTab.Sent && vm.sent.isEmpty() ->
                    item { ListCard { EmptyText("보낸 친구 요청이 없습니다.", vertical = 40.dp) } }

                vm.tab == FriendTab.Friends -> items(vm.friends, key = { it.friendsId }) { friend ->
                    ListCard {
                        FriendRow(
                            friend = friend,
                            onToggleShare = { vm.toggleEmergency(friend) },
                            onMessage = { onWriteMessage(friend.friendUserId) },
                            onRemove = { confirmRemove = friend },
                        )
                    }
                }

                vm.tab == FriendTab.Received -> items(vm.received, key = { it.requestId }) { request ->
                    ListCard {
                        PersonRow(name = request.senderNickname, subtitle = "ID ${request.senderId}") {
                            SmallButton("수락", primary = true) { vm.accept(request.requestId) }
                            Spacer(Modifier.width(8.dp))
                            SmallButton("거절", primary = false) { vm.reject(request.requestId) }
                        }
                    }
                }

                else -> items(vm.sent, key = { it.requestId }) { request ->
                    ListCard {
                        PersonRow(name = request.receiverNickname, subtitle = "ID ${request.receiverId}") {
                            Text("대기중", fontSize = 12.sp, color = colors.textMuted)
                            Spacer(Modifier.width(8.dp))
                            SmallButton("취소", primary = false) { vm.cancel(request.requestId) }
                        }
                    }
                }
            }
        }
    }

    confirmRemove?.let { friend ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("'${friend.friendNickname}' 님을 친구에서 삭제할까요?") },
            text = { Text("서로의 긴급 위치 공유도 함께 끊깁니다.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeFriend(friend.friendUserId)
                    confirmRemove = null
                }) { Text("삭제", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("취소") } },
            containerColor = colors.surface,
            titleContentColor = colors.textStrong,
            textContentColor = colors.textMuted,
        )
    }
}

/** 굵게 세운 두 낱말이 아래 목록의 두 열과 같은 이름이라 눈으로 이어진다. */
private fun shareExplanation(strong: Color): AnnotatedString = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold, color = strong)
    withStyle(bold) { append("내 공유") }
    append("는 내 긴급 위치를 그 친구에게 보여줄지, ")
    withStyle(bold) { append("상대 공유") }
    append("는 그 친구가 나에게 허용했는지입니다. 상대 공유가 ")
    withStyle(bold) { append("OFF") }
    append("면 그 친구의 긴급 위치는 열람할 수 없습니다.")
}

@Composable
private fun AddFriendRow(vm: FriendsViewModel) {
    val colors = SafeLightTheme.colors
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(SafeIcons.Search, null, tint = colors.textMuted, modifier = Modifier.size(17.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (vm.search.isEmpty()) {
                        Text("친구의 아이디 입력", fontSize = 14.sp, color = colors.textMuted)
                    }
                    BasicTextField(
                        value = vm.search,
                        onValueChange = { vm.search = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = colors.textStrong),
                        cursorBrush = SolidColor(colors.bluePrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { vm.sendRequest() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box(
                Modifier
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bluePrimary)
                    .clickable { vm.sendRequest() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("친구 요청", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Text(
            "* 로그인할 때 쓰는 아이디로 요청합니다. 닉네임이 아닙니다.",
            fontSize = 11.5.sp,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TabChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.bluePrimary else colors.surface)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, colors.border, RoundedCornerShape(20.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else colors.textMuted,
            maxLines = 1,
        )
        Text(
            "$count",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) Color.White.copy(alpha = .25f) else colors.bg)
                .padding(horizontal = 7.dp, vertical = 1.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else colors.textMuted,
        )
    }
}

/** 웹은 카드 하나에 줄을 쌓지만, LazyColumn 은 항목마다 카드를 두는 편이 다루기 쉽다. */
@Composable
private fun ListCard(content: @Composable () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
private fun FriendRow(
    friend: FriendDto,
    onToggleShare: () -> Unit,
    onMessage: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(friend.friendNickname)
            Column(Modifier.weight(1f)) {
                Text(
                    friend.friendNickname,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("ID ${friend.friendUserId}", fontSize = 12.sp, color = colors.textMuted)
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .clickable(onClick = onMessage),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SafeIcons.Message, "쪽지", tint = colors.textMuted, modifier = Modifier.size(16.dp))
            }
            SmallButton("삭제", primary = false, onClick = onRemove)
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(colors.border))
        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("내 공유", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
                Toggle(checked = friend.isEmergencyAllowed, onToggle = onToggleShare)
            }
            // 상대 쪽은 내가 못 바꾼다. OFF 면 그 친구의 긴급 위치를 열어도 403 이라 미리 알려 준다.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("상대 공유", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
                ShareBadge(friend.isEmergencyAllowedByFriend)
            }
        }
    }
}

@Composable
private fun PersonRow(
    name: String,
    subtitle: String,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(name)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, fontSize = 12.sp, color = colors.textMuted)
        }
        actions()
    }
}

@Composable
private fun Avatar(name: String) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(colors.blueTint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).ifBlank { "?" },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.bluePrimary,
        )
    }
}

@Composable
private fun ShareBadge(on: Boolean) {
    val colors = SafeLightTheme.colors
    Text(
        if (on) "ON" else "OFF",
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) colors.blueTint else colors.bg)
            .then(
                if (on) Modifier
                else Modifier.border(1.dp, colors.border, RoundedCornerShape(8.dp))
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = if (on) colors.bluePrimary else colors.textMuted,
    )
}

@Composable
private fun Toggle(checked: Boolean, onToggle: () -> Unit) {
    val colors = SafeLightTheme.colors
    val knob by animateDpAsState(if (checked) 20.dp else 2.dp, label = "share")
    Box(
        Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) colors.bluePrimary else colors.border)
            .clickable(onClick = onToggle),
    ) {
        Box(Modifier.offset(x = knob, y = 2.dp).size(20.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun SmallButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) colors.bluePrimary else colors.surface)
            .then(
                if (primary) Modifier
                else Modifier.border(1.dp, colors.border, RoundedCornerShape(10.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
            color = if (primary) Color.White else colors.textMuted,
            maxLines = 1,
        )
    }
}

