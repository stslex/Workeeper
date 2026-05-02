// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository.InlineAdhocResult
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordRepository
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
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
    private val personalRecordRepository = mockk<PersonalRecordRepository>(relaxed = true).apply {
        every { observePersonalRecords(any()) } returns flowOf(emptyMap())
    }

    private val interactor = LiveWorkoutInteractorImpl(
        sessionRepository = sessionRepository,
        performedExerciseRepository = performedExerciseRepository,
        setRepository = setRepository,
        exerciseRepository = exerciseRepository,
        trainingRepository = trainingRepository,
        trainingExerciseRepository = trainingExerciseRepository,
        personalRecordRepository = personalRecordRepository,
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
            sessionRepository.finishSessionAtomic(eq(sessionUuid), any(), capture(captured), any())
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
            sessionRepository.finishSessionAtomic(eq(sessionUuid), any(), capture(captured), any())
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
            sessionRepository.finishSessionAtomic(any(), any(), any(), any())
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

    @Test
    fun `loadSession with non-adhoc training and null trainingExercise plan falls back to adhocPlan`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        val exerciseUuid = "ex-1"
        val adhoc = listOf(PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK))
        seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
        coEvery { trainingExerciseRepository.getPlan(trainingUuid, exerciseUuid) } returns null
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid) } returns adhoc

        val snapshot = interactor.loadSession(sessionUuid)

        assertEquals(adhoc, snapshot?.exercises?.single()?.planSets)
    }

    @Test
    fun `loadSession with non-adhoc training and empty plan returns empty without fallback`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        val exerciseUuid = "ex-1"
        seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
        coEvery { trainingExerciseRepository.getPlan(trainingUuid, exerciseUuid) } returns emptyList()
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid) } returns listOf(
            PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK),
        )

        val snapshot = interactor.loadSession(sessionUuid)

        assertEquals(emptyList<PlanSetDataModel>(), snapshot?.exercises?.single()?.planSets)
        coVerify(exactly = 0) { exerciseRepository.getAdhocPlan(exerciseUuid) }
    }

    @Test
    fun `loadSession with non-adhoc training and non-empty trainingExercise plan returns it as-is`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        val exerciseUuid = "ex-1"
        val plan = listOf(PlanSetDataModel(weight = 100.0, reps = 3, type = SetTypeDataModel.WORK))
        seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
        coEvery { trainingExerciseRepository.getPlan(trainingUuid, exerciseUuid) } returns plan
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid) } returns listOf(
            PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK),
        )

        val snapshot = interactor.loadSession(sessionUuid)

        assertEquals(plan, snapshot?.exercises?.single()?.planSets)
        coVerify(exactly = 0) { exerciseRepository.getAdhocPlan(exerciseUuid) }
    }

    @Test
    fun `loadSession with adhoc training uses getAdhocPlan directly without trainingExercise lookup`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        val exerciseUuid = "ex-1"
        val adhoc = listOf(PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK))
        seedAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid) } returns adhoc

        val snapshot = interactor.loadSession(sessionUuid)

        assertEquals(adhoc, snapshot?.exercises?.single()?.planSets)
        coVerify(exactly = 0) {
            trainingExerciseRepository.getPlan(any(), any())
        }
    }

    private suspend fun seedNonAdhocLoad(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
    ) {
        seedLoad(sessionUuid, trainingUuid, exerciseUuid, isAdhoc = false)
    }

    private suspend fun seedAdhocLoad(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
    ) {
        seedLoad(sessionUuid, trainingUuid, exerciseUuid, isAdhoc = true)
    }

    private suspend fun seedLoad(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
        isAdhoc: Boolean,
    ) {
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
            isAdhoc = isAdhoc,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
            exerciseUuids = listOf(exerciseUuid),
        )
        coEvery { performedExerciseRepository.getBySession(sessionUuid) } returns listOf(
            PerformedExerciseDataModel(
                uuid = "pe-1",
                sessionUuid = sessionUuid,
                exerciseUuid = exerciseUuid,
                position = 0,
                skipped = false,
            ),
        )
        coEvery { exerciseRepository.getExercisesByUuid(listOf(exerciseUuid)) } returns listOf(
            ExerciseDataModel(
                uuid = exerciseUuid,
                name = "Bench",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                lastAdhocSets = null,
            ),
        )
        coEvery { setRepository.getByPerformedExercise("pe-1") } returns emptyList()
    }

    @Test
    fun `createAdhocSession delegates to repository and surfaces both UUIDs`() = runTest {
        coEvery {
            sessionRepository.createAdhocSession(
                name = "Quick start",
                exerciseUuids = listOf("ex-1", "ex-2"),
            )
        } returns SessionRepository.AdhocSessionResult(
            sessionUuid = "session-new",
            trainingUuid = "training-new",
        )

        val result = interactor.createAdhocSession(
            name = "Quick start",
            exerciseUuids = listOf("ex-1", "ex-2"),
        )

        assertEquals("session-new", result.sessionUuid)
        assertEquals("training-new", result.trainingUuid)
    }

    @Test
    fun `createAdhocSession with empty exercise list still produces a session`() = runTest {
        coEvery {
            sessionRepository.createAdhocSession(name = "", exerciseUuids = emptyList())
        } returns SessionRepository.AdhocSessionResult(
            sessionUuid = "blank-session",
            trainingUuid = "blank-training",
        )

        val result = interactor.createAdhocSession(name = "", exerciseUuids = emptyList())

        assertEquals("blank-session", result.sessionUuid)
        assertEquals("blank-training", result.trainingUuid)
    }

    @Test
    fun `addExerciseToActiveSession forwards all three UUIDs verbatim`() = runTest {
        interactor.addExerciseToActiveSession(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
            exerciseUuid = "ex-mid",
        )

        coVerify(exactly = 1) {
            sessionRepository.addExerciseToActiveSession(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
                exerciseUuid = "ex-mid",
            )
        }
    }

    @Test
    fun `discardAdhocSession delegates to repository`() = runTest {
        interactor.discardAdhocSession(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
        )

        coVerify(exactly = 1) {
            sessionRepository.discardAdhocSession(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
            )
        }
    }

    @Test
    fun `cancelSession on adhoc training cascades through discardAdhocSession`() = runTest {
        coEvery { sessionRepository.getById("session-adhoc") } returns SessionDataModel(
            uuid = "session-adhoc",
            trainingUuid = "training-adhoc",
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 0L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining("training-adhoc") } returns adhocTraining(
            uuid = "training-adhoc",
            isAdhoc = true,
        )

        interactor.cancelSession("session-adhoc")

        coVerify(exactly = 1) {
            sessionRepository.discardAdhocSession(
                sessionUuid = "session-adhoc",
                trainingUuid = "training-adhoc",
            )
        }
        coVerify(exactly = 0) { sessionRepository.deleteSession(any()) }
    }

    @Test
    fun `cancelSession on library training only deletes the session`() = runTest {
        coEvery { sessionRepository.getById("session-lib") } returns SessionDataModel(
            uuid = "session-lib",
            trainingUuid = "training-lib",
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 0L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining("training-lib") } returns adhocTraining(
            uuid = "training-lib",
            isAdhoc = false,
        )

        interactor.cancelSession("session-lib")

        coVerify(exactly = 1) { sessionRepository.deleteSession("session-lib") }
        coVerify(exactly = 0) { sessionRepository.discardAdhocSession(any(), any()) }
    }

    @Test
    fun `createInlineAdhocExercise unwraps the repository envelope`() = runTest {
        coEvery {
            exerciseRepository.createInlineAdhocExercise("Skull Crushers")
        } returns InlineAdhocResult(
            exercise = ExerciseDataModel(
                uuid = "ex-new",
                name = "Skull Crushers",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                lastAdhocSets = null,
            ),
            reusedExisting = false,
        )

        val result = interactor.createInlineAdhocExercise("Skull Crushers")

        assertEquals("ex-new", result.exerciseUuid)
        assertEquals("Skull Crushers", result.name)
        assertEquals(ExerciseTypeDataModel.WEIGHTED, result.type)
        assertEquals(false, result.reusedExisting)
    }

    @Test
    fun `updateTrainingName forwards uuid and name to repository`() = runTest {
        interactor.updateTrainingName("training-1", "Push Day")

        coVerify(exactly = 1) {
            trainingRepository.updateName("training-1", "Push Day")
        }
    }

    @Test
    fun `searchExercisesForPicker maps repo entries to picker-local DTOs`() = runTest {
        coEvery {
            exerciseRepository.searchActiveExercises(
                query = "bench",
                excludeUuids = setOf("ex-already-in"),
            )
        } returns listOf(
            ExerciseDataModel(
                uuid = "ex-1",
                name = "Bench Press",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                lastAdhocSets = null,
            ),
            ExerciseDataModel(
                uuid = "ex-2",
                name = "Bench Press (Incline)",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                lastAdhocSets = null,
            ),
        )

        val results = interactor.searchExercisesForPicker(
            query = "bench",
            excludedUuids = setOf("ex-already-in"),
        )

        assertEquals(listOf("ex-1", "ex-2"), results.map { it.uuid })
        assertEquals(listOf("Bench Press", "Bench Press (Incline)"), results.map { it.name })
    }

    private fun adhocTraining(uuid: String, isAdhoc: Boolean): TrainingDataModel = TrainingDataModel(
        uuid = uuid,
        name = "Track now: Bench Press",
        description = null,
        isAdhoc = isAdhoc,
        archived = false,
        archivedAt = null,
        timestamp = 0L,
        labels = emptyList(),
        exerciseUuids = emptyList(),
    )
}
