// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Every input pair of the trailing slot's selector, including [TrailingSlotKind.EMPTY] — the
 * outcome a dropped branch produces by accident. A golden covers the look, never the when.
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
        // Testing `isSelecting` first would return EMPTY and blank the row the user just tapped.
        assertEquals(
            TrailingSlotKind.CHECK,
            trailingSlotKind(isSelected = true, isSelecting = false),
        )
    }
}
