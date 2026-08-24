// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The unfilled-set predicate (§6.1) and the count the finish dialog states; the `!isDone`
 * clause is a guard against a future writer, not a reachable case today.
 */
internal class UnfilledSetTest {

    @Test
    fun `a zero-rep row that was never marked done is unfilled`() {
        assertTrue(setRow(reps = 0, isDone = false).isUnfilled)
    }

    @Test
    fun `a row with reps is not unfilled`() {
        assertFalse(setRow(reps = 5, isDone = false).isUnfilled)
    }

    @Test
    fun `a done row is never unfilled even at zero reps`() {
        // Pinned so a future writer cannot make a done row vanish from the record at finish.
        assertFalse(setRow(reps = 0, isDone = true).isUnfilled)
    }

    @Test
    fun `a negative rep count is treated as unfilled, not as work`() {
        assertTrue(setRow(reps = -1, isDone = false).isUnfilled)
    }

    @Test
    fun `unfilledSetCount sums visible rows across exercises and skips SKIPPED ones`() {
        val state = stateWith(
            exercise(
                uuid = "pe-1",
                status = ExerciseStatusUiModel.CURRENT,
                sets = listOf(setRow(reps = 5, isDone = true), setRow(reps = 0, isDone = false)),
            ),
            exercise(
                uuid = "pe-2",
                status = ExerciseStatusUiModel.PENDING,
                sets = listOf(setRow(reps = 0, isDone = false), setRow(reps = 0, isDone = false)),
            ),
            exercise(
                uuid = "pe-3",
                status = ExerciseStatusUiModel.SKIPPED,
                sets = listOf(setRow(reps = 0, isDone = false)),
            ),
        )

        assertEquals(3, state.unfilledSetCount)
    }

    @Test
    fun `unfilledSetCount is zero for a fully logged session`() {
        val state = stateWith(
            exercise(
                uuid = "pe-1",
                status = ExerciseStatusUiModel.DONE,
                sets = listOf(setRow(reps = 5, isDone = true), setRow(reps = 8, isDone = true)),
            ),
        )

        assertEquals(0, state.unfilledSetCount)
    }
}

private fun setRow(reps: Int, isDone: Boolean, position: Int = 0): LiveSetUiModel =
    LiveSetUiModel(
        position = position,
        weight = null,
        reps = reps,
        type = SetTypeUiModel.WORK,
        isDone = isDone,
    )

private fun exercise(
    uuid: String,
    status: ExerciseStatusUiModel,
    sets: List<LiveSetUiModel>,
): LiveExerciseUiModel = LiveExerciseUiModel(
    performedExerciseUuid = uuid,
    exerciseUuid = "ex-$uuid",
    exerciseName = "Exercise $uuid",
    exerciseType = ExerciseTypeUiModel.WEIGHTED,
    position = 0,
    status = status,
    statusLabel = "",
    planSets = persistentListOf(),
    performedSets = persistentListOf(),
    visibleSets = sets.toImmutableList(),
)

private fun stateWith(vararg exercises: LiveExerciseUiModel): LiveWorkoutStore.State =
    LiveWorkoutStore.State.create(sessionUuid = "s-1", trainingUuid = "t-1")
        .copy(exercises = exercises.toList().toImmutableList())
