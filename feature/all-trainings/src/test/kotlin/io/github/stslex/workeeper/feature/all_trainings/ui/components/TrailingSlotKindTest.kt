// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The trailing slot's selector — every input pair, **including the one that draws nothing.**
 *
 * §27: a subject golden covers *what a surface looks like* and never *when it is shown*. Those are
 * two gates and only one of them is a picture. Now that the slot crossfades (§26, continuity
 * motion) the second gate matters more than it did, because a crossfade to the wrong kind is a
 * 260ms wrong answer rather than an instant one.
 *
 * [TrailingSlotKind.EMPTY] is written deliberately rather than left implied: "no glyph" is the
 * outcome a dropped branch produces by accident, so it is the case where a green test proves least
 * unless somebody wrote it on purpose.
 *
 * This file exists **because §27's MATCH rule says it must**: the sibling screen has the same four
 * cases, and a behavioural parity claim either cites a test covering both sides or is marked
 * unverified. Asserting the two selectors separately is the cheap half of that.
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
        // The flags arrive independently from the screen. If `isSelecting` were tested first this
        // pair would return EMPTY and blank the mark on the row the user just tapped.
        assertEquals(
            TrailingSlotKind.CHECK,
            trailingSlotKind(isSelected = true, isSelecting = false),
        )
    }
}
