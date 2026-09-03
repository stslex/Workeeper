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
 * State driving non-lazy reorderable Column items; live-commit swaps as centres cross.
 * GUARD: consumers must apply `onMoveResolved` synchronously. See documentation/design-system.md.
 */
@Stable
class ReorderableColumnState internal constructor(
    private val onDragStartCallback: (Any) -> Unit,
    private val onMoveResolved: (from: Int, to: Int) -> Unit,
) {

    private val itemTops = mutableMapOf<Any, Float>()
    private val itemBottoms = mutableMapOf<Any, Float>()
    private val itemIndices = mutableMapOf<Any, Int>()
    private val keysByIndex = mutableMapOf<Int, Any>()

    private var draggedRestingTopPx: Float = 0f
    private var draggedRestingBottomPx: Float = 0f
    private val draggedHeightPx: Float
        get() = draggedRestingBottomPx - draggedRestingTopPx

    var draggedKey: Any? by mutableStateOf(null)
        private set

    var draggedFromIndex: Int by mutableIntStateOf(-1)
        private set

    var dragOffsetPx: Float by mutableFloatStateOf(0f)
        private set

    /** Window-Y of the dragged item's resting centre; re-anchored after every committed swap. */
    private var startCenterPx: Float = 0f

    val isDragging: Boolean get() = draggedKey != null

    internal fun onItemPlaced(
        key: Any,
        index: Int,
        top: Float,
        bottom: Float,
    ) {
        // The dragged item's boundsInWindow() includes the drag translation; never feed it back.
        if (key == draggedKey) return

        val oldIndex = itemIndices[key]
        if (oldIndex != null && oldIndex != index && keysByIndex[oldIndex] == key) {
            keysByIndex.remove(oldIndex)
        }

        itemTops[key] = top
        itemBottoms[key] = bottom
        itemIndices[key] = index
        keysByIndex[index] = key
    }

    /**
     * Forget a row that has left composition; a stale registration lets a drag swap against a
     * dead key. See documentation/design-system.md.
     */
    internal fun onItemDisposed(key: Any) {
        itemTops.remove(key)
        itemBottoms.remove(key)
        val index = itemIndices.remove(key)
        // Clear the slot only if it still points at THIS key; a reorder may have reassigned it.
        if (index != null && keysByIndex[index] == key) {
            keysByIndex.remove(index)
        }
    }

    internal fun onDragStart(key: Any) {
        val sourceIndex = itemIndices[key] ?: return
        val top = itemTops[key] ?: return
        val bottom = itemBottoms[key] ?: return

        onDragStartCallback(key)

        draggedKey = key
        draggedFromIndex = sourceIndex
        dragOffsetPx = 0f

        draggedRestingTopPx = top
        draggedRestingBottomPx = bottom
        startCenterPx = (top + bottom) / 2f
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    internal fun onDrag(deltaY: Float) {
        val draggedKeyValue = draggedKey ?: return
        if (deltaY == 0f) return

        dragOffsetPx += deltaY

        // GUARD: direction comes from the latest delta, not the total offset, which can change
        // sign after a re-anchor and swap straight back.
        val direction = if (deltaY > 0f) 1 else -1

        var safety = MAX_SWAPS_PER_FRAME
        while (safety-- > 0) {
            val fingerY = startCenterPx + dragOffsetPx
            val currentIndex = draggedFromIndex
            val targetIndex = currentIndex + direction

            val targetKey = keysByIndex[targetIndex] ?: break
            if (targetKey == draggedKeyValue) break

            val targetTop = itemTops[targetKey] ?: break
            val targetBottom = itemBottoms[targetKey] ?: break
            val targetCenter = (targetTop + targetBottom) / 2f

            val crossed = if (direction > 0) {
                fingerY > targetCenter
            } else {
                fingerY < targetCenter
            }

            if (!crossed) break

            commitAdjacentSwap(
                draggedKey = draggedKeyValue,
                targetKey = targetKey,
                currentIndex = currentIndex,
                targetIndex = targetIndex,
                targetTop = targetTop,
                targetBottom = targetBottom,
                fingerY = fingerY,
                direction = direction,
            )

            onMoveResolved(currentIndex, targetIndex)
        }
    }

    @Suppress("LongParameterList")
    private fun commitAdjacentSwap(
        draggedKey: Any,
        targetKey: Any,
        currentIndex: Int,
        targetIndex: Int,
        targetTop: Float,
        targetBottom: Float,
        fingerY: Float,
        direction: Int,
    ) {
        val currentTop = draggedRestingTopPx
        val currentBottom = draggedRestingBottomPx
        val draggedHeight = draggedHeightPx
        val targetHeight = targetBottom - targetTop

        val gap = if (direction > 0) {
            targetTop - currentBottom
        } else {
            currentTop - targetBottom
        }

        val newDraggedTop = if (direction > 0) {
            currentTop + targetHeight + gap
        } else {
            currentTop - targetHeight - gap
        }
        val newDraggedBottom = newDraggedTop + draggedHeight
        val newDraggedCenter = (newDraggedTop + newDraggedBottom) / 2f

        val newTargetTop = if (direction > 0) {
            currentTop
        } else {
            newDraggedBottom + gap
        }
        val newTargetBottom = newTargetTop + targetHeight

        // Keep the local cache coherent now; one pointer event can chain several swaps.
        draggedRestingTopPx = newDraggedTop
        draggedRestingBottomPx = newDraggedBottom

        itemTops[draggedKey] = newDraggedTop
        itemBottoms[draggedKey] = newDraggedBottom
        itemIndices[draggedKey] = targetIndex

        itemTops[targetKey] = newTargetTop
        itemBottoms[targetKey] = newTargetBottom
        itemIndices[targetKey] = currentIndex

        keysByIndex[currentIndex] = targetKey
        keysByIndex[targetIndex] = draggedKey

        startCenterPx = newDraggedCenter
        dragOffsetPx = fingerY - newDraggedCenter
        draggedFromIndex = targetIndex
    }

    internal fun onDragEnd() {
        reset()
    }

    internal fun onDragCancel() {
        reset()
    }

    fun moveUp(index: Int) {
        if (index > 0) {
            onMoveResolved(index, index - 1)
        }
    }

    fun moveDown(index: Int) {
        val maxIndex = keysByIndex.keys.maxOrNull() ?: return
        if (index in 0 until maxIndex) {
            onMoveResolved(index, index + 1)
        }
    }

    private fun reset() {
        draggedKey = null
        draggedFromIndex = -1
        dragOffsetPx = 0f
        startCenterPx = 0f
        draggedRestingTopPx = 0f
        draggedRestingBottomPx = 0f
    }
}

@Composable
fun rememberReorderableColumnState(
    onDragStarted: (Any) -> Unit = {},
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableColumnState {
    val onMoveLatest by rememberUpdatedState(onMove)
    return remember {
        ReorderableColumnState(
            onDragStartCallback = onDragStarted,
            onMoveResolved = { from, to -> onMoveLatest(from, to) },
        )
    }
}

private const val MAX_SWAPS_PER_FRAME = 32
