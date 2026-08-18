package com.example.safelight.ui.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme

/** 대기 버튼 지름. 웹 모바일과 같은 44 — 터치 타깃 최소 권장치라 더 줄이지 않는다. */
private val IDLE_SIZE = 44.dp

/**
 * 긴급 SOS. 웹 SosButton 을 옮긴 것이다 — 지도 중앙 하단의 상시 버튼과 3단계.
 *
 * 대기 → 확인(지도를 어둡게 덮고 3초 카운트다운, 다시 누르면 즉시 접수) → 완료(위에서 내려오는 배너).
 *
 * 확인 단계를 두는 이유는 오조작이다. 한 번 접수되면 되돌릴 수 없다 —
 * 신고가 남고 위험구역이 생기며 친구들에게 알림이 나간다.
 *
 * [bottomPadding] 은 바텀시트가 가리는 높이다(웹의 `--ls-sheet-peek`).
 */
@Composable
fun BoxScope.SosButton(
    loggedIn: Boolean,
    onNeedLogin: () -> Unit,
    bottomPadding: Dp = 0.dp,
    vm: SosViewModel = viewModel(),
) {
    val context = LocalContextCompat()
    val colors = SafeLightTheme.colors

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.submit(context) else vm.onPermissionDenied()
    }

    // 확인 단계에 들어가면 3초 뒤 저절로 접수된다. 손이 굳어 못 누르는 상황을 위한 것이라
    // 웹과 같은 시간을 쓴다. 화면을 벗어나면(취소·접수) 코루틴이 함께 끝난다.
    LaunchedEffect(vm.phase) {
        if (vm.phase != SosPhase.Confirm) return@LaunchedEffect
        kotlinx.coroutines.delay(COUNTDOWN_MS)
        vm.confirm(context, locationPermission::launch)
    }

    // 완료 배너는 6초 뒤 사라진다(웹과 같다).
    LaunchedEffect(vm.phase) {
        if (vm.phase != SosPhase.Done) return@LaunchedEffect
        kotlinx.coroutines.delay(DONE_MS)
        vm.dismissDone()
    }

    if (vm.phase != SosPhase.Confirm) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IdleButton(loading = vm.loading) {
                if (!loggedIn) onNeedLogin() else vm.openConfirm()
            }
            if (vm.phase == SosPhase.Idle) {
                Text(
                    "위급 상황 시 눌러주세요",
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textStrong,
                )
            }
        }
    }

    if (vm.phase == SosPhase.Confirm) {
        ConfirmOverlay(
            loading = vm.loading,
            onConfirm = { vm.confirm(context, locationPermission::launch) },
            onCancel = vm::cancel,
        )
    }

    if (vm.phase == SosPhase.Done) {
        DoneBanner(reportId = vm.reportId, onClose = vm::dismissDone)
    }

    vm.dialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(dialog.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
            text = { Text(dialog.message, fontSize = 13.5.sp, lineHeight = 21.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissDialog()
                    // 실패했으면 다시 누르는 게 유일한 다음 행동이다. 창을 닫고 버튼을 찾아
                    // 헤매게 두지 않는다(웹도 '다시 시도'를 확인 버튼에 둔다).
                    if (dialog.retry) vm.confirm(context, locationPermission::launch)
                }) {
                    Text(dialog.confirmLabel, color = colors.danger)
                }
            },
            dismissButton = if (dialog.retry) {
                { TextButton(onClick = vm::dismissDialog) { Text("닫기") } }
            } else null,
            containerColor = colors.surface,
            titleContentColor = colors.textStrong,
            textContentColor = colors.textMuted,
        )
    }
}

@Composable
private fun IdleButton(loading: Boolean, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    // 가만히 있으면 지도 위 다른 동그란 버튼과 구분되지 않는다. 웹의 ls-sos 맥박을 옮겼다.
    val pulse = rememberInfiniteTransition(label = "sos")
    val glow by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "glow",
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(IDLE_SIZE)
                // 맥박은 자리를 차지하면 안 된다. 예전처럼 88dp 짜리 Canvas 를 따로 두면
                // 그 크기가 줄의 높이가 되어, 44dp 원 아래에 22dp 짜리 빈 칸이 생기고
                // 안내 문구가 그만큼 멀리 떨어진다. drawBehind 는 이 칸(44dp) 밖으로 넘겨 그려도
                // 레이아웃에는 잡히지 않는다 — 웹이 box-shadow 로 퍼뜨리는 것과 같은 처리다.
                // 반지름·투명도는 웹 @keyframes ls-sos 를 그대로 옮겼다(0 → 16px 로 퍼지며 사라진다).
                .drawBehind {
                    if (loading) return@drawBehind
                    drawCircle(
                        color = colors.danger.copy(alpha = (1f - glow) * .5f),
                        radius = size.minDimension / 2f + 16.dp.toPx() * glow,
                    )
                }
                .clip(CircleShape)
                .background(colors.danger)
                .border(2.dp, Color.White, CircleShape)
                .clickable(enabled = !loading, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                )
            } else {
                // 44dp 안에 아이콘과 글자를 같이 넣으면 둘 다 못 알아본다. 글자만 남긴다(웹과 같다).
                Text("긴급", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun BoxScope.ConfirmOverlay(loading: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = SafeLightTheme.colors
    val countdown = rememberInfiniteTransition(label = "countdown")
    val progress by countdown.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(COUNTDOWN_MS.toInt(), easing = LinearEasing)),
        label = "ring",
    )

    Column(
        Modifier
            .matchParentSize()
            .background(Color(0xFF0F172A).copy(alpha = .35f))
            // 뒤의 지도를 눌러 조작하지 못하게 막는다 — 지금은 이 창에만 답해야 한다.
            .clickable(enabled = false, onClick = {}),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Color.White.copy(alpha = .25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * (if (loading) 1f else progress),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(
                Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(colors.danger)
                    .border(5.dp, Color.White, CircleShape)
                    .clickable(enabled = !loading, onClick = onConfirm)
                    // 원 안쪽 여백. 없으면 안내 문구가 원 밖으로 삐져나가 양끝이 잘린다
                    // (글꼴 크기를 키워 쓰는 기기에서는 더 심하다).
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (loading) {
                    // 위치 조회에 몇 초가 걸릴 수 있다. 화면이 그대로면 눌리지 않은 줄 알고 또 누른다.
                    CircularProgressIndicator(Modifier.size(34.dp), color = Color.White, strokeWidth = 3.dp)
                    Text(
                        "접수 중...",
                        Modifier.padding(top = 10.dp),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Text(
                        "위치를 확인하고 있습니다",
                        Modifier.padding(top = 4.dp),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = .85f),
                    )
                } else {
                    Text("긴급 신고", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(
                        "한 번 더 누르면\n신고됩니다",
                        Modifier.padding(top = 6.dp),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = .92f),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "3초 후 자동 접수",
                        Modifier.padding(top = 4.dp),
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = .8f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // 접수가 시작된 뒤로는 취소할 수 없다. 여기서 취소를 받아주면 신고는 그대로 올라가는데
        // 사용자는 취소된 줄 안다.
        Text(
            "취소",
            Modifier
                .padding(top = 22.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(colors.surface.copy(alpha = if (loading) .5f else 1f))
                .clickable(enabled = !loading, onClick = onCancel)
                .padding(horizontal = 32.dp, vertical = 12.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textStrong,
        )
    }
}

/** 위에서 내려오는 완료 배너. 웹처럼 위 모서리는 각지게 둬 화면에 물려 있는 것으로 읽히게 한다. */
@Composable
private fun BoxScope.DoneBanner(reportId: Long?, onClose: () -> Unit) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(colors.safe),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SafeIcons.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "긴급 신고가 접수되었습니다",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textStrong,
                    )
                    if (reportId != null) {
                        Text(
                            "RP-$reportId",
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.bg)
                                .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMuted,
                        )
                    }
                }
                Text(
                    "허용한 친구에게 현재 위치를 보냈고, 관제센터로 신고가 전송되었습니다.",
                    Modifier.padding(top = 4.dp),
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = colors.textMuted,
                )
                // 웹은 여기에 '사이렌 켜짐'도 적지만 넣지 않았다 — 양쪽 다 소리를 내지 않는다.
                // 울리지도 않는 사이렌이 켜졌다고 적으면, 소리가 안 나는 것을 고장으로 읽는다.
                Text(
                    "위험구역 자동 등록",
                    Modifier.padding(top = 8.dp),
                    fontSize = 11.5.sp,
                    color = colors.textMuted,
                )
            }
            Icon(
                SafeIcons.Close,
                "닫기",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp).clickable(onClick = onClose),
            )
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.safe))
    }
}

/** 권한 확인에 필요한 Context. 컴포저블 안에서 한 줄로 읽으려고 따로 뺐다. */
@Composable
private fun LocalContextCompat(): Context = androidx.compose.ui.platform.LocalContext.current

@SuppressLint("MissingPermission")
internal fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
