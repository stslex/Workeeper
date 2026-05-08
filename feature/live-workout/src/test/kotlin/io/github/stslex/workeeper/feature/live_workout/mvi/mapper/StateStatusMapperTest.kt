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
 * Unit tests for `StateStatusMapper.recomputeOnly` — the pure status derivation pipeline.
 * Drives single-exercise inputs with crafted `planSets` / `performedSets` / `visibleSets`
 * combinations and asserts the computed `ExerciseStatusUiModel`.
 *
 * `ResourceWrapper` is relaxed-mocked because `recomputeOnly` does not touch it
 * (presentation strings are applied in the parent `recomputeStatuses` path via
 * `withPresentation`, which these tests don't exercise).
 *
 * The visible-set indices contribute to the "expected positions" set inside the
 * is-done check (alongside `planSets.indices` and the positions of `performed` rows).
 * That guards a previously fixed regression where a partially-completed adhoc exercise
 * could be classified as DONE because empty plan + single performed = single
 * "expected" position. Cases 5–7 lock in the post-fix behavior.
 */
internal class StateStatusMapperTest {

    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val mapper = StateStatusMapper(resourceWrapper)

    @Test
    fun `recomputeOnly assigns CURRENT when plan is empty visible has three rows and only one is performed done`() {
        // Regression: pre-fix, "no plan + one performed done" registered as DONE because
        // expectedPositions only covered plan.indices ∪ performed positions = {0}.
        // After folding visibleSets.indices in, expectedPositions = {0,1,2} and the row
        // is correctly not-done.
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
        // User added one set beyond the plan and completed everything — DONE wins.
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
        // expectedPositions is empty so isDone short-circuits to false via the
        // `isNotEmpty()` guard — distinct from the all-elements-done branch but the
        // observable outcome (status not DONE) is the same. With auto-current and the
        // exercise being neither skipped nor done, it's elected CURRENT.
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
