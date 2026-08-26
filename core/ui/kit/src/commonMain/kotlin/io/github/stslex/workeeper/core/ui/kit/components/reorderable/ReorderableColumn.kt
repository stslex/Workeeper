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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_reorder_move_down
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_reorder_move_up
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.jetbrains.compose.resources.stringResource

/**
 * Row-container modifier for a reorderable Column item: registers bounds, applies the drag
 * visual, and displaces siblings. Gesture detection lives in [reorderableColumnDragHandle].
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
    // The `semantics` block is not a composable scope, so the announced strings resolve here.
    val moveUpLabel = stringResource(Res.string.core_ui_kit_reorder_move_up)
    val moveDownLabel = stringResource(Res.string.core_ui_kit_reorder_move_down)
    // GUARD: nothing else unregisters the row; a stale entry is an off-screen drop target.
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
            if (isDragged) {
                translationY = dragOffset
            }
        }
        .background(backgroundColor)
        .padding(vertical = verticalPadding)
        .semantics {
            // GUARD: register only reachable moves - moveUp/moveDown no-op at the ends while
            // still returning true. [lastIndex] is required and must not gain a default.
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
 * Gesture-only modifier for the drag handle inside a reorderable row: long-press-to-drag,
 * scoped to whatever it decorates. [enabled] = false keeps the handle visible but inert.
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
