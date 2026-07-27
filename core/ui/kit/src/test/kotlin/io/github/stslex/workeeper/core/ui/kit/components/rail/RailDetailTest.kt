// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.rail

import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The §8 degradation ladder, pinned at its boundaries.
 *
 * The rule is a pure function precisely so it can be tested here rather than only inferred
 * from a screenshot: a golden proves what one width renders, this proves where the thresholds
 * actually sit. Both matter — the goldens would still pass if a threshold moved by a dp.
 *
 * `SUBJECT_WIDTH` (392dp) is the golden canvas width, so the levels asserted here are the
 * levels the rail goldens land on.
 */
internal class RailDetailTest {

    @Test
    fun `the four mockup presets reach only two of the three levels at full width`() {
        // The mockup ships these four as its degradation toggles: 8, 20, 32 and 80 segments.
        // MEASURED, and it contradicts the assumption that they walk the whole ladder:
        // 16x5 lands on EXERCISES, not OVERALL. Verified against the mockup's own rail width
        // (412px = its 452 frame less two 20px gutters) as well as the golden's 392dp — the
        // level is the same at both, so this is not an artefact of the golden canvas.
        assertEquals(RailDetail.SETS, resolveAt(GOLDEN_WIDTH, exercises = 2, sets = 4))
        assertEquals(RailDetail.SETS, resolveAt(GOLDEN_WIDTH, exercises = 5, sets = 4))
        assertEquals(RailDetail.EXERCISES, resolveAt(GOLDEN_WIDTH, exercises = 8, sets = 4))
        assertEquals(RailDetail.EXERCISES, resolveAt(GOLDEN_WIDTH, exercises = 16, sets = 5))
    }

    @Test
    fun `OVERALL needs a narrow rail, not merely a lot of exercises`() {
        // At full width it takes 24 exercises to fall off the exercise level, which is far
        // past any realistic session — so OVERALL is a NARROW-WIDTH state, and the fifth
        // golden constrains the rail rather than inflating the data to reach it.
        assertEquals(RailDetail.EXERCISES, resolveAt(GOLDEN_WIDTH, exercises = 23, sets = 4))
        assertEquals(RailDetail.OVERALL, resolveAt(GOLDEN_WIDTH, exercises = 24, sets = 4))
        assertEquals(RailDetail.OVERALL, resolveAt(NARROW_WIDTH, exercises = 16, sets = 5))
    }

    @Test
    fun `all three levels are exercised by the golden set`() {
        // Guards the golden set itself: if a future geometry change collapsed two of the
        // cases onto one level, the goldens would silently stop covering a branch. The fifth
        // entry is the narrow-width case, which is the only one that reaches OVERALL.
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
        // 4 segments in 1 group: gaps are 3 x 3dp = 9dp, so the boundary is
        // 4 x 9dp + 9dp = 45dp. One dp either side of it.
        assertEquals(RailDetail.SETS, resolveAt(45.dp, exercises = 1, sets = 4))
        assertEquals(RailDetail.SETS, resolveAt(46.dp, exercises = 1, sets = 4))
        assertEquals(RailDetail.EXERCISES, resolveAt(44.dp, exercises = 1, sets = 4))
    }

    @Test
    fun `exercises level holds exactly while every group still meets the minimum`() {
        // 4 groups collapsed to one segment each: gaps are 3 x 6dp = 18dp, so the boundary is
        // 4 x 11dp + 18dp = 62dp. Below it there is nowhere left to go but overall.
        assertEquals(RailDetail.EXERCISES, resolveAt(62.dp, exercises = 4, sets = 4))
        assertEquals(RailDetail.OVERALL, resolveAt(61.dp, exercises = 4, sets = 4))
    }

    @Test
    fun `skipped groups leave the set-level arithmetic but still occupy an exercise slot`() {
        // Two groups of 4, one skipped. At set level only the active group is measured, so it
        // fits in a width that four active segments would need; at exercise level both groups
        // still take a slot, because a skipped exercise is still drawn.
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
        // Below the segment minimum there is nothing for the exercise level to improve on —
        // one group of one segment is the same picture — so it falls straight to OVERALL.
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
