package com.example.safelight.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeColors
import com.example.safelight.ui.theme.SafeLightTheme

// 관리자 화면 다섯 개가 함께 쓰는 조각들. 웹 adminStyles.js 에 해당한다.

/** 배지 한 벌 — 글자와 그 뒤에 깔 옅은 배경색. */
data class BadgeStyle(val label: String, val color: Color) {
    val background: Color get() = color.copy(alpha = .12f)
}

/**
 * 긴급신고 상태. 백엔드 enum 은 RECEIVED · RESOLVED · FALSE 셋뿐이다(PROCESSING 은 없다).
 * 허위 여부는 상태가 아니라 `isFalseReport` 로 따로 온다.
 */
@Composable
fun statusBadge(code: String): BadgeStyle {
    val colors = SafeLightTheme.colors
    return when (code) {
        "RECEIVED" -> BadgeStyle("접수", colors.bluePrimary)
        "RESOLVED" -> BadgeStyle("해결", colors.safe)
        "FALSE" -> BadgeStyle("오탐", colors.textMuted)
        else -> BadgeStyle(code, colors.textMuted)
    }
}

/** 위험도. 웹 LEVEL_STYLE 과 같은 색이다. */
fun levelBadge(code: String?, colors: SafeColors): BadgeStyle = when (code) {
    "HIGH" -> BadgeStyle("HIGH", colors.danger)
    "MEDIUM" -> BadgeStyle("MEDIUM", colors.warning)
    "LOW" -> BadgeStyle("LOW", colors.safe)
    else -> BadgeStyle(code ?: "-", colors.textMuted)
}

@Composable
fun AdminBadge(style: BadgeStyle, modifier: Modifier = Modifier) {
    Text(
        style.label,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(style.background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = style.color,
        maxLines = 1,
    )
}

/** 웹의 필터 칩. 라벨 뒤에 건수를 달아 고르기 전에도 몇 건인지 보인다. */
@Composable
fun AdminChip(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else colors.textMuted,
            maxLines = 1,
        )
        if (count != null) {
            Text(
                "$count",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) Color.White.copy(alpha = .9f) else colors.textMuted.copy(alpha = .7f),
            )
        }
    }
}

/** 흰 바탕 카드. 왼쪽에 색 띠를 두면 목록을 훑을 때 상태가 먼저 눈에 들어온다. */
@Composable
fun AdminCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    borderColor: Color? = null,
    background: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = SafeLightTheme.colors
    val shape = RoundedCornerShape(14.dp)
    // 색 띠가 카드 높이만큼 늘어나야 한다. Row 를 IntrinsicSize.Min 으로 재야
    // 안쪽 fillMaxHeight 가 '내용이 정한 높이'를 기준으로 잡힌다(안 그러면 무한 제약이다).
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(background ?: colors.surface)
            .border(1.dp, borderColor ?: colors.border, shape),
    ) {
        if (accent != null) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun AdminErrorBox(text: String) {
    val colors = SafeLightTheme.colors
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.danger.copy(alpha = .08f))
            .border(1.dp, colors.danger, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = colors.danger,
    )
}

/** 목록이 비었을 때. 카드와 같은 자리를 채워야 '아직 안 온 것'과 '없는 것'이 구분된다. */
@Composable
fun AdminEmptyCard(text: String) {
    val colors = SafeLightTheme.colors
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(vertical = 26.dp),
        fontSize = 13.sp,
        color = colors.textMuted,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun AdminSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = SafeLightTheme.colors
    Row(
        modifier
            .fillMaxWidth()
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
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    fontSize = 14.sp,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = colors.textStrong),
                cursorBrush = SolidColor(colors.bluePrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                SafeIcons.Close,
                "지우기",
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp).clickable { onValueChange("") },
            )
        }
    }
}

/** 라벨 붙은 입력 한 칸. 수정 다이얼로그에서 쓴다. */
@Composable
fun AdminField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Int = 46,
    singleLine: Boolean = true,
    placeholder: String = "",
) {
    val colors = SafeLightTheme.colors
    Column(modifier) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(11.dp))
                .padding(horizontal = 13.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = 13.5.sp, color = colors.textMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                    color = colors.textStrong,
                ),
                cursorBrush = SolidColor(colors.bluePrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 색으로 무게를 알린다. 웹 ActionSheet 의 tone 과 같은 이름이다. */
enum class ActionTone { Default, Primary, Safe, Danger }

/**
 * 한 항목.
 *
 * [caption] 은 잠긴 항목에 붙인다 — 왜 못 누르는지가 아니라 **무엇을 먼저 하면 되는지**를 적는다.
 * 회색으로 죽어 있기만 하면 막다른 길처럼 읽힌다.
 */
data class SheetAction(
    val label: String,
    val tone: ActionTone = ActionTone.Default,
    val enabled: Boolean = true,
    val caption: String? = null,
    val onClick: () -> Unit,
)

/** 웹 ActionSheet. 목록의 '⋮'·'관리' 를 누르면 아래에서 올라온다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminActionSheet(
    title: String,
    subtitle: String?,
    actions: List<SheetAction>,
    onClose: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.5.sp,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(1.dp).background(colors.border))

            actions.forEach { action ->
                val tint = when {
                    !action.enabled -> colors.textMuted.copy(alpha = .45f)
                    action.tone == ActionTone.Primary -> colors.bluePrimary
                    action.tone == ActionTone.Safe -> colors.safe
                    action.tone == ActionTone.Danger -> colors.danger
                    else -> colors.textStrong
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (action.enabled) Modifier.clickable {
                                onClose()
                                action.onClick()
                            } else Modifier
                        )
                        .padding(vertical = 15.dp),
                ) {
                    Text(action.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = tint)
                    if (!action.caption.isNullOrBlank()) {
                        Text(
                            action.caption,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
        }
    }
}

/** 카드 아래 나란히 놓는 버튼. 눌리는 자리가 44 아래로 내려가지 않게 한다. */
@Composable
fun AdminActionButton(
    label: String,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.Default,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    val accent = when (tone) {
        ActionTone.Primary -> colors.bluePrimary
        ActionTone.Safe -> colors.safe
        ActionTone.Danger -> colors.danger
        ActionTone.Default -> colors.textStrong
    }
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (filled) accent else colors.surface)
            .then(
                if (filled) Modifier
                else Modifier.border(1.dp, colors.border, RoundedCornerShape(11.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) Color.White else accent,
            maxLines = 1,
        )
    }
}

// ── 날짜 ──────────────────────────────────────────────────────────────────────
// 백엔드가 `2026-08-17T09:12:33` 형태로 주므로 자르기만 하면 된다.
// minSdk 24 라 java.time 은 못 쓴다(디슈가링을 켜지 않았다).

fun fmtDate(iso: String?): String = iso?.take(10) ?: "-"

fun fmtDateTime(iso: String?): String =
    if (iso.isNullOrBlank()) "-" else iso.take(16).replace('T', ' ')

/** 카드처럼 좁은 자리용 'MM-DD HH:mm'. */
fun fmtShort(iso: String?): String =
    if (iso.isNullOrBlank() || iso.length < 16) "-" else iso.substring(5, 16).replace('T', ' ')
