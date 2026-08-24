// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Confirms `isDoneLive` and `isDoneLoad` agree when the live state has no drafts, and locks
 * the intended divergence when it has one.
 */
internal class ExerciseDoneRuleTest {

    @Test
    fun `parity case 1 - sparse position 4 only on a 5-set plan`() {
        val plan = (0 until 5).map { planSet() }
        val performed = listOf(set(position = 4, isDone = true))
        assertParity(plan, performed, expected = false)
    }

    @Test
    fun `parity case 2 - sparse positions 1 and 3 on a 5-set plan`() {
        val plan = (0 until 5).map { planSet() }
        val performed = listOf(
            set(position = 1, isDone = true),
            set(position = 3, isDone = true),
        )
        assertParity(plan, performed, expected = false)
    }

    @Test
    fun `parity case 3 - all-done dense on a 5-set plan`() {
        val plan = (0 until 5).map { planSet() }
        val performed = (0 until 5).map { set(position = it, isDone = true) }
        assertParity(plan, performed, expected = true)
    }

    @Test
    fun `parity case 4 - adhoc partial single done at position 0`() {
        val plan = emptyList<PlanSetUiModel>()
        val performed = listOf(set(position = 0, isDone = true))
        assertParity(plan, performed, expected = true)
    }

    @Test
    fun `parity case 5 - adhoc full sparse positions 2 and 4 both done`() {
        val plan = emptyList<PlanSetUiModel>()
        val performed = listOf(
            set(position = 2, isDone = true),
            set(position = 4, isDone = true),
        )
        assertParity(plan, performed, expected = true)
    }

    @Test
    fun `live and load diverge when a draft fills an extra visible row`() {
        // A typed-but-unchecked draft keeps the live path CURRENT; the load path never sees it.
        val plan = emptyList<PlanSetUiModel>()
        val performed = listOf(set(position = 0, isDone = true))
        val visibleWithDraft = listOf(
            set(position = 0, isDone = true),
            set(position = 1, isDone = false),
        )

        val live = ExerciseDoneRule.isDoneLive(
            planSets = plan,
            performedSets = performed,
            visibleSets = visibleWithDraft,
            skipped = false,
        )
        val load = ExerciseDoneRule.isDoneLoad(
            planSets = plan,
            performedSets = performed,
            skipped = false,
        )
        assertFalse(live)
        assertTrue(load)
    }

    private fun assertParity(
        plan: List<PlanSetUiModel>,
        performed: List<LiveSetUiModel>,
        expected: Boolean,
    ) {
        val live = ExerciseDoneRule.isDoneLive(
            planSets = plan,
            performedSets = performed,
            visibleSets = emptyList<LiveSetUiModel>(),
            skipped = false,
        )
        val load = ExerciseDoneRule.isDoneLoad(
            planSets = plan,
            performedSets = performed,
            skipped = false,
        )
        assertEquals(expected, live, "isDoneLive disagreed with expected")
        assertEquals(expected, load, "isDoneLoad disagreed with expected")
        assertEquals(live, load, "live and load disagreed under draft-free input")
    }

    private fun planSet(): PlanSetUiModel =
        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK)

    private fun set(position: Int, isDone: Boolean): LiveSetUiModel = LiveSetUiModel(
        position = position,
        weight = 100.0,
        reps = 5,
        type = SetTypeUiModel.WORK,
        isDone = isDone,
    )
}
