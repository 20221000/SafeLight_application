package com.example.safelight.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme

/** 가입 후 로그인 화면으로 자동 이동하기까지의 시간. 웹 RegisterPage 의 3초와 같은 값이다. */
private const val AUTO_GO_LOGIN_MS = 3_000L

/** 웹 RegisterPage.jsx 를 옮긴 화면. */
@Composable
fun RegisterScreen(vm: AuthViewModel, onGoLogin: () -> Unit) {
    vm.registered?.let { account ->
        RegisterDoneScreen(account = account, onGoLogin = { vm.consumeRegistered(); onGoLogin() })
        return
    }

    var username by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    fun submit() = vm.register(username, nickname, email, password, passwordConfirm)

    AuthLayout {
        AuthLogo(subtitle = "새 계정을 만들어주세요")

        AuthField("아이디", SafeIcons.User, username, { username = it; vm.clearError() }, "영문, 숫자 조합")
        AuthField("닉네임", SafeIcons.Tag, nickname, { nickname = it; vm.clearError() }, "사용할 닉네임")
        AuthField("이메일", SafeIcons.Mail, email, { email = it; vm.clearError() }, "example@email.com")
        AuthField(
            "비밀번호", SafeIcons.Lock, password, { password = it; vm.clearError() }, "8자 이상",
            isPassword = true,
        )
        AuthField(
            "비밀번호 확인", SafeIcons.Lock, passwordConfirm, { passwordConfirm = it; vm.clearError() },
            "비밀번호 재입력",
            isPassword = true,
            imeAction = ImeAction.Go,
            onImeAction = { submit() },
        )

        AuthError(vm.error)
        AuthSubmitButton(
            text = if (vm.loading) "처리 중..." else "회원가입",
            loading = vm.loading,
            onClick = { submit() },
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("이미 계정이 있으신가요? ", fontSize = 13.sp, color = SafeLightTheme.colors.textMuted)
            Text(
                "로그인",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SafeLightTheme.colors.bluePrimary,
                modifier = Modifier.clickable { vm.clearError(); onGoLogin() },
            )
        }
    }
}

/**
 * 가입 완료 화면. 웹과 같이 방금 만들어진 계정을 눈으로 확인시키고,
 * 자동 이동을 기다리게 두지 않고 버튼으로 건너뛸 수 있게 한다.
 */
@Composable
private fun RegisterDoneScreen(account: RegisteredAccount, onGoLogin: () -> Unit) {
    val colors = SafeLightTheme.colors

    // 자동 이동까지 남은 시간을 막대로 보여준다. '이동합니다...' 뿐이면 언제 넘어갈지 알 수 없다.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(account) {
        progress.animateTo(1f, tween(AUTO_GO_LOGIN_MS.toInt(), easing = LinearEasing))
        onGoLogin()
    }

    AuthLayout {
        AuthLogo(subtitle = "가입이 완료되었습니다")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(colors.safe),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SafeIcons.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(
                        "계정이 만들어졌습니다",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textStrong,
                    )
                    Text(
                        "이제 이 아이디로 로그인할 수 있습니다",
                        fontSize = 12.5.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // 무엇이 만들어졌는지 눈으로 확인시킨다. 오타를 여기서 알아채면 다시 가입하면 된다.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp),
            ) {
                val rows = listOf(
                    "아이디" to account.username,
                    "닉네임" to account.nickname,
                    "이메일" to account.email,
                )
                rows.forEachIndexed { index, (label, value) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(label, fontSize = 12.5.sp, color = colors.textMuted, modifier = Modifier.width(52.dp))
                        Text(
                            value,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (index < rows.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                    }
                }
            }
        }

        Box(Modifier.height(18.dp))
        AuthSubmitButton(text = "로그인하러 가기", loading = false, onClick = onGoLogin)

        Text(
            "잠시 후 로그인 화면으로 이동합니다",
            fontSize = 12.sp,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 7.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.border),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(colors.bluePrimary),
            )
        }
    }
}
