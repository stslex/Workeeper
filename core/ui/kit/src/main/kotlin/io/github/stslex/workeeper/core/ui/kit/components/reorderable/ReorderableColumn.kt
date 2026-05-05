package io.github.stslex.workeeper.core.ui.kit.components.reorderable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

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
): Modifier {
    val displacement = state.displacementFor(index, key)
    val animatedDisplacement by animateFloatAsState(
        targetValue = displacement,
        label = "reorderable-column-displacement",
    )
    val isDragged = state.draggedKey == key
    val dragOffset = state.dragOffsetPx
    return this
        .onGloballyPositioned { coords ->
            val rootRect = coords.boundsInWindow()
            state.onItemPlaced(key, index, rootRect.top, rootRect.bottom)
        }
        .then(
            if (isDragged) {
                Modifier
                    .graphicsLayer { translationY = dragOffset }
            } else {
                Modifier.graphicsLayer { translationY = animatedDisplacement }
            },
        )
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction("Move up") {
                    state.moveUp(index)
                    true
                },
                CustomAccessibilityAction("Move down") {
                    state.moveDown(index)
                    true
                },
            )
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
    index: Int,
    enabled: Boolean = true,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(state, key, index) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key, index) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragCancel() },
        )
    }
}

private const val DRAG_SHADOW_ELEVATION_DP = 6
