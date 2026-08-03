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
 *
 * Live-commit semantics: as the dragged item's center crosses a neighbor's center we
 * fire [onMoveResolved] immediately and re-anchor the offset so the finger stays on
 * the same visual point across the swap. The list is therefore always in its actual
 * order during drag — no preview-displacement layer that has to "undo" itself on
 * release. On release the state simply resets; no terminal `onMoveResolved` call.
 *
 * Requirement: consumers must apply `onMoveResolved` synchronously (update list state
 * before returning from the lambda). Async/throttled updates desync the crossover
 * logic and are not supported.
 *
 * Sibling motion: with live-commit there is no preview displacement — non-dragged
 * rows snap to their new layout positions when the list reorders. To get smooth
 * slide-in for non-dragged rows, wrap the consumer Column in a `LookaheadScope` and
 * apply `Modifier.animateBounds(scope)` to each row.
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

    /**
     * Window-Y of the dragged item's resting center at its current index.
     * Re-anchored after every committed adjacent swap.
     */
    private var startCenterPx: Float = 0f

    val isDragging: Boolean get() = draggedKey != null

    internal fun onItemPlaced(
        key: Any,
        index: Int,
        top: Float,
        bottom: Float,
    ) {
        // During drag, boundsInWindow() for dragged item includes graphicsLayer.translationY.
        // Do not feed transformed bounds back into crossover math.
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

        // Direction must be based on the latest gesture delta, not on total dragOffset.
        // Otherwise after re-anchor dragOffset can change sign and immediately swap back.
        //
        // MEASURED, and the measurement narrows the claim: `deltaY → dragOffsetPx` here is a
        // NO-OP against `ReorderableColumnStateTest`, and the reason is not a gate hole. The
        // hazard the line above names needs an UNCOMMITTED crossing in the direction the wrong
        // expression picks, and the loop cannot leave one: it commits every crossing before it
        // returns, and `commitAdjacentSwap` re-anchors so the finger sits within the overshoot of
        // the new centre. The two expressions can therefore only ever disagree about which
        // *uncrossed* neighbour to test, and both then `break`. (With unequal row heights the
        // re-anchored offset genuinely can carry the opposite sign — but it points at the row
        // just passed, whose centre is far behind the finger, so that branch breaks too.)
        //
        // So this is defence, not a live fix, and it is kept: the invariant it leans on is the
        // loop's, not this line's, and a future change to the commit path would break it silently.
        // Recorded here rather than in a session log because the next person to mutate it will
        // get the same green and should not have to re-derive why (§27, "a green mutation accuses
        // the SUITE or the MUTATION, and only the reader can tell which"). This is an argument
        // from the state machine, not a proof.
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

        // Keep our local cache coherent immediately. We cannot wait for the next
        // layout pass because the same pointer event can chain several swaps.
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
