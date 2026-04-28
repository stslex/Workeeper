// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.SetRepository
import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionStateDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LiveWorkoutInteractorImplTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val performedExerciseRepository = mockk<PerformedExerciseRepository>(relaxed = true)
    private val setRepository = mockk<SetRepository>(relaxed = true)
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val trainingExerciseRepository = mockk<TrainingExerciseRepository>(relaxed = true)

    private val interactor = LiveWorkoutInteractorImpl(
        sessionRepository = sessionRepository,
        performedExerciseRepository = performedExerciseRepository,
        setRepository = setRepository,
        exerciseRepository = exerciseRepository,
        trainingRepository = trainingRepository,
        trainingExerciseRepository = trainingExerciseRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `startSession reuses an in-progress session for the same training`() = runTest {
        val trainingUuid = "training-1"
        coEvery { sessionRepository.getAnyActiveSession() } returns
            io.github.stslex.workeeper.core.exercise.session.model.ActiveSessionInfo(
                sessionUuid = "session-existing",
                trainingUuid = trainingUuid,
                startedAt = 0L,
            )

        val resolved = interactor.startSession(trainingUuid)

        assertEquals("session-existing", resolved)
        coVerify(exactly = 0) {
            sessionRepository.startSessionWithExercises(any(), any())
        }
    }

    @Test
    fun `startSession seeds performed exercises ordered by position`() = runTest {
        val trainingUuid = "training-1"
        coEvery { sessionRepository.getAnyActiveSession() } returns null
        coEvery { trainingExerciseRepository.getRowsForTraining(trainingUuid) } returns listOf(
            TrainingExerciseRepository.TrainingExerciseRow(exerciseUuid = "ex-2", position = 1, planSets = null),
            TrainingExerciseRepository.TrainingExerciseRow(exerciseUuid = "ex-1", position = 0, planSets = null),
        )
        val captured = slot<List<Pair<String, Int>>>()
        coEvery {
            sessionRepository.startSessionWithExercises(eq(trainingUuid), capture(captured))
        } returns SessionDataModel(
            uuid = "session-new",
            trainingUuid = trainingUuid,
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 0L,
            finishedAt = null,
        )

        val resolved = interactor.startSession(trainingUuid)

        assertEquals("session-new", resolved)
        assertEquals(listOf("ex-1" to 0, "ex-2" to 1), captured.captured)
    }

    @Test
    fun `finishSession sends PlanUpdate per non-skipped exercise and finishes atomically`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        coEvery { sessionRepository.getById(sessionUuid) } returns SessionDataModel(
            uuid = sessionUuid,
            trainingUuid = trainingUuid,
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 1_000L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining(trainingUuid) } returns TrainingDataModel(
            uuid = trainingUuid,
            name = "Push Day",
            description = null,
            isAdhoc = false,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
            exerciseUuids = listOf("ex-1", "ex-2"),
        )
        coEvery { performedExerciseRepository.getBySession(sessionUuid) } returns listOf(
            PerformedExerciseDataModel(
                uuid = "pe-1",
                sessionUuid = sessionUuid,
                exerciseUuid = "ex-1",
                position = 0,
                skipped = false,
            ),
            PerformedExerciseDataModel(
                uuid = "pe-2",
                sessionUuid = sessionUuid,
                exerciseUuid = "ex-2",
                position = 1,
                skipped = true,
            ),
        )
        coEvery { setRepository.getByPerformedExercise("pe-1") } returns listOf(
            SetsDataModel(uuid = "s-1", reps = 5, weight = 100.0, type = SetsDataType.WORK),
            SetsDataModel(uuid = "s-2", reps = 5, weight = 100.0, type = SetsDataType.WORK),
        )
        coEvery { trainingExerciseRepository.getPlan(trainingUuid, "ex-1") } returns listOf(
            PlanSetDataModel(weight = 90.0, reps = 5, type = SetTypeDataModel.WORK),
        )
        val captured = slot<List<PlanUpdate>>()
        coEvery {
            sessionRepository.finishSessionAtomic(eq(sessionUuid), any(), capture(captured))
        } returns true

        val result = interactor.finishSession(sessionUuid)

        // Only the non-skipped exercise contributes a PlanUpdate.
        assertEquals(1, captured.captured.size)
        val update = captured.captured.single()
        assertEquals("ex-1", update.exerciseUuid)
        assertEquals(false, update.isAdhoc)
        // PlanUpdateRule promotes the larger performed list to the new plan.
        assertEquals(
            listOf(
                PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
                PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
            ),
            update.newPlan,
        )

        assertEquals(2, result?.setsLogged)
        assertEquals(1, result?.doneCount)
        assertEquals(2, result?.totalCount)
        assertEquals(1, result?.skippedCount)
    }

    @Test
    fun `finishSession marks PlanUpdate as adhoc when training is adhoc`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        coEvery { sessionRepository.getById(sessionUuid) } returns SessionDataModel(
            uuid = sessionUuid,
            trainingUuid = trainingUuid,
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 1_000L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining(trainingUuid) } returns TrainingDataModel(
            uuid = trainingUuid,
            name = "Track now",
            description = null,
            isAdhoc = true,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
            exerciseUuids = listOf("ex-1"),
        )
        coEvery { performedExerciseRepository.getBySession(sessionUuid) } returns listOf(
            PerformedExerciseDataModel(
                uuid = "pe-1",
                sessionUuid = sessionUuid,
                exerciseUuid = "ex-1",
                position = 0,
                skipped = false,
            ),
        )
        coEvery { setRepository.getByPerformedExercise("pe-1") } returns listOf(
            SetsDataModel(uuid = "s-1", reps = 8, weight = 50.0, type = SetsDataType.WORK),
        )
        coEvery { exerciseRepository.getAdhocPlan("ex-1") } returns null
        val captured = slot<List<PlanUpdate>>()
        coEvery {
            sessionRepository.finishSessionAtomic(eq(sessionUuid), any(), capture(captured))
        } returns true

        interactor.finishSession(sessionUuid)

        val update = captured.captured.single()
        assertEquals("ex-1", update.exerciseUuid)
        assertEquals(true, update.isAdhoc)
        assertEquals(
            listOf(PlanSetDataModel(weight = 50.0, reps = 8, type = SetTypeDataModel.WORK)),
            update.newPlan,
        )
    }

    @Test
    fun `finishSession returns null when session is gone after preload`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        coEvery { sessionRepository.getById(sessionUuid) } returns SessionDataModel(
            uuid = sessionUuid,
            trainingUuid = trainingUuid,
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 0L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining(trainingUuid) } returns TrainingDataModel(
            uuid = trainingUuid,
            name = "Push Day",
            description = null,
            isAdhoc = false,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
            exerciseUuids = emptyList(),
        )
        coEvery { performedExerciseRepository.getBySession(sessionUuid) } returns emptyList()
        coEvery {
            sessionRepository.finishSessionAtomic(any(), any(), any())
        } returns false

        val result = interactor.finishSession(sessionUuid)

        assertEquals(null, result)
    }

    @Test
    fun `cancelSession deletes the session row`() = runTest {
        interactor.cancelSession("session-7")
        coVerify(exactly = 1) { sessionRepository.deleteSession("session-7") }
    }

    @Test
    fun `setSkipped also wipes any logged sets when skipping`() = runTest {
        interactor.setSkipped("pe-1", skipped = true)
        coVerify(exactly = 1) { performedExerciseRepository.setSkipped("pe-1", true) }
        coVerify(exactly = 1) { setRepository.deleteAllForPerformedExercise("pe-1") }
    }

    @Test
    fun `setSkipped with false does not wipe sets`() = runTest {
        interactor.setSkipped("pe-1", skipped = false)
        coVerify(exactly = 1) { performedExerciseRepository.setSkipped("pe-1", false) }
        coVerify(exactly = 0) { setRepository.deleteAllForPerformedExercise(any()) }
    }
}
