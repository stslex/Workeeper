package io.github.stslex.workeeper.core.ui.kit.components.reorderable

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

@Stable
class ReorderableLazyListState internal constructor(
    val listState: LazyListState,
    private val onMoveResolved: (from: Int, to: Int) -> Unit,
) {

    var draggedKey: Any? by mutableStateOf(null)
        private set

    var draggedFromIndex: Int by mutableIntStateOf(-1)
        private set

    var draggedToIndex: Int by mutableIntStateOf(-1)
        private set

    var dragOffsetPx: Float by mutableFloatStateOf(0f)
        private set

    private var startCenterPx: Float = 0f

    val isDragging: Boolean get() = draggedKey != null

    internal fun onDragStart(key: Any, sourceIndex: Int) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            ?: return
        draggedKey = key
        draggedFromIndex = sourceIndex
        draggedToIndex = sourceIndex
        dragOffsetPx = 0f
        startCenterPx = info.offset.toFloat() + info.size / 2f
    }

    internal fun onDrag(deltaY: Float) {
        if (draggedKey == null) return
        dragOffsetPx += deltaY
        val draggedCenter = startCenterPx + dragOffsetPx
        val newTarget = listState.layoutInfo.visibleItemsInfo
            .filter { it.key != draggedKey }
            .firstOrNull { item ->
                val itemCenter = item.offset + item.size / 2f
                if (draggedCenter > startCenterPx) {
                    draggedCenter > itemCenter && itemCenter > startCenterPx
                } else {
                    draggedCenter < itemCenter && itemCenter < startCenterPx
                }
            }
        newTarget?.index?.let { draggedToIndex = it }
    }

    internal fun onDragEnd() {
        val from = draggedFromIndex
        val to = draggedToIndex
        if (from >= 0 && to >= 0 && from != to) {
            onMoveResolved(from, to)
        }
        reset()
    }

    internal fun onDragCancel() {
        reset()
    }

    fun moveUp(index: Int) {
        if (index > 0) onMoveResolved(index, index - 1)
    }

    fun moveDown(index: Int, lastIndex: Int) {
        if (index < lastIndex) onMoveResolved(index, index + 1)
    }

    private fun reset() {
        draggedKey = null
        draggedFromIndex = -1
        draggedToIndex = -1
        dragOffsetPx = 0f
        startCenterPx = 0f
    }
}

@Composable
fun rememberReorderableLazyListState(
    listState: LazyListState = rememberLazyListState(),
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableLazyListState {
    val onMoveLatest by rememberUpdatedState(onMove)
    return remember(listState) {
        ReorderableLazyListState(
            listState = listState,
            onMoveResolved = { from, to -> onMoveLatest(from, to) },
        )
    }
}
