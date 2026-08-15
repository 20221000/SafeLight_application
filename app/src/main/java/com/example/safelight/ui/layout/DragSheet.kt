package com.example.safelight.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.safelight.ui.theme.SafeLightTheme
import kotlin.math.abs

/** 핸들 바만 남은 높이. 웹 BottomSheet 의 SHEET_COLLAPSED 와 같다. */
val SHEET_COLLAPSED = 26.dp

/** 다 올렸을 때 화면에서 차지하는 비율(웹 useDragSheet 의 fullRatio). */
private const val FULL_RATIO = 0.92f

/**
 * 지도 위에 얹는 드래그 바텀시트. 웹 useDragSheet 를 옮긴 것이다.
 *
 * 스냅 지점은 셋이다:
 *  - collapsed : 핸들 바만 남고 지도만 보인다
 *  - mid       : 기본값. 시트 머리말(제목 블록)까지 보인다
 *  - full      : 화면을 거의 다 덮는다
 *
 * mid 높이를 상수로 박지 않고 제목 블록을 실측해서 쓴다 — 글꼴 크기 설정이나 기기 폭에 따라
 * 제목 줄 높이가 달라져서, 상수로 두면 어떤 기기에서는 제목이 잘리고 어떤 기기에서는 아랫줄이 샌다.
 */
@Stable
class DragSheetState(private val collapsedPx: Float) {

    var containerHeightPx by mutableFloatStateOf(0f)
    var headHeightPx by mutableFloatStateOf(0f)

    /** NaN 이면 아직 사용자가 건드리지 않은 것 — 그때는 mid 를 쓴다. */
    private var heightPx by mutableFloatStateOf(Float.NaN)

    val midPx: Float get() = collapsedPx + headHeightPx
    val fullPx: Float get() = maxOf(midPx, containerHeightPx * FULL_RATIO)
    val currentPx: Float get() = (if (heightPx.isNaN()) midPx else heightPx).coerceIn(collapsedPx, fullPx)
    val isFull: Boolean get() = containerHeightPx > 0f && currentPx >= fullPx - 2f

    /** [deltaPx] 는 손가락이 아래로 갈 때 양수다(그만큼 시트가 낮아진다). */
    fun dragBy(deltaPx: Float) {
        heightPx = (currentPx - deltaPx).coerceIn(collapsedPx, fullPx)
    }

    /** 놓았을 때 가장 가까운 스냅 지점으로 붙는다. */
    fun settle() {
        heightPx = listOf(collapsedPx, midPx, fullPx).minByOrNull { abs(it - currentPx) } ?: midPx
    }

    /** 핸들을 톡 누르면 mid ↔ full 을 오간다. */
    fun toggle() {
        heightPx = if (isFull) midPx else fullPx
    }
}

@Composable
fun rememberDragSheetState(): DragSheetState {
    val collapsedPx = with(LocalDensity.current) { SHEET_COLLAPSED.toPx() }
    return remember(collapsedPx) { DragSheetState(collapsedPx) }
}

/**
 * [head] 는 항상 보이는 제목 블록이다(이 높이가 mid 스냅이 된다).
 * [body] 는 시트를 끝까지 올렸을 때만 스크롤된다 — 그 전에는 본문을 끌어도 시트가 먼저 올라온다.
 */
@Composable
fun DragSheet(
    state: DragSheetState,
    modifier: Modifier = Modifier,
    head: @Composable ColumnScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val colors = SafeLightTheme.colors
    val density = LocalDensity.current
    val scroll = rememberScrollState()

    // 본문 스크롤과 시트 드래그를 중재한다.
    //  · 위로 밀 때  : 시트가 아직 다 안 올라왔으면 시트가 먼저 커진다(본문은 그 뒤에 스크롤).
    //  · 아래로 당길 때: 본문이 더 내려갈 데가 없으면 남은 만큼으로 시트가 내려간다.
    //
    // 손가락으로 끈 것(UserInput)만 본다. 이 조건이 없으면 화면에 처음 들어올 때 시트가 혼자
    // 끝까지 올라간다 — 입력칸에 포커스가 잡히면서 Compose 가 '보이게 하려고' 스크롤을 흘리는데,
    // 그것까지 드래그로 세기 때문이다(2026-08-15 기기에서 확인).
    val nestedScroll = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y < 0f && !state.isFull) {
                    state.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f) {
                    state.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                state.settle()
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .height(with(density) { state.currentPx.toDp() })
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(colors.surface),
    ) {
        // 핸들은 스크롤 영역 밖에 둔다 — 안에 있으면 시트 이동과 본문 스크롤이 서로 먹는다.
        Box(
            Modifier
                .fillMaxWidth()
                .height(SHEET_COLLAPSED)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { state.dragBy(it) },
                    onDragStopped = { state.settle() },
                )
                .clickable { state.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.border),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .nestedScroll(nestedScroll)
                // 시트를 다 올렸을 때만 실제로 스크롤된다(그 전에는 위 nestedScroll 이 다 가져간다).
                .verticalScroll(scroll),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { state.headHeightPx = it.height.toFloat() },
            ) { head() }
            body()
        }
    }
}
