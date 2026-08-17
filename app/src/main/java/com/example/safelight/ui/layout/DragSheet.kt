package com.example.safelight.ui.layout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 핸들 바만 남은 높이. 웹 BottomSheet 의 SHEET_COLLAPSED 와 같다. */
val SHEET_COLLAPSED = 26.dp

/** 다 올렸을 때 화면에서 차지하는 비율(웹 useDragSheet 의 fullRatio). */
private const val FULL_RATIO = 0.92f

/** 놓았을 때 스냅 지점까지 미끄러지는 시간. 웹 `transition: height .24s ease` 와 같다. */
private const val SNAP_MS = 240

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
class DragSheetState(
    private val collapsedPx: Float,
    private val scope: CoroutineScope,
) {

    var containerHeightPx by mutableFloatStateOf(0f)
    var headHeightPx by mutableFloatStateOf(0f)

    /** NaN 이면 아직 사용자가 건드리지 않은 것 — 그때는 mid 를 쓴다. */
    private var heightPx by mutableFloatStateOf(Float.NaN)

    /** 스냅 애니메이션. 다음 손짓이 오면 즉시 끊는다. */
    private var snap: Job? = null

    val midPx: Float get() = collapsedPx + headHeightPx
    val fullPx: Float get() = maxOf(midPx, containerHeightPx * FULL_RATIO)
    val currentPx: Float get() = (if (heightPx.isNaN()) midPx else heightPx).coerceIn(collapsedPx, fullPx)
    val isFull: Boolean get() = containerHeightPx > 0f && currentPx >= fullPx - 2f

    /** 지도 위 버튼들이 피해야 할 높이. 시트를 다 올려도 mid 까지만 따라 올라간다(웹과 같다). */
    val peekPx: Float get() = minOf(currentPx, midPx)

    /**
     * [deltaPx] 는 손가락이 아래로 갈 때 양수다(그만큼 시트가 낮아진다).
     *
     * 끄는 동안에는 애니메이션을 거치지 않고 값을 바로 쓴다. 코루틴에 실어 보내면 한 프레임씩
     * 밀려서, 빠르게 쓸어 올릴 때 여러 델타가 **같은 옛 높이**를 기준으로 계산돼 시트가
     * 거의 안 움직인다(2026-08-17 기기에서 확인).
     */
    fun dragBy(deltaPx: Float) {
        snap?.cancel()
        heightPx = (currentPx - deltaPx).coerceIn(collapsedPx, fullPx)
    }

    /** 놓았을 때 가장 가까운 스냅 지점으로 미끄러진다. */
    fun settle() {
        moveTo(listOf(collapsedPx, midPx, fullPx).minByOrNull { abs(it - currentPx) } ?: midPx)
    }

    /** 핸들을 톡 누르면 mid ↔ full 을 오간다. */
    fun toggle() = moveTo(if (isFull) midPx else fullPx)

    private fun moveTo(target: Float) {
        snap?.cancel()
        val from = currentPx
        snap = scope.launch {
            animate(from, target, animationSpec = tween(SNAP_MS, easing = FastOutSlowInEasing)) { value, _ ->
                heightPx = value
            }
        }
    }
}

@Composable
fun rememberDragSheetState(): DragSheetState {
    val collapsedPx = with(LocalDensity.current) { SHEET_COLLAPSED.toPx() }
    val scope = rememberCoroutineScope()
    return remember(collapsedPx, scope) { DragSheetState(collapsedPx, scope) }
}

/** 한 번의 드래그가 무엇을 하는지. 웹처럼 제스처가 시작될 때 정하고 끝날 때까지 바꾸지 않는다. */
private enum class DragMode { Sheet, Scroll }

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

    // 본문 스크롤과 시트 드래그를 중재한다. 웹 useDragSheet 의 본문 터치 핸들러와 같은 판정이다:
    //  · 시트가 full 이 아니면        → 본문 어디를 잡아도 시트가 움직인다(스크롤은 아직 잠금)
    //  · full 인데 내용이 안 넘치면   → 스크롤할 것이 없으니 역시 시트가 움직인다
    //  · full 이고 맨 위에서 아래로   → 시트가 내려간다
    //  · 그 밖에                      → 본문이 스크롤된다
    //
    // 무엇을 할지는 **제스처가 시작될 때 한 번** 정하고 손을 뗄 때까지 유지한다.
    // 매 이벤트마다 다시 판단하면, 한 번 끌어올리는 사이 시트가 full 이 되는 순간부터
    // 같은 손짓이 본문 스크롤로 넘어가 내용이 홱 지나간다.
    //
    // 손가락으로 끈 것(UserInput)만 본다. 이 조건이 없으면 화면에 처음 들어올 때 시트가 혼자
    // 끝까지 올라간다 — 입력칸에 포커스가 잡히면서 Compose 가 '보이게 하려고' 스크롤을 흘리는데,
    // 그것까지 드래그로 세기 때문이다(2026-08-15 기기에서 확인).
    val nestedScroll = remember(state, scroll) {
        object : NestedScrollConnection {
            private var mode: DragMode? = null

            private fun decide(deltaY: Float): DragMode = when {
                !state.isFull -> DragMode.Sheet
                scroll.maxValue == 0 -> DragMode.Sheet
                scroll.value == 0 && deltaY > 0f -> DragMode.Sheet
                else -> DragMode.Scroll
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val current = mode ?: decide(available.y).also { mode = it }
                if (current != DragMode.Sheet) return Offset.Zero
                state.dragBy(available.y)
                return available   // 본문은 이 제스처 동안 스크롤되지 않는다
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val wasSheet = mode == DragMode.Sheet
                mode = null
                if (!wasSheet) return Velocity.Zero
                state.settle()
                // 남은 속도를 본문에 넘기지 않는다 — 시트를 끌었을 뿐인데 내용까지 튀면 안 된다.
                return available
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
            // 제목 블록은 언제나 시트를 움직인다(웹의 data-sheet-head). 시트를 다 올려 놓고
            // 내용이 길 때, 본문은 스크롤이라 여기를 잡지 않으면 시트를 내릴 방법이 없다.
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { state.dragBy(it) },
                        onDragStopped = { state.settle() },
                    )
                    .onSizeChanged { state.headHeightPx = it.height.toFloat() },
            ) { head() }
            body()
        }
    }
}
