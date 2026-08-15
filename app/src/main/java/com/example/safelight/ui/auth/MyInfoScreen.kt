package com.example.safelight.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * '내 정보' 탭. 로그인 전에는 로그인·회원가입 화면이고, 로그인 후에는 계정 요약을 보여준다.
 *
 * 웹의 MyInfoPage 는 프로필·내가 쓴 글·신고 내역까지 담고 있다. 여기는 아직 로그인까지만 옮긴
 * 단계라 그 자리를 비워 뒀다 — 목록을 붙일 때 이 화면을 늘리면 된다.
 */
@Composable
fun MyInfoScreen(vm: AuthViewModel) {
    var showRegister by remember { mutableStateOf(false) }

    val user = vm.user
    if (user == null) {
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

    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(colors.blueTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SafeIcons.MyInfo,
                    null,
                    tint = colors.bluePrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    user.username,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textStrong,
                )
                Text(
                    if (user.isAdmin) "관리자" else "일반 회원",
                    fontSize = 12.5.sp,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (user.isAdmin) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bluePrimary)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text("ADMIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(13.dp))
                .clickable { vm.logout() },
            contentAlignment = Alignment.Center,
        ) {
            Text("로그아웃", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.danger)
        }

        Text(
            "프로필 · 내가 쓴 글 · 신고 내역을 붙일 자리",
            fontSize = 12.5.sp,
            color = colors.textMuted,
        )
    }
}
