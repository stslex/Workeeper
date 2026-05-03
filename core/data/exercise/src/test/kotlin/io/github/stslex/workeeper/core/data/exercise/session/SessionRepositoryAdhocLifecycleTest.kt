// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SessionRepositoryAdhocLifecycleTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val performedExerciseDao = mockk<PerformedExerciseDao>(relaxed = true)
    private val setDao = mockk<SetDao>(relaxed = true)
    private val trainingDao = mockk<TrainingDao>(relaxed = true)
    private val exerciseDao = mockk<ExerciseDao>(relaxed = true)
    private val trainingExerciseDao = mockk<TrainingExerciseDao>(relaxed = true)
    private val transition = spyk(
        object : DbTransitionRunner {
            override suspend fun <T> invoke(block: suspend () -> T): T = block()
        },
    )

    private val repository = SessionRepositoryImpl(
        dao = sessionDao,
        performedExerciseDao = performedExerciseDao,
        setDao = setDao,
        trainingDao = trainingDao,
        exerciseDao = exerciseDao,
        trainingExerciseDao = trainingExerciseDao,
        transition = transition,
        ioDispatcher = dispatcher,
    )

    @Test
    fun `createAdhocSession inserts training with isAdhoc true and seeds plan plus performed rows`() =
        runTest(dispatcher) {
            val ex1 = Uuid.random()
            val ex2 = Uuid.random()
            val trainingSlot = slot<TrainingEntity>()
            val planSlot = slot<List<TrainingExerciseEntity>>()
            val sessionSlot = slot<SessionEntity>()
            val performedSlot = slot<List<PerformedExerciseEntity>>()
            coEvery { trainingDao.insert(capture(trainingSlot)) } just Runs
            coEvery { trainingExerciseDao.insert(capture(planSlot)) } just Runs
            coEvery {
                sessionDao.startSessionWithExercises(
                    capture(sessionSlot),
                    capture(performedSlot),
                )
            } just Runs

            val result = repository.createAdhocSession(
                name = "Push Day",
                exerciseUuids = listOf(ex1.toString(), ex2.toString()),
            )

            assertTrue(trainingSlot.captured.isAdhoc)
            assertEquals("Push Day", trainingSlot.captured.name)
            assertEquals(trainingSlot.captured.uuid.toString(), result.trainingUuid)
            assertEquals(sessionSlot.captured.uuid.toString(), result.sessionUuid)
            assertEquals(trainingSlot.captured.uuid, sessionSlot.captured.trainingUuid)
            assertEquals(listOf(0, 1), planSlot.captured.map { it.position })
            assertEquals(listOf(0, 1), performedSlot.captured.map { it.position })
            assertEquals(listOf(ex1, ex2), planSlot.captured.map { it.exerciseUuid })
            assertEquals(listOf(ex1, ex2), performedSlot.captured.map { it.exerciseUuid })
            assertTrue(planSlot.captured.all { it.planSets == null })
        }

    @Test
    fun `createAdhocSession with empty exercise list still creates training and session`() =
        runTest(dispatcher) {
            val performedSlot = slot<List<PerformedExerciseEntity>>()
            coEvery {
                sessionDao.startSessionWithExercises(any(), capture(performedSlot))
            } just Runs

            val result = repository.createAdhocSession(name = "", exerciseUuids = emptyList())

            assertTrue(result.sessionUuid.isNotBlank())
            assertTrue(result.trainingUuid.isNotBlank())
            assertTrue(performedSlot.captured.isEmpty())
            // No plan rows means trainingExerciseDao.insert should not be touched.
            coVerify(exactly = 0) { trainingExerciseDao.insert(any<List<TrainingExerciseEntity>>()) }
        }

    @Test
    fun `addExerciseToActiveSession appends at next position in both plan and performed tables`() =
        runTest(dispatcher) {
            val trainingId = Uuid.random()
            val sessionId = Uuid.random()
            val exerciseId = Uuid.random()
            // No exercise history → plan_sets seeds to null, matches inline-create path.
            coEvery { exerciseDao.getById(exerciseId) } returns null
            coEvery { trainingExerciseDao.getMaxPosition(trainingId) } returns 2
            coEvery { performedExerciseDao.getMaxPosition(sessionId) } returns 4
            val planSlot = slot<TrainingExerciseEntity>()
            val performedSlot = slot<PerformedExerciseEntity>()
            coEvery { trainingExerciseDao.insert(capture(planSlot)) } just Runs
            coEvery { performedExerciseDao.insert(capture(performedSlot)) } just Runs

            repository.addExerciseToActiveSession(
                sessionUuid = sessionId.toString(),
                trainingUuid = trainingId.toString(),
                exerciseUuid = exerciseId.toString(),
            )

            assertEquals(3, planSlot.captured.position)
            assertEquals(5, performedSlot.captured.position)
            assertEquals(trainingId, planSlot.captured.trainingUuid)
            assertEquals(sessionId, performedSlot.captured.sessionUuid)
            assertEquals(exerciseId, planSlot.captured.exerciseUuid)
            assertEquals(exerciseId, performedSlot.captured.exerciseUuid)
            assertEquals(null, planSlot.captured.planSets)
        }

    @Test
    fun `addExerciseToActiveSession on empty session uses position zero`() = runTest(dispatcher) {
        val trainingId = Uuid.random()
        val sessionId = Uuid.random()
        val exerciseId = Uuid.random()
        coEvery { exerciseDao.getById(exerciseId) } returns null
        coEvery { trainingExerciseDao.getMaxPosition(trainingId) } returns null
        coEvery { performedExerciseDao.getMaxPosition(sessionId) } returns null
        val planSlot = slot<TrainingExerciseEntity>()
        val performedSlot = slot<PerformedExerciseEntity>()
        coEvery { trainingExerciseDao.insert(capture(planSlot)) } just Runs
        coEvery { performedExerciseDao.insert(capture(performedSlot)) } just Runs

        repository.addExerciseToActiveSession(
            sessionUuid = sessionId.toString(),
            trainingUuid = trainingId.toString(),
            exerciseUuid = exerciseId.toString(),
        )

        assertEquals(0, planSlot.captured.position)
        assertEquals(0, performedSlot.captured.position)
    }

    @Test
    fun addExerciseToActiveSession_libraryExercise_doesNotFlipIsAdhoc() = runTest(dispatcher) {
        val trainingId = Uuid.random()
        val sessionId = Uuid.random()
        val libraryExerciseId = Uuid.random()
        coEvery { exerciseDao.getById(libraryExerciseId) } returns null
        coEvery { trainingExerciseDao.getMaxPosition(trainingId) } returns null
        coEvery { performedExerciseDao.getMaxPosition(sessionId) } returns null
        coEvery { trainingExerciseDao.insert(any<TrainingExerciseEntity>()) } just Runs
        coEvery { performedExerciseDao.insert(any<PerformedExerciseEntity>()) } just Runs

        repository.addExerciseToActiveSession(
            sessionUuid = sessionId.toString(),
            trainingUuid = trainingId.toString(),
            exerciseUuid = libraryExerciseId.toString(),
        )

        coVerify(exactly = 1) {
            trainingExerciseDao.insert(
                match<TrainingExerciseEntity> {
                    it.trainingUuid == trainingId &&
                        it.exerciseUuid == libraryExerciseId &&
                        it.planSets == null
                },
            )
        }
        coVerify(exactly = 0) { exerciseDao.update(any()) }
        coVerify(exactly = 0) { exerciseDao.graduateAdhocForTraining(any()) }
    }

    @Test
    fun addExerciseToActiveSession_existingLibraryWithLastAdhoc_preloadsPlan() =
        runTest(dispatcher) {
            val trainingId = Uuid.random()
            val sessionId = Uuid.random()
            val exerciseId = Uuid.random()
            val historyJson =
                "[{\"weight\":60.0,\"reps\":8,\"type\":\"WORK\"}]"
            coEvery { exerciseDao.getById(exerciseId) } returns adhocExerciseEntity(exerciseId)
                .copy(name = "Bench Press", isAdhoc = false, lastAdhocSets = historyJson)
            coEvery { trainingExerciseDao.getMaxPosition(trainingId) } returns null
            coEvery { performedExerciseDao.getMaxPosition(sessionId) } returns null
            val planSlot = slot<TrainingExerciseEntity>()
            coEvery { trainingExerciseDao.insert(capture(planSlot)) } just Runs
            coEvery { performedExerciseDao.insert(any<PerformedExerciseEntity>()) } just Runs

            val result = repository.addExerciseToActiveSession(
                sessionUuid = sessionId.toString(),
                trainingUuid = trainingId.toString(),
                exerciseUuid = exerciseId.toString(),
            )

            // The new training_exercise row carries the verbatim history JSON so the next
            // session reload sees the same plan that the in-memory state already shows.
            assertEquals(historyJson, planSlot.captured.planSets)
            // The repository surfaces the parsed list to the caller so the picker handler
            // can seed LiveExerciseUiModel.planSets without a second DB read.
            assertEquals(
                listOf(
                    PlanSetDataModel(
                        weight = 60.0,
                        reps = 8,
                        type = SetTypeDataModel.WORK,
                    ),
                ),
                result.planSets,
            )
        }

    @Test
    fun addExerciseToActiveSession_inlineCreatedNoHistory_planSetsNull() =
        runTest(dispatcher) {
            val trainingId = Uuid.random()
            val sessionId = Uuid.random()
            val exerciseId = Uuid.random()
            // Freshly inline-created exercise — is_adhoc = 1, no last_adhoc_sets yet.
            coEvery { exerciseDao.getById(exerciseId) } returns adhocExerciseEntity(exerciseId)
            coEvery { trainingExerciseDao.getMaxPosition(trainingId) } returns null
            coEvery { performedExerciseDao.getMaxPosition(sessionId) } returns null
            val planSlot = slot<TrainingExerciseEntity>()
            coEvery { trainingExerciseDao.insert(capture(planSlot)) } just Runs
            coEvery { performedExerciseDao.insert(any<PerformedExerciseEntity>()) } just Runs

            val result = repository.addExerciseToActiveSession(
                sessionUuid = sessionId.toString(),
                trainingUuid = trainingId.toString(),
                exerciseUuid = exerciseId.toString(),
            )

            assertEquals(null, planSlot.captured.planSets)
            assertEquals(null, result.planSets)
        }

    @Test
    fun addExerciseToActiveSession_libraryNoAdhocHistory_planSetsNull() =
        runTest(dispatcher) {
            val trainingId = Uuid.random()
            val sessionId = Uuid.random()
            val exerciseId = Uuid.random()
            // Library exercise that was only used in non-adhoc trainings → last_adhoc_sets
            // never written. The picker still surfaces it, and the insert correctly leaves
            // plan_sets null instead of fabricating a baseline.
            coEvery { exerciseDao.getById(exerciseId) } returns adhocExerciseEntity(exerciseId)
                .copy(name = "Squat", isAdhoc = false, lastAdhocSets = null)
            coEvery { trainingExerciseDao.getMaxPosition(trainingId) } returns null
            coEvery { performedExerciseDao.getMaxPosition(sessionId) } returns null
            val planSlot = slot<TrainingExerciseEntity>()
            coEvery { trainingExerciseDao.insert(capture(planSlot)) } just Runs
            coEvery { performedExerciseDao.insert(any<PerformedExerciseEntity>()) } just Runs

            val result = repository.addExerciseToActiveSession(
                sessionUuid = sessionId.toString(),
                trainingUuid = trainingId.toString(),
                exerciseUuid = exerciseId.toString(),
            )

            assertEquals(null, planSlot.captured.planSets)
            assertEquals(null, result.planSets)
        }

    @Test
    fun finishSession_adhocTraining_graduatesAllRows() = runTest(dispatcher) {
        val trainingId = Uuid.random()
        val sessionId = Uuid.random()
        val updatedSession = slot<SessionEntity>()
        coEvery { sessionDao.getById(sessionId) } returns SessionEntity(
            uuid = sessionId,
            trainingUuid = trainingId,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 1_000L,
            finishedAt = null,
        )
        coEvery { sessionDao.update(capture(updatedSession)) } just Runs
        coEvery { exerciseDao.graduateAdhocForTraining(trainingId) } just Runs
        coEvery { trainingDao.graduateTraining(trainingId) } just Runs

        val applied = repository.finishSessionAtomic(
            sessionUuid = sessionId.toString(),
            finishedAt = 2_000L,
            planUpdates = emptyList(),
        )

        assertTrue(applied)
        assertEquals(SessionStateEntity.FINISHED, updatedSession.captured.state)
        assertEquals(2_000L, updatedSession.captured.finishedAt)
        coVerify(exactly = 1) { exerciseDao.graduateAdhocForTraining(trainingId) }
        coVerify(exactly = 1) { trainingDao.graduateTraining(trainingId) }
    }

    @Test
    fun finishSession_withNewName_appliesNameInsideTransaction() = runTest(dispatcher) {
        val trainingId = Uuid.random()
        val sessionId = Uuid.random()
        coEvery { sessionDao.getById(sessionId) } returns SessionEntity(
            uuid = sessionId,
            trainingUuid = trainingId,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 1_000L,
            finishedAt = null,
        )

        val applied = repository.finishSessionAtomic(
            sessionUuid = sessionId.toString(),
            finishedAt = 2_000L,
            planUpdates = emptyList(),
            newTrainingName = "Renamed Push Day",
        )

        assertTrue(applied)
        // Order proves rename + graduation + state flip ran in the same transition block:
        // the updateName lands first, then graduation, then the FINISHED flip — Room
        // rolls back the whole batch if any step throws.
        coVerifyOrder {
            transition.invoke(any<suspend () -> Boolean>())
            trainingDao.updateName(trainingId, "Renamed Push Day")
            exerciseDao.graduateAdhocForTraining(trainingId)
            trainingDao.graduateTraining(trainingId)
            sessionDao.update(any())
        }
    }

    @Test
    fun finishSession_withNullName_doesNotCallUpdateName() = runTest(dispatcher) {
        val trainingId = Uuid.random()
        val sessionId = Uuid.random()
        coEvery { sessionDao.getById(sessionId) } returns SessionEntity(
            uuid = sessionId,
            trainingUuid = trainingId,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 1_000L,
            finishedAt = null,
        )

        val applied = repository.finishSessionAtomic(
            sessionUuid = sessionId.toString(),
            finishedAt = 2_000L,
            planUpdates = emptyList(),
            newTrainingName = null,
        )

        assertTrue(applied)
        coVerify(exactly = 0) { trainingDao.updateName(any(), any()) }
    }

    @Test
    fun finishSession_withNewName_failureBeforeStateFlipSkipsUpdate() = runTest(dispatcher) {
        val trainingId = Uuid.random()
        val sessionId = Uuid.random()
        coEvery { sessionDao.getById(sessionId) } returns SessionEntity(
            uuid = sessionId,
            trainingUuid = trainingId,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 1_000L,
            finishedAt = null,
        )
        // Simulate a write failure after the rename but before the state flip — in
        // production Room rolls back the entire transaction including the rename.
        coEvery { exerciseDao.graduateAdhocForTraining(trainingId) } throws
            IllegalStateException("graduation write failed")

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.finishSessionAtomic(
                    sessionUuid = sessionId.toString(),
                    finishedAt = 2_000L,
                    planUpdates = emptyList(),
                    newTrainingName = "Half-Applied",
                )
            }
        }

        // The state flip never ran — the exception bubbled out of the transition block,
        // so Room would not have committed the rename either.
        coVerify(exactly = 0) { sessionDao.update(any()) }
        coVerify(exactly = 0) { trainingDao.graduateTraining(any()) }
    }

    @Test
    fun `discardAdhocSession deletes session, training and only adhoc-flagged exercises`() =
        runTest(dispatcher) {
            val trainingId = Uuid.random()
            val sessionId = Uuid.random()
            val adhocExerciseId = Uuid.random()
            coEvery { exerciseDao.getAdhocExercisesForTraining(trainingId) } returns listOf(
                adhocExerciseEntity(adhocExerciseId),
            )

            repository.discardAdhocSession(
                sessionUuid = sessionId.toString(),
                trainingUuid = trainingId.toString(),
            )

            coVerify(exactly = 1) { sessionDao.delete(sessionId) }
            coVerify(exactly = 1) { trainingDao.permanentDelete(trainingId) }
            coVerify(exactly = 1) { exerciseDao.deleteByUuids(listOf(adhocExerciseId)) }
        }

    @Test
    fun `discardAdhocSession with no adhoc exercises skips the bulk delete`() =
        runTest(dispatcher) {
            val trainingId = Uuid.random()
            val sessionId = Uuid.random()
            coEvery { exerciseDao.getAdhocExercisesForTraining(trainingId) } returns emptyList()

            repository.discardAdhocSession(
                sessionUuid = sessionId.toString(),
                trainingUuid = trainingId.toString(),
            )

            coVerify(exactly = 1) { sessionDao.delete(sessionId) }
            coVerify(exactly = 1) { trainingDao.permanentDelete(trainingId) }
            coVerify(exactly = 0) { exerciseDao.deleteByUuids(any()) }
        }

    private fun adhocExerciseEntity(uuid: Uuid): ExerciseEntity = ExerciseEntity(
        uuid = uuid,
        name = "Inline-created",
        type = ExerciseTypeEntity.WEIGHTED,
        description = null,
        imagePath = null,
        archived = false,
        createdAt = 0L,
        archivedAt = null,
        lastAdhocSets = null,
        isAdhoc = true,
    )
}
