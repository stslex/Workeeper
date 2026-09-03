// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.reorderable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The reorder drag: live commit, re-anchoring, and no terminal call on release — none of which a
 * golden can see. Geometry throughout: three 100px rows at y = 0/100/200, centres at 50/150/250.
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

    /**
     * GUARD: `onItemPlaced` is the only writer, so a row leaving composition must be disposed —
     * otherwise its bounds and its index outlive it as a phantom drop target.
     */
    @Test
    fun `a disposed row stops being a drop target`() {
        placeThreeRows()
        state.onItemDisposed("c")

        state.onDragStart("b")
        // "b" rests centred at 150; 101px puts the finger at 251, past "c"'s departed centre.
        state.onDrag(deltaY = 101f)

        assertTrue(moves.isEmpty())
    }

    @Test
    fun `moveDown at the new last row does nothing once the row below it is disposed`() {
        placeThreeRows()
        state.onItemDisposed("c")

        // "b" is the last row now; a stale entry for "c" would let index 1 still move down.
        state.moveDown(index = 1)

        assertTrue(moves.isEmpty())
    }

    /** The other direction, so disposal is not simply deleting everything it touches. */
    @Test
    fun `disposing one row leaves the others droppable`() {
        placeThreeRows()
        state.onItemDisposed("c")

        state.onDragStart("a")
        state.onDrag(deltaY = 101f)

        assertEquals(listOf(0 to 1), moves)
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
        // Live commit: a cancelled gesture leaves committed swaps in place, it does not roll back.
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
        // Re-anchoring puts the finger at "a"'s NEW centre (150) + 1px; another 100 crosses 250.
        state.onDrag(deltaY = 100f)

        assertEquals(listOf(0 to 1, 1 to 2), moves)
    }

    @Test
    fun `dragging back the other way swaps back, once`() {
        // The re-anchor stops a spurious reverse: direction reads the latest delta, not the total.
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

    /** The accessibility path: up/down are custom actions too; clamping is their whole logic. */
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
