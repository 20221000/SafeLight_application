package com.example.safelight.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 게시글 카테고리 배지. 웹 tokens.js 의 POST_CATEGORY 와 같은 색·라벨이다.
 * 한쪽만 바꾸면 두 화면의 배지 색이 갈린다.
 */
data class PostCategoryStyle(val label: String, val color: Color, val background: Color)

/** 모르는 코드면 null. 웹 PostDetailPage 와 같이, 없는 값을 INFO 로 메우지 않는다. */
@Composable
fun postCategoryStyle(code: String?): PostCategoryStyle? {
    val colors = SafeLightTheme.colors
    return when (code) {
        "NOTICE" -> PostCategoryStyle("공지", colors.bluePrimary, colors.blueTint)
        "INFO" -> PostCategoryStyle("정보", colors.info, colors.blueTint)
        "QUESTION" -> PostCategoryStyle("질문", colors.warning, colors.warning.copy(alpha = .13f))
        "TIP" -> PostCategoryStyle("팁", colors.safe, colors.safe.copy(alpha = .13f))
        "REPORT" -> PostCategoryStyle("안전 신고", colors.danger, colors.danger.copy(alpha = .10f))
        else -> null
    }
}

@Composable
fun CategoryBadge(code: String?, modifier: Modifier = Modifier, fontSize: Float = 10.5f) {
    val style = postCategoryStyle(code) ?: return
    Text(
        style.label,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(style.background)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        fontSize = fontSize.sp,
        fontWeight = FontWeight.ExtraBold,
        color = style.color,
        maxLines = 1,
    )
}

/** `2026-08-16T08:35:12` 에서 날짜만. 웹의 `createdAt?.slice(0, 10)` 과 같다. */
fun String?.toDateOnly(): String = this.orEmpty().take(10)
