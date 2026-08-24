// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The trailing slot's selector, every input pair including [TrailingSlotKind.EMPTY] — "no glyph"
 * is also what a dropped branch produces, so it is asserted on purpose.
 */
internal class TrailingSlotKindTest {

    @Test
    fun `at rest the slot promises a destination`() {
        assertEquals(
            TrailingSlotKind.CHEVRON,
            trailingSlotKind(isSelected = false, isSelecting = false),
        )
    }

    @Test
    fun `an unselected row in selection mode draws nothing and keeps its slot`() {
        assertEquals(
            TrailingSlotKind.EMPTY,
            trailingSlotKind(isSelected = false, isSelecting = true),
        )
    }

    @Test
    fun `a selected row draws the check`() {
        assertEquals(
            TrailingSlotKind.CHECK,
            trailingSlotKind(isSelected = true, isSelecting = true),
        )
    }

    @Test
    fun `selected outranks selecting, so the mark never blanks`() {
        // The flags arrive independently; testing `isSelecting` first would blank the mark on the
        // row the user just tapped.
        assertEquals(
            TrailingSlotKind.CHECK,
            trailingSlotKind(isSelected = true, isSelecting = false),
        )
    }
}
