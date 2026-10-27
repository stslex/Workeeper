// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.NAMED_COPY
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for `StateStatusMapper.recomputeOnly`, the pure status derivation pipeline.
 * Cases 5–7 lock in `visibleSets.indices` counting toward the is-done expected positions.
 */
internal class StateStatusMapperTest {

    private val mapper = StateStatusMapper()

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

    @Test
    fun `recomputeStatuses labels the cards and the header from the State's copy`() {
        val state = State.create(sessionUuid = "session-1", trainingUuid = "training-1").copy(
            isLoading = false,
            liveCopy = NAMED_COPY,
            exercises = persistentListOf(
                exercise(
                    plan = persistentListOf(PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK)),
                    status = ExerciseStatusUiModel.SKIPPED,
                ),
                exercise(plan = persistentListOf()).copy(performedExerciseUuid = "pe-2", position = 1),
                exercise(
                    plan = persistentListOf(PlanSetUiModel(weight = null, reps = 12, type = SetTypeUiModel.WORK)),
                ).copy(performedExerciseUuid = "pe-3", exerciseType = ExerciseTypeUiModel.WEIGHTLESS, position = 2),
            ),
        )

        val result = mapper.recomputeStatuses(state)

        assertEquals(listOf("statusSkipped", "statusNoPlan", "12 repsUnit"), result.exercises.map { it.statusLabel })
        assertEquals("metaSkippedTemplate metaTemplate 0 2 0 1 1", result.headerMetaLabel)
        assertEquals("trainingNamePlaceholder", result.trainingNameLabel)
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
