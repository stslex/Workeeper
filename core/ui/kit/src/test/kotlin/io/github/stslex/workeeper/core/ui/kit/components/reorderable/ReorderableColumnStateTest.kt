// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.reorderable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The reorder drag, asserted directly — because a golden cannot see it.
 *
 * §27: "a golden image gates only what a single static frame contains. Anything whose evidence
 * needs a second frame, a gesture or a scroll is invisible to it, and will pass a mutation
 * silently." A drag is three frames minimum and a gesture throughout, so the pictures this stage
 * records for the training editor can show that there is **one handle where two arrows used to
 * be** and nothing at all about whether dragging it works.
 *
 * **This class had no tests, in either of its two consumers.** It has shipped since the
 * past-session rebuild and the editors stage makes it the training editor's only reorder
 * affordance — deleting the up/down buttons removes the path that WAS covered
 * (`ClickHandlerTest`'s `OnExerciseReorder` cases still cover the store side; what went is the
 * only *tested* way to produce those arguments). Replacing a covered control with an uncovered
 * one is a coverage regression dressed as a redesign, which is what this file refuses.
 *
 * Its semantics are documented and non-obvious, so each is asserted rather than assumed:
 * **live commit** (the move fires as centres cross, not on release), **re-anchoring** (the finger
 * stays on the same visual point across a swap, so a continued drag does not immediately swap
 * back), and **no terminal call on release**.
 *
 * Geometry: three 100px rows at y = 0/100/200, centres at 50/150/250 — chosen so a crossing is a
 * round number and a reader can check the arithmetic without running anything.
 */
internal class ReorderableColumnStateTest {

    private val moves = mutableListOf<Pair<Int, Int>>()
    private val dragStarts = mutableListOf<Any>()

    private val state = ReorderableColumnState(
        onDragStartCallback = { dragStarts += it },
        onMoveResolved = { from, to -> moves += from to to },
    )

    private fun placeThreeRows() {
        state.onItemPlaced(key = "a", index = 0, top = 0f, bottom = 100f)
        state.onItemPlaced(key = "b", index = 1, top = 100f, bottom = 200f)
        state.onItemPlaced(key = "c", index = 2, top = 200f, bottom = 300f)
    }

    @Test
    fun `a drag past the next row's centre commits the move, and does so before release`() {
        placeThreeRows()
        state.onDragStart("a")

        // "a" rests centred at 50; "b" is centred at 150. 101px puts the finger at 151.
        state.onDrag(deltaY = 101f)

        assertEquals(listOf(0 to 1), moves)
        // Live commit: the move has already fired, with the gesture still down.
        assertTrue(state.isDragging)
    }

    @Test
    fun `a drag that stops short of the centre commits nothing`() {
        placeThreeRows()
        state.onDragStart("a")

        // 99px puts the finger at 149 — one pixel above "b"'s centre.
        state.onDrag(deltaY = 99f)

        assertTrue(moves.isEmpty())
    }

    @Test
    fun `release commits nothing further — the moves already fired`() {
        placeThreeRows()
        state.onDragStart("a")
        state.onDrag(deltaY = 101f)
        val afterDrag = moves.toList()

        state.onDragEnd()

        assertEquals(afterDrag, moves)
        assertFalse(state.isDragging)
        assertNull(state.draggedKey)
    }

    @Test
    fun `cancel commits nothing further either, and it does NOT undo`() {
        // Live-commit semantics, stated in the class KDoc and asserted here because the opposite
        // is the more common design: the list is in its real order throughout the drag, so a
        // cancelled gesture leaves the committed swaps in place rather than rolling them back.
        placeThreeRows()
        state.onDragStart("a")
        state.onDrag(deltaY = 101f)

        state.onDragCancel()

        assertEquals(listOf(0 to 1), moves)
        assertFalse(state.isDragging)
    }

    @Test
    fun `a continued drag in the same direction chains the second swap and does not repeat the first`() {
        placeThreeRows()
        state.onDragStart("a")

        state.onDrag(deltaY = 101f)
        // Re-anchoring means the finger is now at "a"'s NEW centre (150) plus the 1px overshoot.
        // Another 100px carries it past "c"'s centre at 250.
        state.onDrag(deltaY = 100f)

        assertEquals(listOf(0 to 1, 1 to 2), moves)
    }

    @Test
    fun `dragging back the other way swaps back, once`() {
        // The re-anchor exists so this does NOT happen spuriously: after a swap the raw
        // `dragOffsetPx` can change sign, and a direction read off the total offset rather than
        // off the latest delta would fire an immediate reverse swap. Going back has to be an
        // actual reverse gesture, and this asserts it is one.
        placeThreeRows()
        state.onDragStart("a")
        state.onDrag(deltaY = 101f)
        moves.clear()

        // "a" now rests centred at 150 with the finger 1px past it; "b" is centred at 50.
        state.onDrag(deltaY = -102f)

        assertEquals(listOf(1 to 0), moves)
    }

    @Test
    fun `a drag past the last row commits nothing — there is no row to swap with`() {
        placeThreeRows()
        state.onDragStart("c")

        state.onDrag(deltaY = 500f)

        assertTrue(moves.isEmpty())
    }

    @Test
    fun `onDragStart on an unplaced key does nothing, so a stale key cannot start a phantom drag`() {
        placeThreeRows()

        state.onDragStart("not-in-the-list")

        assertFalse(state.isDragging)
        assertTrue(dragStarts.isEmpty())
    }

    @Test
    fun `the drag-start callback fires once, with the key that was grabbed`() {
        placeThreeRows()

        state.onDragStart("b")

        assertEquals(listOf<Any>("b"), dragStarts)
    }

    /**
     * The accessibility path, which is what carries the deleted up/down arrows' SEMANTICS
     * (§26, "Reorder is long-press drag"). `reorderableColumnItem` registers both as
     * `CustomAccessibilityAction`s, so removing the two buttons removed two drawn marks and no
     * capability — provided these two clamp at the ends, which is the whole of their logic.
     */
    @Test
    fun `moveUp and moveDown carry the deleted arrows' semantics, and clamp at both ends`() {
        placeThreeRows()

        state.moveUp(0)
        state.moveDown(2)
        assertTrue(moves.isEmpty())

        state.moveUp(2)
        state.moveDown(0)
        assertEquals(listOf(2 to 1, 0 to 1), moves)
    }
}
