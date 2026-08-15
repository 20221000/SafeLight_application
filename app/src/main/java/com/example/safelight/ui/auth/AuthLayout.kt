package com.example.safelight.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 로그인·회원가입 공통 배경과 카드. 웹 AuthLayout.jsx 를 옮긴 것이다.
 * 데스크탑의 모달 모드는 옮기지 않았다 — 웹에서도 모바일에서는 쓰지 않는다.
 */

/** 배경 격자. 웹 AuthLayout 의 linear-gradient 와 같다(52px 간격, --auth-grid, opacity .7). */
private val GRID_SPACING = 52.dp
private const val GRID_OPACITY = 0.7f

@Composable
fun AuthLayout(content: @Composable () -> Unit) {
    val colors = SafeLightTheme.colors
    val gridColor = colors.authGrid
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .drawBehind {
                val step = GRID_SPACING.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(gridColor, Offset(x, 0f), Size(1f, size.height), alpha = GRID_OPACITY)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawRect(gridColor, Offset(0f, y), Size(size.width, 1f), alpha = GRID_OPACITY)
                    y += step
                }
                // 좌상단·우하단 푸른 번짐. 웹은 화면 밖으로 절반쯤 걸친 원 두 개를 놓는다:
                //   top:-120 left:-80  420×420  → 중심 (130, 90),                반지름 210
                //   bottom:-140 right:-60 460×460 → 중심 (너비-170, 높이-90),     반지름 230
                fun glow(center: Offset, radius: Float, color: Color) = drawCircle(
                    // 웹의 `radial-gradient(circle, color, transparent 70%)`.
                    brush = Brush.radialGradient(
                        0f to color,
                        0.7f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
                glow(Offset(130.dp.toPx(), 90.dp.toPx()), 210.dp.toPx(), Color(0x1A2563EB))
                glow(
                    Offset(size.width - 170.dp.toPx(), size.height - 90.dp.toPx()),
                    230.dp.toPx(),
                    Color(0x142563EB),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier
                    // 웹: width 420 · 모바일 최대 calc(100% - 32px) · margin 24px 0
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    // 웹 모바일: padding 30px 22px
                    .padding(horizontal = 22.dp, vertical = 30.dp),
            ) {
                content()
            }
        }
    }
}

/** 카드 맨 위의 로고 + 한 줄 안내. */
@Composable
fun AuthLogo(subtitle: String) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.bluePrimary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SafeIcons.Shield, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text(
            "Safe Light",
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textStrong,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            subtitle,
            fontSize = 13.sp,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** 라벨 + 아이콘이 붙은 입력칸. 웹 AuthField 와 같은 치수다. */
@Composable
fun AuthField(
    label: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    val colors = SafeLightTheme.colors
    Text(
        label,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.textMuted,
        modifier = Modifier.padding(bottom = 7.dp),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, null, tint = colors.textMuted, modifier = Modifier.size(17.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 14.5.sp, color = colors.textMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.5.sp, color = colors.textStrong),
                cursorBrush = SolidColor(colors.bluePrimary),
                visualTransformation =
                    if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() },
                    onGo = { onImeAction() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Box(Modifier.height(16.dp))
}

/** 카드 맨 아래의 큰 파란 버튼. */
@Composable
fun AuthSubmitButton(text: String, loading: Boolean, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(colors.bluePrimary.copy(alpha = if (loading) 0.7f else 1f))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** 에러 문구. 웹은 버튼 바로 위에 danger 색으로 둔다. */
@Composable
fun AuthError(message: String) {
    if (message.isBlank()) return
    Text(
        message,
        fontSize = 12.5.sp,
        color = SafeLightTheme.colors.danger,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}
