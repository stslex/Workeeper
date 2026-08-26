// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.rail

import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The §8 degradation ladder pinned at its boundaries; GOLDEN_WIDTH is the golden canvas. */
internal class RailDetailTest {

    @Test
    fun `the four mockup presets reach only two of the three levels at full width`() {
        // The mockup's four degradation toggles; 16x5 lands on EXERCISES, not OVERALL.
        assertEquals(RailDetail.SETS, resolveAt(GOLDEN_WIDTH, exercises = 2, sets = 4))
        assertEquals(RailDetail.SETS, resolveAt(GOLDEN_WIDTH, exercises = 5, sets = 4))
        assertEquals(RailDetail.EXERCISES, resolveAt(GOLDEN_WIDTH, exercises = 8, sets = 4))
        assertEquals(RailDetail.EXERCISES, resolveAt(GOLDEN_WIDTH, exercises = 16, sets = 5))
    }

    @Test
    fun `OVERALL needs a narrow rail, not merely a lot of exercises`() {
        // At full width 24 exercises are needed to leave the exercise level, far past a session.
        assertEquals(RailDetail.EXERCISES, resolveAt(GOLDEN_WIDTH, exercises = 23, sets = 4))
        assertEquals(RailDetail.OVERALL, resolveAt(GOLDEN_WIDTH, exercises = 24, sets = 4))
        assertEquals(RailDetail.OVERALL, resolveAt(NARROW_WIDTH, exercises = 16, sets = 5))
    }

    @Test
    fun `all three levels are exercised by the golden set`() {
        // Guards the golden set: a geometry change must not collapse two cases onto one level.
        val levels = listOf(2 to 4, 5 to 4, 8 to 4, 16 to 5).map { (exercises, sets) ->
            resolveAt(GOLDEN_WIDTH, exercises, sets)
        } + resolveAt(NARROW_WIDTH, exercises = 16, sets = 5)
        assertEquals(RailDetail.entries.toSet(), levels.toSet())
    }

    @Test
    fun `an empty rail is overall`() {
        assertEquals(RailDetail.OVERALL, RailDetail.resolve(GOLDEN_WIDTH, emptyList()))
    }

    @Test
    fun `sets level holds exactly while every segment still meets the minimum`() {
        // 4 segments, 3 x 3dp gaps: the boundary is 4 x 9dp + 9dp = 45dp.
        assertEquals(RailDetail.SETS, resolveAt(45.dp, exercises = 1, sets = 4))
        assertEquals(RailDetail.SETS, resolveAt(46.dp, exercises = 1, sets = 4))
        assertEquals(RailDetail.EXERCISES, resolveAt(44.dp, exercises = 1, sets = 4))
    }

    @Test
    fun `exercises level holds exactly while every group still meets the minimum`() {
        // 4 groups of one segment, 3 x 6dp gaps: the boundary is 4 x 11dp + 18dp = 62dp.
        assertEquals(RailDetail.EXERCISES, resolveAt(62.dp, exercises = 4, sets = 4))
        assertEquals(RailDetail.OVERALL, resolveAt(61.dp, exercises = 4, sets = 4))
    }

    @Test
    fun `skipped groups leave the set-level arithmetic but still occupy an exercise slot`() {
        // Two groups of 4, one skipped: only the active group measures at set level.
        val groups = listOf(
            railGroup(sets = 4, skipped = true),
            railGroup(sets = 4, skipped = false),
        )
        assertEquals(RailDetail.SETS, RailDetail.resolve(45.dp, groups))

        // Narrow enough that 4 segments no longer fit, but 2 groups do.
        assertEquals(RailDetail.EXERCISES, RailDetail.resolve(30.dp, groups))
    }

    @Test
    fun `a single exercise with a single set stays at sets level in any usable width`() {
        assertEquals(RailDetail.SETS, resolveAt(9.dp, exercises = 1, sets = 1))
        // Below the segment minimum the exercise level is the same picture, so it falls to OVERALL.
        assertEquals(RailDetail.OVERALL, resolveAt(8.dp, exercises = 1, sets = 1))
    }
}

private val GOLDEN_WIDTH = 392.dp

/** The narrow-rail golden's width — the only case in the set that reaches OVERALL. */
private val NARROW_WIDTH = 120.dp

private fun resolveAt(width: androidx.compose.ui.unit.Dp, exercises: Int, sets: Int): RailDetail =
    RailDetail.resolve(width, (0 until exercises).map { railGroup(sets, skipped = false) })

private fun railGroup(sets: Int, skipped: Boolean): RailGroup = RailGroup(
    segments = (0 until sets).map { RailSegment(isFilled = false) }.toImmutableList(),
    isSkipped = skipped,
)
