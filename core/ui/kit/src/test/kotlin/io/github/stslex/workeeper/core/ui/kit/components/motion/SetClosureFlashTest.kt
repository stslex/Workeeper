// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The flash-gating rule from §9, stated where it can be tested without a frame clock.
 *
 * `rememberSetClosureVisuals` fires its pulse only on a false -> true transition. The subtlety
 * is that `LaunchedEffect(isDone)` ALSO runs on first composition, so keying on the value alone
 * flashes every already-completed set whenever a session loads or a completed card is collapsed
 * and reopened — a burst of wow moments for work the user finished minutes ago.
 */
internal class SetClosureFlashTest {

    @Test
    fun `an already-done row entering composition does not flash`() {
        // The regression this pins. `wasDone` is seeded from the CURRENT value precisely so
        // the first composition is a no-op whichever state the row arrives in.
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
