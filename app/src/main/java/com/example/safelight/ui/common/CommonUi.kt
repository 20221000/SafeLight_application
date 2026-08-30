package com.example.safelight.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 화면 여럿이 똑같이 갖고 있던 조각들을 한곳에 모았다.
 * 여기 있는 것은 '어느 화면에 속한 UI'가 아니라 '어느 화면에서나 같은 모양'인 것들만이다.
 */

/**
 * 목록이 비었을 때의 회색 한 줄. 다섯 화면이 각자 정의하고 있었다(글자 크기·색·정렬은 전부 같고
 * 여백만 달랐다).
 *
 * 기본 여백은 쪽지·알림 화면 값이다. 다른 여백을 쓰던 화면은 호출부에서 넘긴다 —
 * 한 값으로 통일하면 그 화면들의 간격이 바뀐다.
 */
@Composable
fun EmptyText(
    text: String,
    horizontal: Dp = 20.dp,
    vertical: Dp = 48.dp,
) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal, vertical = vertical),
        fontSize = 13.sp,
        color = SafeLightTheme.colors.textMuted,
        textAlign = TextAlign.Center,
    )
}

/** 지도 우하단 줌·현재위치 버튼을 담는 흰 판. 지도 화면과 경로 화면이 글자 하나까지 같았다. */
@Composable
fun MapControlSurface(content: @Composable () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(11.dp)),
    ) { content() }
}

@Composable
fun MapControlButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 위치 권한이 있는지. 지도·경로·SOS 세 곳이 각자 같은 확장함수를 갖고 있었다.
 * 위치를 읽기 전에 반드시 이걸로 먼저 확인한다(@SuppressLint("MissingPermission") 의 근거다).
 */
fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
