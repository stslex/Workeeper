package io.github.stslex.workeeper.core.ui.kit.components.reorderable

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * Row-container modifier for a reorderable Column item. Registers the item's measured
 * bounds (so the state can pick a drop target), applies the drag visual (shadow +
 * vertical translation) when this row is the one being dragged, and otherwise applies an
 * animated displacement so siblings between source and target shift out of the way as
 * the drag progresses.
 *
 * Gesture detection lives in [reorderableColumnDragHandle] — this modifier deliberately
 * does **not** install a long-press detector on the whole row, so child widgets that
 * consume long-press (text fields, tooltip wrappers) no longer block the reorder
 * affordance.
 */
@Composable
fun Modifier.reorderableColumnItem(
    state: ReorderableColumnState,
    key: Any,
    index: Int,
    lastIndex: Int,
    tintSelected: Color = AppUi.colors.accentTintedForeground,
    tintUnselected: Color = Color.Transparent,
    verticalPadding: Dp = AppDimension.Space.xs,
): Modifier {
    val isDragged = state.draggedKey == key
    val dragOffset = state.dragOffsetPx

    val backgroundColor by animateColorAsState(
        targetValue = if (isDragged) tintSelected else tintUnselected,
        label = "reorderable-column-background",
    )
    // Resolved out here rather than inside the `semantics` lambda: that block is not a composable
    // scope, and these are announced strings, so they are resources in every language the app has.
    val moveUpLabel = stringResource(R.string.core_ui_kit_reorder_move_up)
    val moveDownLabel = stringResource(R.string.core_ui_kit_reorder_move_down)
    // The row registers itself while placed and MUST unregister when it leaves: nothing else
    // removes it, and a stale entry is a drop target that is not on screen.
    DisposableEffect(key) {
        onDispose { state.onItemDisposed(key) }
    }

    return this
        .onGloballyPositioned { coords ->
            val rootRect = coords.boundsInWindow()
            state.onItemPlaced(key, index, rootRect.top, rootRect.bottom)
        }
        .zIndex(if (isDragged) 1f else 0f)
        .graphicsLayer {
            // If this item is being dragged, we want to disable the default layout-based
            // placement and just follow the finger.
            if (isDragged) {
                translationY = dragOffset
            }
        }
        .background(backgroundColor)
        .padding(vertical = verticalPadding)
        .semantics {
            // Only the moves that can actually happen are offered. `moveUp` no-ops at 0 and
            // `moveDown` no-ops at [lastIndex], so registering both unconditionally advertises an
            // impossible action to a screen reader AND returns `true` for it — the action reports
            // success having done nothing — and a control that reports a move it did not make is
            // worse than one that is simply absent.
            //
            // [lastIndex] is REQUIRED and must not gain a default: a default is a value every
            // existing call site would silently keep, which is the bug.
            customActions = buildList {
                if (index > 0) {
                    add(
                        CustomAccessibilityAction(moveUpLabel) {
                            state.moveUp(index)
                            true
                        },
                    )
                }
                if (index < lastIndex) {
                    add(
                        CustomAccessibilityAction(moveDownLabel) {
                            state.moveDown(index)
                            true
                        },
                    )
                }
            }
        }
}

/**
 * Gesture-only modifier for the small drag-handle widget inside a reorderable row.
 * Applies long-press-to-drag detection scoped to whatever element it decorates — usually
 * a 24dp Icon at the trailing edge.
 *
 * Set [enabled] to `false` to keep the handle visible but inert (e.g. read-only rows).
 */
fun Modifier.reorderableColumnDragHandle(
    state: ReorderableColumnState,
    key: Any,
    enabled: Boolean = true,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(state, key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragCancel() },
        )
    }
}
