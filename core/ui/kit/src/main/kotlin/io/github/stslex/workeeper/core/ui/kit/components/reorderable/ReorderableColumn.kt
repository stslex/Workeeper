package io.github.stslex.workeeper.core.ui.kit.components.reorderable

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

fun Modifier.reorderableColumnItem(
    state: ReorderableColumnState,
    key: Any,
    index: Int,
    enabled: Boolean = true,
): Modifier = this
    .onGloballyPositioned { coords ->
        val rootRect = coords.boundsInWindow()
        state.onItemPlaced(key, index, rootRect.top, rootRect.bottom)
    }
    .then(
        if (state.draggedKey == key) {
            Modifier
                .shadow(elevation = DRAG_SHADOW_ELEVATION_DP.dp, clip = false)
                .graphicsLayer { translationY = state.dragOffsetPx }
        } else {
            Modifier
        },
    )
    .then(
        if (enabled) {
            Modifier.pointerInput(state, key, index) {
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
        } else {
            Modifier
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

private const val DRAG_SHADOW_ELEVATION_DP = 6
