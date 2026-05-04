package io.github.stslex.workeeper.core.ui.kit.components.reorderable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * State driving non-lazy reorderable Column items.
 *
 * Used inside an outer LazyColumn item where nesting another LazyColumn is not safe
 * (Compose forbids nested same-direction scroll containers). Each child registers its
 * measured Y bounds via the modifier's `onGloballyPositioned`; reorder targets are
 * resolved against those bounds.
 */
@Stable
class ReorderableColumnState internal constructor(
    private val onMoveResolved: (from: Int, to: Int) -> Unit,
) {

    private val itemTops = mutableMapOf<Any, Float>()
    private val itemBottoms = mutableMapOf<Any, Float>()
    private val itemIndices = mutableMapOf<Any, Int>()
    private val keysByIndex = mutableMapOf<Int, Any>()

    var draggedKey: Any? by mutableStateOf(null)
        private set

    var draggedFromIndex: Int by mutableIntStateOf(-1)
        private set

    var draggedToIndex: Int by mutableIntStateOf(-1)
        private set

    var dragOffsetPx: Float by mutableFloatStateOf(0f)
        private set

    private var startCenterPx: Float = 0f
    private var lastKnownLastIndex: Int = -1

    val isDragging: Boolean get() = draggedKey != null

    internal fun onItemPlaced(key: Any, index: Int, top: Float, bottom: Float) {
        itemTops[key] = top
        itemBottoms[key] = bottom
        itemIndices[key] = index
        keysByIndex[index] = key
        if (index > lastKnownLastIndex) lastKnownLastIndex = index
    }

    internal fun onDragStart(key: Any, sourceIndex: Int) {
        val top = itemTops[key] ?: return
        val bottom = itemBottoms[key] ?: return
        draggedKey = key
        draggedFromIndex = sourceIndex
        draggedToIndex = sourceIndex
        dragOffsetPx = 0f
        startCenterPx = (top + bottom) / 2f
    }

    internal fun onDrag(deltaY: Float) {
        if (draggedKey == null) return
        dragOffsetPx += deltaY
        val draggedCenter = startCenterPx + dragOffsetPx
        val candidateKey = itemTops.entries
            .firstOrNull { (k, top) ->
                k != draggedKey &&
                    draggedCenter >= top &&
                    draggedCenter <= (itemBottoms[k] ?: top)
            }
            ?.key
        candidateKey?.let { itemIndices[it]?.also { idx -> draggedToIndex = idx } }
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

    fun moveDown(index: Int) {
        if (index < lastKnownLastIndex) onMoveResolved(index, index + 1)
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
fun rememberReorderableColumnState(
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableColumnState {
    val onMoveLatest by rememberUpdatedState(onMove)
    return remember {
        ReorderableColumnState(onMoveResolved = { from, to -> onMoveLatest(from, to) })
    }
}
