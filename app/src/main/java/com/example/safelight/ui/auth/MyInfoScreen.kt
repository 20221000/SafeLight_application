package com.example.safelight.ui.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.SessionUser
import com.example.safelight.data.net.EmergencyReportDto
import com.example.safelight.data.net.PostListDto
import com.example.safelight.ui.community.CategoryBadge
import com.example.safelight.ui.community.toDateOnly
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme
import java.util.Locale

/**
 * '내 정보' 탭. 로그인 전에는 로그인·회원가입 화면이고, 로그인 후에는 웹 MyInfoPage 와 같은 내용이다.
 *
 * 카드 순서도 웹과 같다 — 프로필 / 계정 설정 / 알림 / 쪽지 / 안전 설정 /
 * 내가 쓴 글 / 내 신고 내역 / 신고 신뢰도 / 계정 관리.
 */
@Composable
fun MyInfoScreen(
    vm: AuthViewModel,
    onOpenPost: (Long) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenAdmin: () -> Unit,
    myInfoVm: MyInfoViewModel = viewModel(),
) {
    var showRegister by remember { mutableStateOf(false) }

    val user = vm.user
    if (user == null) {
        // 로그아웃·탈퇴 뒤에 남은 내용을 다음 사람이 보지 않도록 비운다.
        LaunchedEffect(Unit) { myInfoVm.clear() }
        if (showRegister) {
            RegisterScreen(vm = vm, onGoLogin = { showRegister = false })
        } else {
            LoginScreen(
                vm = vm,
                onGoRegister = { showRegister = true },
                onLoggedIn = { showRegister = false },
            )
        }
        return
    }

    LaunchedEffect(user.userId) { myInfoVm.start(user.userId) }

    // 탈퇴가 끝나면 로그인 화면으로 돌아간다.
    LaunchedEffect(myInfoVm.withdrawn) {
        if (myInfoVm.withdrawn) vm.logout()
    }

    MyInfoContent(
        user = user,
        vm = myInfoVm,
        onLogout = vm::logout,
        onOpenPost = onOpenPost,
        onOpenNotifications = onOpenNotifications,
        onOpenMessages = onOpenMessages,
        onOpenFriends = onOpenFriends,
        onOpenAdmin = onOpenAdmin,
    )
}

@Composable
private fun MyInfoContent(
    user: SessionUser,
    vm: MyInfoViewModel,
    onLogout: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var showWithdraw by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.messageShown()
    }

    val profile = vm.profile
    // 서버 값을 먼저 본다. 저장해 둔 세션은 로그인 시점 사진이라 그 뒤에 바뀐 닉네임이 없다.
    val displayName = profile?.nickname?.takeIf { it.isNotBlank() } ?: user.username

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        // 바깥 Scaffold(SafeLightRoot)가 이미 상태바·탭바만큼 비켜 놨다.
        // 여기서 또 빼면 제목 위아래로 한 번 더 여백이 생긴다.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 제목 줄 — 웹 모바일과 같이 좁은 헤더에서 뺀 관리자 전환·로그아웃을 이 자리에 둔다.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("내 정보", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.textStrong)
                    Text(
                        "계정과 안전 설정을 관리하세요",
                        fontSize = 13.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (user.isAdmin) {
                    HeaderIconButton(SafeIcons.Shield, "관리자 콘솔", onOpenAdmin)
                }
                HeaderIconButton(SafeIcons.LogOut, "로그아웃", onLogout)
            }

            ProfileHeader(displayName = displayName, user = user, joinedAt = profile?.createdAt.toDateOnly())

            AccountCard(vm = vm)

            // 알림 — 친구의 긴급신고(SOS)
            Card(title = "알림", desc = "친구의 긴급 상황 알림을 확인합니다.") {
                CountRow(
                    label = "알림함",
                    count = vm.unreadNotifications,
                    hasText = "읽지 않은 알림이 ${vm.unreadNotifications}개 있습니다.",
                    emptyText = "읽지 않은 알림이 없습니다.",
                    onClick = onOpenNotifications,
                )
            }

            // 쪽지 — 백엔드가 친구끼리만 주고받도록 막아 둔다.
            Card(title = "쪽지", desc = "친구와 주고받은 쪽지를 확인합니다.") {
                CountRow(
                    label = "쪽지함",
                    count = vm.unreadMessages,
                    hasText = "읽지 않은 쪽지가 ${vm.unreadMessages}개 있습니다.",
                    emptyText = "읽지 않은 쪽지가 없습니다.",
                    onClick = onOpenMessages,
                )
            }

            Card(title = "안전 설정", desc = "긴급 상황 관련 설정입니다.") {
                LinkRow(
                    title = "긴급 위치 공유 친구 관리",
                    desc = "긴급 신고 시 내 위치를 공유할 친구를 지정합니다.",
                    onClick = onOpenFriends,
                )
                Spacer(Modifier.height(10.dp))
                InsetRow {
                    Column(Modifier.weight(1f)) {
                        Text("긴급 알람 소리", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textStrong)
                        Text(
                            "긴급 신고 접수 시 사이렌을 재생합니다.",
                            fontSize = 12.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Toggle(checked = vm.alarmSound, onToggle = vm::toggleAlarmSound)
                }
            }

            Card(
                title = "내가 쓴 글",
                desc = if (vm.myPosts.isNotEmpty()) "총 ${vm.myPosts.size}개의 게시글을 작성했습니다."
                else "커뮤니티에 작성한 글이 여기에 표시됩니다.",
            ) {
                if (vm.myPosts.isEmpty()) {
                    EmptyText("아직 작성한 글이 없습니다.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.myPosts.forEach { MyPostRow(it, onClick = { onOpenPost(it.postId) }) }
                    }
                }
            }

            Card(
                title = "내 신고 내역",
                desc = if (vm.myReports.isNotEmpty()) "총 ${vm.myReports.size}건의 긴급 신고 내역이 있습니다."
                else "내가 접수한 긴급 신고가 여기에 표시됩니다.",
            ) {
                if (vm.myReports.isEmpty()) {
                    EmptyText("아직 신고 내역이 없습니다.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.myReports.forEach { MyReportRow(it) }
                    }
                }
            }

            Card(title = "신고 신뢰도", desc = "허위 긴급신고가 누적되면 서비스 이용이 제한될 수 있습니다.") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val fakeCount = profile?.falseReportCount ?: 0
                    val blacklisted = profile?.isBlacklisted == true
                    StatBox(Modifier.weight(1f), label = "허위신고 횟수") {
                        Text(
                            "$fakeCount / 3",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (fakeCount > 0) colors.danger else colors.textStrong,
                        )
                    }
                    StatBox(Modifier.weight(1f), label = "계정 상태") {
                        Text(
                            if (blacklisted) "블랙리스트" else "정상",
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (blacklisted) colors.danger.copy(alpha = .10f)
                                    else colors.safe.copy(alpha = .13f)
                                )
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (blacklisted) colors.danger else colors.safe,
                        )
                    }
                }
            }

            Card(title = "계정 관리") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "탈퇴 시 모든 정보가 삭제되며 복구할 수 없습니다.",
                        fontSize = 13.sp,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .border(1.dp, colors.danger, RoundedCornerShape(11.dp))
                            .clickable { showWithdraw = true }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("회원 탈퇴", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.danger)
                    }
                }
            }
        }
    }

    if (showWithdraw) {
        AlertDialog(
            onDismissRequest = { showWithdraw = false },
            title = { Text("정말 탈퇴하시겠습니까?") },
            text = { Text("탈퇴 시 모든 정보가 삭제되며 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showWithdraw = false
                    vm.withdraw()
                }) { Text("탈퇴하기", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showWithdraw = false }) { Text("취소") }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textStrong,
            textContentColor = colors.textMuted,
        )
    }
}

/** 제목 줄 오른쪽의 42dp 아이콘 버튼. 웹 MyInfoPage 의 headerIconBtn 과 같은 치수다. */
@Composable
private fun HeaderIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = colors.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProfileHeader(displayName: String, user: SessionUser, joinedAt: String) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1E40AF)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                displayName.take(1),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                displayName,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "@${user.username}",
                fontSize = 13.sp,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            // 이메일·전화는 아래 '계정 설정'에서 보고 고치므로 여기서는 가입일만 둔다.
            if (joinedAt.isNotBlank()) {
                Text(
                    "가입 $joinedAt",
                    fontSize = 12.sp,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (user.isAdmin) {
            Text(
                "관리자",
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.blueTint)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.bluePrimary,
            )
        }
    }
}

/**
 * 계정 설정. 읽을 때와 고칠 때의 생김새를 아예 다르게 둔다 —
 * 같은 입력칸에서 테두리만 지우면 라벨과 값이 비슷한 크기·색이라 어느 쪽이 내 정보인지 안 보인다.
 */
@Composable
private fun AccountCard(vm: MyInfoViewModel) {
    val colors = SafeLightTheme.colors
    Card(title = "계정 설정", desc = "프로필과 비밀번호를 변경할 수 있습니다.") {
        if (vm.editingProfile) {
            LabeledField("닉네임", vm.nickname, { vm.nickname = it }, "닉네임")
            LabeledField(
                "이메일",
                vm.email,
                { vm.email = it },
                "등록된 이메일 없음",
                keyboardType = KeyboardType.Email,
            )
            // 백엔드가 010-1234-5678 형식만 받아서 누르는 대로 하이픈을 넣어 준다.
            LabeledField(
                "전화번호",
                vm.phone,
                vm::onPhoneChange,
                "등록된 전화번호 없음",
                keyboardType = KeyboardType.Phone,
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
            ) {
                ReadRow("닉네임", vm.nickname, "등록된 닉네임 없음", divider = true)
                ReadRow("이메일", vm.email, "등록된 이메일 없음", divider = true)
                ReadRow("전화번호", vm.phone, "등록된 전화번호 없음", divider = false)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            if (vm.editingProfile) {
                CardButton("취소", primary = false, enabled = !vm.saving, onClick = vm::cancelProfileEdit)
            }
            CardButton(
                text = when {
                    vm.saving -> "저장 중..."
                    vm.editingProfile -> "프로필 저장"
                    else -> "프로필 수정"
                },
                primary = true,
                enabled = !vm.saving,
                onClick = {
                    if (vm.editingProfile) vm.saveProfile() else vm.startProfileEdit()
                },
            )
        }

        Box(
            Modifier
                .padding(vertical = 20.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border),
        )

        LabeledField(
            "현재 비밀번호",
            vm.currentPassword,
            { vm.currentPassword = it },
            "현재 비밀번호",
            isPassword = true,
        )
        LabeledField(
            "새 비밀번호",
            vm.newPassword,
            { vm.newPassword = it },
            "새 비밀번호 (8자 이상)",
            isPassword = true,
        )
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            CardButton("비밀번호 변경", primary = false, enabled = !vm.saving, onClick = vm::changePassword)
        }
    }
}

@Composable
private fun ReadRow(label: String, value: String, empty: String, divider: Boolean) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(label, fontSize = 12.5.sp, color = colors.textMuted, modifier = Modifier.width(62.dp))
        Text(
            value.ifBlank { empty },
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 값이 없으면 굵게 세울 게 아니라 '비어 있다'로 읽혀야 한다.
            fontSize = if (value.isNotBlank()) 14.5.sp else 13.sp,
            fontWeight = if (value.isNotBlank()) FontWeight.Bold else FontWeight.Medium,
            color = if (value.isNotBlank()) colors.textStrong else colors.textMuted,
        )
    }
    if (divider) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = SafeLightTheme.colors
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textMuted,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(11.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 14.sp, color = colors.textMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = colors.textStrong),
                cursorBrush = SolidColor(colors.bluePrimary),
                visualTransformation =
                    if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CardButton(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (primary) colors.bluePrimary.copy(alpha = if (enabled) 1f else .7f) else colors.surface)
            .then(
                if (primary) Modifier
                else Modifier.border(1.dp, colors.border, RoundedCornerShape(11.dp))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (primary) Color.White else colors.textStrong,
        )
    }
}

/** 웹 Card 와 같은 치수의 흰 상자. */
@Composable
private fun Card(title: String, desc: String? = null, content: @Composable () -> Unit) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(22.dp),
    ) {
        Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = colors.textStrong)
        if (desc != null) {
            Text(desc, fontSize = 12.5.sp, color = colors.textMuted, modifier = Modifier.padding(top = 3.dp))
        }
        Column(Modifier.padding(top = 16.dp)) { content() }
    }
}

/** 카드 안의 회색 줄. */
@Composable
private fun InsetRow(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CountRow(label: String, count: Int, hasText: String, emptyText: String, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    InsetRow(onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textStrong)
                if (count > 0) {
                    Text(
                        "$count",
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.danger)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Text(
                if (count > 0) hasText else emptyText,
                fontSize = 12.sp,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(SafeIcons.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LinkRow(title: String, desc: String, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    InsetRow(onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textStrong)
            Text(desc, fontSize = 12.sp, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(SafeIcons.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
    }
}

/** 웹 Toggle 과 같은 치수(42x24, 손잡이 20). */
@Composable
private fun Toggle(checked: Boolean, onToggle: () -> Unit) {
    val colors = SafeLightTheme.colors
    val knobOffset by animateDpAsState(if (checked) 20.dp else 2.dp, label = "toggle")
    Box(
        Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) colors.bluePrimary else colors.border)
            .clickable(onClick = onToggle),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset, y = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun StatBox(modifier: Modifier, label: String, value: @Composable () -> Unit) {
    val colors = SafeLightTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 12.sp, color = colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
        value()
    }
}

@Composable
private fun MyPostRow(post: PostListDto, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    InsetRow(onClick = onClick) {
        CategoryBadge(post.category)
        Spacer(Modifier.width(10.dp))
        Text(
            post.title,
            modifier = Modifier.weight(1f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Text(post.createdAt.toDateOnly(), fontSize = 12.sp, color = colors.textMuted, maxLines = 1)
    }
}

@Composable
private fun MyReportRow(report: EmergencyReportDto) {
    val colors = SafeLightTheme.colors
    // 허위 판정은 상태(RECEIVED/RESOLVED/FALSE)와 별개로 오므로 그쪽을 먼저 본다.
    val status: Pair<String, Color> = when {
        report.isFalseReport -> "허위" to colors.danger
        report.reportStatus == "RESOLVED" -> "완료" to colors.safe
        report.reportStatus == "FALSE" -> "오탐" to colors.textMuted
        else -> "접수" to colors.bluePrimary
    }
    val level: Pair<String, Color>? = when (report.dangerLevel) {
        "HIGH" -> "위험" to colors.danger
        "MEDIUM" -> "주의" to colors.warning
        "LOW" -> "관심" to colors.safe
        else -> null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(status.first, status.second)
            level?.let { Badge(it.first, it.second) }
            Text(
                report.description?.takeIf { it.isNotBlank() } ?: "긴급 신고",
                modifier = Modifier.weight(1f),
                fontSize = 13.5.sp,
                color = colors.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(report.reportedAt.toDateOnly(), fontSize = 12.sp, color = colors.textMuted, maxLines = 1)
        }
        val lat = report.latitude
        val lng = report.longitude
        if (lat != null && lng != null) {
            val cctv = report.nearestCctv?.cctvName?.takeIf { it.isNotBlank() }
            Text(
                String.format(Locale.US, "%.5f, %.5f", lat, lng) + if (cctv != null) " · 인근 CCTV $cctv" else "",
                fontSize = 11.5.sp,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = .12f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        fontSize = 13.sp,
        color = SafeLightTheme.colors.textMuted,
        textAlign = TextAlign.Center,
    )
}
