// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionStateDataModel
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor.PerformedExerciseSnapshot
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor.SessionSnapshot
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LiveWorkoutMapperTest {
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `mapper marks the lowest-position non-skipped non-done exercise as CURRENT`() {
        val snapshot = SessionSnapshot(
            session = sessionAt(1000L),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = listOf(
                fullyDone(uuid = "pe-1", position = 0),
                pending(uuid = "pe-2", position = 1),
                pending(uuid = "pe-3", position = 2),
            ),
        )

        val state = snapshot.toState(nowMillis = 5000L, resourceWrapper = resourceWrapper)

        assertEquals(ExerciseStatusUiModel.DONE, state.exercises[0].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, state.exercises[1].status)
        assertEquals(ExerciseStatusUiModel.PENDING, state.exercises[2].status)
    }

    @Test
    fun `mapper marks skipped rows as SKIPPED and walks past them for CURRENT`() {
        val snapshot = SessionSnapshot(
            session = sessionAt(1000L),
            trainingName = "Pull Day",
            isAdhoc = false,
            exercises = listOf(
                pending(
                    uuid = "pe-1",
                    position = 0,
                ).copy(performed = pending("pe-1", 0).performed.copy(skipped = true)),
                pending(uuid = "pe-2", position = 1),
            ),
        )

        val state = snapshot.toState(nowMillis = 1000L, resourceWrapper = resourceWrapper)

        assertEquals(ExerciseStatusUiModel.SKIPPED, state.exercises[0].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, state.exercises[1].status)
    }

    @Test
    fun `mapper preserves session start time on the resulting state`() {
        val snapshot = SessionSnapshot(
            session = sessionAt(startedAt = 7_000L),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = emptyList(),
        )

        val state = snapshot.toState(nowMillis = 9_000L, resourceWrapper = resourceWrapper)

        assertEquals(7_000L, state.startedAt)
        assertEquals(9_000L, state.nowMillis)
        assertEquals(2_000L, state.elapsedMillis)
    }

    @Test
    fun `mapper treats no-plan exercise with logged sets as DONE`() {
        val snapshot = SessionSnapshot(
            session = sessionAt(1000L),
            trainingName = "Adhoc",
            isAdhoc = true,
            exercises = listOf(
                pending(uuid = "pe-1", position = 0).copy(
                    planSets = null,
                    performedSets = listOf(
                        PlanSetDataModel(weight = null, reps = 10, type = SetTypeDataModel.WORK),
                    ),
                ),
                pending(uuid = "pe-2", position = 1),
            ),
        )

        val state = snapshot.toState(nowMillis = 2000L, resourceWrapper = resourceWrapper)

        assertEquals(ExerciseStatusUiModel.DONE, state.exercises[0].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, state.exercises[1].status)
    }

    private fun sessionAt(startedAt: Long): SessionDataModel = SessionDataModel(
        uuid = "session-1",
        trainingUuid = "training-1",
        state = SessionStateDataModel.IN_PROGRESS,
        startedAt = startedAt,
        finishedAt = null,
    )

    private fun pending(uuid: String, position: Int): PerformedExerciseSnapshot =
        PerformedExerciseSnapshot(
            performed = PerformedExerciseDataModel(
                uuid = uuid,
                sessionUuid = "session-1",
                exerciseUuid = "exercise-$position",
                position = position,
                skipped = false,
            ),
            exerciseName = "Exercise $position",
            exerciseType = ExerciseTypeDataModel.WEIGHTED,
            planSets = listOf(
                PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
                PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
            ),
            performedSets = emptyList(),
            performedSetUuids = emptyList(),
        )

    private fun fullyDone(uuid: String, position: Int): PerformedExerciseSnapshot =
        pending(uuid, position).copy(
            performedSets = listOf(
                PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
                PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
            ),
        )
}
