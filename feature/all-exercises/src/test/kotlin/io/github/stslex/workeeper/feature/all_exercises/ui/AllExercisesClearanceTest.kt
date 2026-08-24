// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui

import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The list's bottom clearance: `16 + 56 + 16` = 88 (spec §26). A unit test because
 * `contentPadding.bottom` moves no pixel in the single unscrolled frame a golden renders.
 */
internal class AllExercisesClearanceTest {

    @Test
    fun `list bottom clearance is the drawn 88, not the 72 it shipped with`() {
        assertEquals(88.dp, LIST_BOTTOM_CLEARANCE)
    }

    /** The three drawn parts, asserted individually rather than against their own expression. */
    @Test
    fun `each drawn part is the value the mockup gives it`() {
        assertEquals(16.dp, AppDimension.screenEdge, "the drawn 20px gutter, on the ladder")
        assertEquals(56.dp, AppDimension.heightLg, "the FAB's diameter")
    }
}
