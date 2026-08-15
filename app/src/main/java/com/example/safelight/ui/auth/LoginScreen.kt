package com.example.safelight.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme

/** 웹 LoginPage.jsx 를 옮긴 화면. */
@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onGoRegister: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    var usernameOrEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    fun submit() = vm.login(usernameOrEmail, password, onLoggedIn)

    AuthLayout {
        AuthLogo(subtitle = "안전한 밤길, 로그인하고 시작하세요")

        AuthField(
            label = "아이디 또는 이메일",
            icon = SafeIcons.User,
            value = usernameOrEmail,
            onValueChange = { usernameOrEmail = it; vm.clearError() },
            placeholder = "아이디 또는 이메일 입력",
        )
        AuthField(
            label = "비밀번호",
            icon = SafeIcons.Lock,
            value = password,
            onValueChange = { password = it; vm.clearError() },
            placeholder = "비밀번호 입력",
            isPassword = true,
            imeAction = ImeAction.Go,
            onImeAction = { submit() },
        )

        AuthError(vm.error)
        AuthSubmitButton(
            text = if (vm.loading) "로그인 중..." else "로그인",
            loading = vm.loading,
            onClick = { submit() },
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 22.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("아직 계정이 없으신가요? ", fontSize = 13.sp, color = SafeLightTheme.colors.textMuted)
            Text(
                "회원가입",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SafeLightTheme.colors.bluePrimary,
                modifier = Modifier.clickable { vm.clearError(); onGoRegister() },
            )
        }
    }
}
