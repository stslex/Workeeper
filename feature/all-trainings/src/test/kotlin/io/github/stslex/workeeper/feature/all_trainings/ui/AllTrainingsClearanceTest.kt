// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The one gate the visual one cannot be.
 *
 * §26 "Add action" fixes the list's bottom clearance at `16 + 56 + 16` = **88** — FAB clearance and
 * nothing else, because the navigation bar's inset is the host's. §24 carried the change to this
 * screen as the only code edit that gate found: `heightLg + screenEdge` = 72 omitted the leading 16,
 * the gap between the last row and the top of the button.
 *
 * It gets a unit test because `contentPadding.bottom` is **invisible in a single frame**: it moves no
 * pixel unless the list is scrolled to its end, and Paparazzi renders one frame of an unscrolled
 * list. No golden can see this value, so no number of goldens closes the hole — the assertion has to
 * be on the value itself. §27 ("a golden image gates only what a single static frame contains")
 * carries the measurement that established it, and names the rest of the class: scroll behaviour,
 * the paging footer's appearance, and motion, where a golden pair pins the endpoints and says
 * nothing about the transit.
 */
internal class AllTrainingsClearanceTest {

    @Test
    fun `list bottom clearance is the drawn 88, not the 72 it shipped with`() {
        assertEquals(88.dp, LIST_BOTTOM_CLEARANCE)
    }

    @Test
    fun `clearance is composed of the three drawn parts, not a literal`() {
        assertEquals(
            AppDimension.screenEdge + AppDimension.heightLg + AppDimension.screenEdge,
            LIST_BOTTOM_CLEARANCE,
        )
    }
}
