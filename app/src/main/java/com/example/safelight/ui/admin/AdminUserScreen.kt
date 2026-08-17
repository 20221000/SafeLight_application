package com.example.safelight.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.net.UserProfileDto
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 사용자 관리. 웹 AdminUserPage 의 모바일 배치를 옮겼다 —
 * 표 대신 회원 카드, 위험도 칩, 그리고 '관리' 시트 한 곳에 모은 네 가지 처리.
 *
 * [selfUserId] 는 지금 로그인한 관리자다. 백엔드가 자기 자신의 권한·블랙리스트 변경을
 * SecurityException 으로 막으므로 눌리기 전에 잠근다.
 */
@Composable
fun AdminUserScreen(
    selfUserId: Long?,
    vm: AdminUserViewModel = viewModel(),
) {
    val colors = SafeLightTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var sheetUser by remember { mutableStateOf<UserProfileDto?>(null) }
    // 블랙리스트·권한·삭제는 되돌리는 값이 서로 달라서 확인창 하나에 무엇을 물을지만 담아 넘긴다.
    var confirm by remember { mutableStateOf<UserConfirm?>(null) }

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.messageShown()
    }

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vm.error?.let { item { AdminErrorBox("데이터를 불러오지 못했습니다: $it") } }

            item {
                AdminSearchField(
                    value = vm.search,
                    onValueChange = { vm.search = it },
                    placeholder = "닉네임 · 아이디 · 이메일 검색",
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    UserFilter.entries.forEach { entry ->
                        AdminChip(
                            label = entry.label,
                            count = vm.count(entry),
                            selected = vm.filter == entry,
                            onClick = { vm.filter = entry },
                        )
                    }
                }
            }

            val list = vm.visible
            if (list.isEmpty()) {
                item { AdminEmptyCard(if (vm.loading) "불러오는 중…" else "사용자가 없습니다.") }
            } else {
                items(list, key = { it.userId }) { user ->
                    UserCard(user) { sheetUser = user }
                }
            }
        }
    }

    sheetUser?.let { user ->
        val isSelf = selfUserId != null && user.userId == selfUserId
        val name = user.nickname.ifBlank { user.username }
        AdminActionSheet(
            title = name,
            subtitle = "@${user.username} · #${user.userId}",
            actions = listOf(
                SheetAction("정보 수정", ActionTone.Primary) { vm.openEdit(user) },
                SheetAction(
                    label = if (user.isBlacklisted) "블랙리스트 해제" else "블랙리스트 지정",
                    tone = if (user.isBlacklisted) ActionTone.Safe else ActionTone.Danger,
                    enabled = !isSelf,
                    caption = "본인 계정에는 쓸 수 없습니다".takeIf { isSelf },
                    onClick = { confirm = blacklistConfirm(name, user) { vm.toggleBlacklist(user) } },
                ),
                SheetAction(
                    label = if (user.role == "ADMIN") "일반 사용자로 변경" else "관리자로 변경",
                    enabled = !isSelf,
                    caption = "본인 권한은 바꿀 수 없습니다".takeIf { isSelf },
                    onClick = {
                        val next = if (user.role == "ADMIN") "USER" else "ADMIN"
                        confirm = roleConfirm(name, next) { vm.changeRole(user, next) }
                    },
                ),
                SheetAction(
                    label = "사용자 삭제",
                    tone = ActionTone.Danger,
                    enabled = user.role != "ADMIN",
                    caption = "먼저 일반 사용자로 내려야 합니다".takeIf { user.role == "ADMIN" },
                    onClick = { confirm = deleteConfirm(name) { vm.deleteUser(user) } },
                ),
            ),
            onClose = { sheetUser = null },
        )
    }

    confirm?.let { pending ->
        AdminConfirmDialog(
            title = pending.title,
            message = pending.message,
            confirmLabel = pending.confirmLabel,
            danger = pending.danger,
            onConfirm = { pending.run(); confirm = null },
            onDismiss = { confirm = null },
        )
    }

    vm.editing?.let { user -> EditDialog(user, vm) }
}

/** 확인창에 띄울 내용 한 벌. 셋 다 되돌리는 값이 달라 문구를 각자 들고 온다. */
private data class UserConfirm(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val danger: Boolean,
    val run: () -> Unit,
)

/**
 * 블랙리스트는 그 사람이 긴급신고를 못 하게 막는 처리다(백엔드 EmergencyReportService).
 * 그래서 무엇이 막히는지를 적는다 — '정말 하시겠습니까'로는 무게를 알 수 없다.
 */
private fun blacklistConfirm(name: String, user: UserProfileDto, run: () -> Unit): UserConfirm =
    if (user.isBlacklisted) UserConfirm(
        title = "'$name' 의 블랙리스트를 해제할까요?",
        // 해제해도 허위신고 횟수는 그대로다. 3회 이상인 사람은 다음 한 건에 다시 걸린다.
        message = "다시 긴급신고를 할 수 있게 됩니다. 허위신고 횟수(${user.falseReportCount}회)는 " +
            "그대로 남아, 3회 이상이면 다음 허위신고에 곧바로 다시 블랙리스트가 됩니다.",
        confirmLabel = "해제",
        danger = false,
        run = run,
    ) else UserConfirm(
        title = "'$name' 을(를) 블랙리스트에 등록할까요?",
        message = "이 사용자는 긴급신고를 할 수 없게 됩니다. 나머지 기능은 그대로 쓸 수 있고, " +
            "같은 자리에서 해제할 수 있습니다.",
        confirmLabel = "블랙리스트 등록",
        danger = true,
        run = run,
    )

private fun roleConfirm(name: String, role: String, run: () -> Unit) = UserConfirm(
    title = "'$name' 의 권한을 $role 로 바꿀까요?",
    message = if (role == "ADMIN")
        "관리자 콘솔 전체를 쓸 수 있게 됩니다 — 신고 처리, 사용자 관리, 공지까지. " +
            "권한은 토큰에 박혀 있어 그 사용자가 다시 로그인해야 적용됩니다."
    else "관리자 콘솔에 들어갈 수 없게 됩니다. 그 사용자가 다시 로그인해야 적용됩니다.",
    confirmLabel = "권한 변경",
    danger = role != "ADMIN",
    run = run,
)

private fun deleteConfirm(name: String, run: () -> Unit) = UserConfirm(
    title = "'$name' 사용자를 삭제할까요?",
    message = "되돌릴 수 없습니다.",
    confirmLabel = "삭제",
    danger = true,
    run = run,
)

@Composable
private fun UserCard(user: UserProfileDto, onManage: () -> Unit) {
    val colors = SafeLightTheme.colors
    val falseCount = user.falseReportCount
    // 3회면 자동 블랙리스트라 그 앞 단계도 눈에 띄어야 손쓸 틈이 생긴다.
    val countColor = when {
        falseCount >= 3 -> colors.danger
        falseCount > 0 -> colors.warning
        else -> colors.textMuted
    }

    AdminCard(
        background = if (user.isBlacklisted) colors.danger.copy(alpha = .04f) else null,
        borderColor = if (user.isBlacklisted) colors.danger.copy(alpha = .35f) else null,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1E40AF)))
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (user.nickname.ifBlank { user.username }).take(1).ifBlank { "?" },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            user.nickname.ifBlank { user.username },
                            Modifier.weight(1f, fill = false),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AdminBadge(
                            if (user.isBlacklisted) BadgeStyle("차단됨", colors.danger)
                            else BadgeStyle("정상", colors.safe)
                        )
                        if (user.role == "ADMIN") {
                            AdminBadge(BadgeStyle("ADMIN", colors.bluePrimary))
                        }
                    }
                    Text(
                        "@${user.username} · #${user.userId}",
                        fontSize = 12.sp,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(colors.border))

            // 라벨 줄을 기준으로 맞춘다. 아래를 맞추면 배지가 글자보다 낮아서 '가입일'과
            // '허위신고'가 서로 어긋난 높이에 앉는다.
            Row(
                Modifier.padding(top = 11.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text("가입일", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
                    Text(
                        fmtDate(user.createdAt),
                        Modifier.padding(top = 3.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textStrong,
                    )
                }
                Column {
                    Text("허위신고", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
                    Text(
                        "$falseCount / 3",
                        Modifier
                            .padding(top = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(countColor.copy(alpha = .12f))
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = countColor,
                    )
                }
                Box(Modifier.weight(1f))
                AdminActionButton("관리", Modifier.align(Alignment.Bottom), onClick = onManage)
            }
        }
    }
}

@Composable
private fun EditDialog(user: UserProfileDto, vm: AdminUserViewModel) {
    val colors = SafeLightTheme.colors
    AlertDialog(
        onDismissRequest = vm::closeEdit,
        title = {
            Text(
                "사용자 수정 · #${user.userId} ${user.username}",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminField("닉네임", vm.editNickname, { vm.editNickname = it })
                AdminField("이메일", vm.editEmail, { vm.editEmail = it })
                AdminField(
                    "전화번호 (010-1234-5678)",
                    vm.editPhone,
                    vm::onEditPhoneChange,
                    placeholder = "010-1234-5678",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = vm::saveEdit, enabled = !vm.saving) {
                Text(if (vm.saving) "저장 중…" else "저장", color = colors.bluePrimary)
            }
        },
        dismissButton = { TextButton(onClick = vm::closeEdit) { Text("취소") } },
        containerColor = colors.surface,
        titleContentColor = colors.textStrong,
        textContentColor = colors.textMuted,
    )
}
