// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for `StateStatusMapper.recomputeOnly`, the pure status derivation pipeline.
 * Cases 5–7 lock in `visibleSets.indices` counting toward the is-done expected positions.
 */
internal class StateStatusMapperTest {

    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val mapper = StateStatusMapper(resourceWrapper)

    @Test
    fun `recomputeOnly assigns CURRENT when plan is empty visible has three rows and only one is performed done`() {
        // With visibleSets folded in, expectedPositions is {0,1,2}, so this is not done.
        val exercise = exercise(
            plan = persistentListOf(),
            performed = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            ),
            visible = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(1, null, 0, SetTypeUiModel.WORK, isDone = false),
                LiveSetUiModel(2, null, 0, SetTypeUiModel.WORK, isDone = false),
            ),
        )

        val result = mapper.recomputeOnly(listOf(exercise), activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.CURRENT, result.first().status)
    }

    @Test
    fun `recomputeOnly assigns DONE when plan is empty visible has three rows and all three performed are done`() {
        val exercise = exercise(
            plan = persistentListOf(),
            performed = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(2, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            ),
            visible = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(2, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            ),
        )

        val result = mapper.recomputeOnly(listOf(exercise), activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.DONE, result.first().status)
    }

    @Test
    fun `recomputeOnly assigns DONE when plan has two rows visible has three rows and all three performed are done`() {
        val exercise = exercise(
            plan = persistentListOf(
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            performed = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(2, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            ),
            visible = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(2, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            ),
        )

        val result = mapper.recomputeOnly(listOf(exercise), activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.DONE, result.first().status)
    }

    @Test
    fun `recomputeOnly assigns CURRENT when plan has three rows but only positions zero and one are performed done`() {
        val exercise = exercise(
            plan = persistentListOf(
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            performed = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            ),
            visible = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(2, 100.0, 5, SetTypeUiModel.WORK, isDone = false),
            ),
        )

        val result = mapper.recomputeOnly(listOf(exercise), activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.CURRENT, result.first().status)
    }

    @Test
    fun `recomputeOnly assigns CURRENT when plan performed and visible sets are all empty`() {
        // Empty expectedPositions short-circuits isDone to false, so auto-current elects it.
        val exercise = exercise(
            plan = persistentListOf(),
            performed = persistentListOf(),
            visible = persistentListOf(),
        )

        val result = mapper.recomputeOnly(listOf(exercise), activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.CURRENT, result.first().status)
    }

    @Test
    fun `recomputeOnly preserves SKIPPED status even when every visible row is performed done`() {
        val exercise = exercise(
            plan = persistentListOf(
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            performed = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(2, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            ),
            visible = persistentListOf(
                LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                LiveSetUiModel(2, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            ),
            status = ExerciseStatusUiModel.SKIPPED,
        )

        val result = mapper.recomputeOnly(listOf(exercise), activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.SKIPPED, result.first().status)
    }

    private fun exercise(
        plan: ImmutableList<PlanSetUiModel>,
        performed: ImmutableList<LiveSetUiModel> = persistentListOf(),
        visible: ImmutableList<LiveSetUiModel> = persistentListOf(),
        status: ExerciseStatusUiModel = ExerciseStatusUiModel.PENDING,
    ): LiveExerciseUiModel = LiveExerciseUiModel(
        performedExerciseUuid = PE_UUID,
        exerciseUuid = "ex-1",
        exerciseName = "Bench Press",
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        position = 0,
        status = status,
        statusLabel = "",
        planSets = plan,
        performedSets = performed,
        visibleSets = visible,
    )

    private companion object {
        const val PE_UUID = "pe-1"
    }
}
