// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * GUARD: the set-closure pulse fires only on a false -> true transition — keying on `isDone`
 * alone would flash every already-completed set whenever a session loads.
 */
internal class SetClosureFlashTest {

    @Test
    fun `an already-done row entering composition does not flash`() {
        // `wasDone` is seeded from the current value, so first composition is always a no-op.
        assertEquals(false, closedJustNow(previous = true, current = true))
    }

    @Test
    fun `a not-done row entering composition does not flash`() {
        assertEquals(false, closedJustNow(previous = false, current = false))
    }

    @Test
    fun `closing a set flashes`() {
        assertEquals(true, closedJustNow(previous = false, current = true))
    }

    @Test
    fun `unchecking a set does not flash`() {
        assertEquals(false, closedJustNow(previous = true, current = false))
    }
}
