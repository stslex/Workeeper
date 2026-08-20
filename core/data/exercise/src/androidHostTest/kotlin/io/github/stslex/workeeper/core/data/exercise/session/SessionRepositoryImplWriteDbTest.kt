// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class SessionRepositoryImplWriteDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: SessionRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        repository = SessionRepositoryImpl(
            dao = env.sessionDao,
            performedExerciseDao = env.performedExerciseDao,
            setDao = env.setDao,
            trainingDao = env.trainingDao,
            exerciseDao = env.exerciseDao,
            trainingExerciseDao = env.trainingExerciseDao,
            transition = env.transition,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `startSession persists an in-progress session referencing the training`() = runTest {
        val training = env.seedTraining()

        val result = repository.startSession(training.uuid.toString())

        val persisted = env.sessionDao.getById(Uuid.parse(result.uuid))
        assertNotNull(persisted)
        assertEquals(SessionStateEntity.IN_PROGRESS, persisted?.state)
        assertEquals(training.uuid, persisted?.trainingUuid)
        assertNull(persisted?.finishedAt)
        // startedAt is wall-clock; assert it's a positive timestamp.
        assertTrue((persisted?.startedAt ?: 0L) > 0L)
    }

    @Test
    fun `startSessionWithExercises seeds performed_exercise rows in one transaction`() = runTest {
        val training = env.seedTraining()
        val exerciseA = env.seedExercise(name = "A")
        val exerciseB = env.seedExercise(name = "B")

        val result = repository.startSessionWithExercises(
            trainingUuid = training.uuid.toString(),
            exerciseUuids = listOf(
                exerciseA.uuid.toString() to 0,
                exerciseB.uuid.toString() to 1,
            ),
        )

        val sessionId = Uuid.parse(result.uuid)
        val sessionRow = env.sessionDao.getById(sessionId)
        assertNotNull(sessionRow)
        assertEquals(SessionStateEntity.IN_PROGRESS, sessionRow?.state)

        val performed = env.performedExerciseDao.getBySession(sessionId)
            .sortedBy { it.position }
        assertEquals(listOf(0, 1), performed.map { it.position })
        assertEquals(listOf(exerciseA.uuid, exerciseB.uuid), performed.map { it.exerciseUuid })
        assertTrue(performed.none { it.skipped })
    }

    @Test
    fun `startSessionWithExercises with empty list still creates session and writes no performed`() = runTest {
        val training = env.seedTraining()

        val result = repository.startSessionWithExercises(
            trainingUuid = training.uuid.toString(),
            exerciseUuids = emptyList(),
        )

        val sessionId = Uuid.parse(result.uuid)
        assertNotNull(env.sessionDao.getById(sessionId))
        assertTrue(env.performedExerciseDao.getBySession(sessionId).isEmpty())
    }

    @Test
    fun `resumeSession returns the session model when it is in-progress`() = runTest {
        val training = env.seedTraining()
        val active = env.seedSession(trainingUuid = training.uuid)

        val result = repository.resumeSession(active.uuid.toString())

        assertNotNull(result)
        assertEquals(active.uuid.toString(), result?.uuid)
    }

    @Test
    fun `resumeSession returns null for a finished session`() = runTest {
        val training = env.seedTraining()
        val finished = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 100L,
        )

        assertNull(repository.resumeSession(finished.uuid.toString()))
    }

    @Test
    fun `resumeSession returns null for an unknown session uuid`() = runTest {
        assertNull(repository.resumeSession(Uuid.random().toString()))
    }

    @Test
    fun `finishSession flips state to FINISHED and writes finished_at`() = runTest {
        val training = env.seedTraining()
        val active = env.seedSession(trainingUuid = training.uuid)

        repository.finishSession(active.uuid.toString(), finishedAt = 9_000L)

        val updated = env.sessionDao.getById(active.uuid)
        assertEquals(SessionStateEntity.FINISHED, updated?.state)
        assertEquals(9_000L, updated?.finishedAt)
    }

    @Test
    fun `finishSession is a no-op when the session is missing`() = runTest {
        val training = env.seedTraining()
        // Seed an unrelated session so we can verify untouched state.
        val other = env.seedSession(trainingUuid = training.uuid)

        repository.finishSession(Uuid.random().toString(), finishedAt = 500L)

        val unaffected = env.sessionDao.getById(other.uuid)
        assertEquals(SessionStateEntity.IN_PROGRESS, unaffected?.state)
        assertNull(unaffected?.finishedAt)
    }

    @Test
    fun `deleteSession removes the row and cascades performed_exercise plus sets`() = runTest {
        val training = env.seedTraining()
        val exercise = env.seedExercise()
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = exercise.uuid,
        )
        env.seedSet(performedExerciseUuid = performed.uuid, position = 0)

        repository.deleteSession(session.uuid.toString())

        assertNull(env.sessionDao.getById(session.uuid))
        // session_table cascades performed_exercise_table and (transitively) set_table.
        assertTrue(env.performedExerciseDao.getBySession(session.uuid).isEmpty())
        assertTrue(env.setDao.getByPerformedExercise(performed.uuid).isEmpty())
    }

    @Test
    fun `createAdhocSession inserts an isAdhoc training plus session and seeds plan and performed rows`() =
        runTest {
            val firstExercise = env.seedExercise(name = "Inline-A", isAdhoc = true)
            val secondExercise = env.seedExercise(name = "Inline-B", isAdhoc = true)

            val result = repository.createAdhocSession(
                name = "Quick Push",
                exerciseUuids = listOf(
                    firstExercise.uuid.toString(),
                    secondExercise.uuid.toString(),
                ),
            )

            val trainingId = Uuid.parse(result.trainingUuid)
            val sessionId = Uuid.parse(result.sessionUuid)
            val training = env.trainingDao.getById(trainingId)
            assertNotNull(training)
            assertEquals("Quick Push", training?.name)
            assertEquals(true, training?.isAdhoc)

            val session = env.sessionDao.getById(sessionId)
            assertNotNull(session)
            assertEquals(SessionStateEntity.IN_PROGRESS, session?.state)
            assertEquals(trainingId, session?.trainingUuid)

            val plan = env.trainingExerciseDao.getByTraining(trainingId)
                .sortedBy { it.position }
            assertEquals(
                listOf(firstExercise.uuid, secondExercise.uuid),
                plan.map { it.exerciseUuid },
            )
            assertTrue(plan.all { it.planSets == null })

            val performed = env.performedExerciseDao.getBySession(sessionId)
                .sortedBy { it.position }
            assertEquals(
                listOf(firstExercise.uuid, secondExercise.uuid),
                performed.map { it.exerciseUuid },
            )
        }

    @Test
    fun `createAdhocSession with empty exercise list still produces training and session rows`() =
        runTest {
            val result = repository.createAdhocSession(name = "Blank", exerciseUuids = emptyList())

            val trainingId = Uuid.parse(result.trainingUuid)
            val sessionId = Uuid.parse(result.sessionUuid)
            assertEquals(true, env.trainingDao.getById(trainingId)?.isAdhoc)
            assertEquals(SessionStateEntity.IN_PROGRESS, env.sessionDao.getById(sessionId)?.state)
            assertTrue(env.trainingExerciseDao.getByTraining(trainingId).isEmpty())
            assertTrue(env.performedExerciseDao.getBySession(sessionId).isEmpty())
        }

    @Test
    fun `addExerciseToActiveSession appends rows at the next position in plan and performed`() =
        runTest {
            val training = env.seedTraining(isAdhoc = true)
            val firstExercise = env.seedExercise(name = "First")
            val nextExercise = env.seedExercise(name = "Next")
            val session = env.seedSession(trainingUuid = training.uuid)
            // Seed the first exercise as the existing position-0 attachment to mimic what
            // createAdhocSession would have written.
            env.seedTrainingExercise(
                trainingUuid = training.uuid,
                exerciseUuid = firstExercise.uuid,
                position = 0,
            )
            env.seedPerformed(
                sessionUuid = session.uuid,
                exerciseUuid = firstExercise.uuid,
                position = 0,
            )

            val result = repository.addExerciseToActiveSession(
                sessionUuid = session.uuid.toString(),
                trainingUuid = training.uuid.toString(),
                exerciseUuid = nextExercise.uuid.toString(),
            )

            val plan = env.trainingExerciseDao.getByTraining(training.uuid)
                .sortedBy { it.position }
            assertEquals(listOf(0, 1), plan.map { it.position })
            assertEquals(nextExercise.uuid, plan[1].exerciseUuid)
            assertNull(plan[1].planSets)

            val performed = env.performedExerciseDao.getBySession(session.uuid)
                .sortedBy { it.position }
            assertEquals(listOf(0, 1), performed.map { it.position })
            assertEquals(
                result.performedExerciseUuid,
                performed.first { it.position == 1 }.uuid.toString(),
            )
            assertNull(result.planSets)
        }

    @Test
    fun `addExerciseToActiveSession on an empty session uses position zero`() = runTest {
        val training = env.seedTraining(isAdhoc = true)
        val exercise = env.seedExercise(name = "Solo")
        val session = env.seedSession(trainingUuid = training.uuid)

        repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = exercise.uuid.toString(),
        )

        val plan = env.trainingExerciseDao.getByTraining(training.uuid).single()
        val performed = env.performedExerciseDao.getBySession(session.uuid).single()
        assertEquals(0, plan.position)
        assertEquals(0, performed.position)
        assertEquals(exercise.uuid, plan.exerciseUuid)
        assertEquals(exercise.uuid, performed.exerciseUuid)
    }

    @Test
    fun `addExerciseToActiveSession seeds plan_sets from last_adhoc_sets when present`() = runTest {
        val training = env.seedTraining(isAdhoc = true)
        val historyJson = """[{"weight":60.0,"reps":8,"type":"WORK"}]"""
        val exercise = env.seedExercise(name = "Bench Press", lastAdhocSets = historyJson)
        val session = env.seedSession(trainingUuid = training.uuid)

        val result = repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = exercise.uuid.toString(),
        )

        val plan = env.trainingExerciseDao.getByTraining(training.uuid).single()
        // The new training_exercise row carries the verbatim history JSON so the next session
        // reload sees the same plan.
        assertEquals(historyJson, plan.planSets)
        assertNotNull(result.planSets)
        assertEquals(1, result.planSets?.size)
        assertEquals(60.0, result.planSets?.first()?.weight)
        assertEquals(8, result.planSets?.first()?.reps)
    }

    @Test
    fun `addExerciseToActiveSession leaves plan_sets null when exercise has no history`() = runTest {
        val training = env.seedTraining(isAdhoc = true)
        val freshExercise = env.seedExercise(name = "Fresh", isAdhoc = true)
        val session = env.seedSession(trainingUuid = training.uuid)

        val result = repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = freshExercise.uuid.toString(),
        )

        val plan = env.trainingExerciseDao.getByTraining(training.uuid).single()
        assertNull(plan.planSets)
        assertNull(result.planSets)
    }

    @Test
    fun `addExerciseToActiveSession does not flip is_adhoc on the picked library exercise`() = runTest {
        val training = env.seedTraining(isAdhoc = true)
        val libraryExercise = env.seedExercise(name = "Library", isAdhoc = false)
        val session = env.seedSession(trainingUuid = training.uuid)

        repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = libraryExercise.uuid.toString(),
        )

        // The library exercise must not be flipped to is_adhoc = 1; that flag drives the
        // discardAdhocSession cleanup predicate.
        val reloaded = env.exerciseDao.getById(libraryExercise.uuid)
        assertEquals(false, reloaded?.isAdhoc)
    }

    @Test
    fun `discardAdhocSession deletes session, training, and only adhoc-flagged exercises for that training`() =
        runTest {
            val adhocTraining = env.seedTraining(name = "Adhoc", isAdhoc = true)
            val adhocOnly = env.seedExercise(name = "AdhocOnly", isAdhoc = true)
            val libraryPicked = env.seedExercise(name = "Library", isAdhoc = false)
            val session = env.seedSession(trainingUuid = adhocTraining.uuid)
            // Both exercises participate in this ad-hoc training.
            env.seedTrainingExercise(
                trainingUuid = adhocTraining.uuid,
                exerciseUuid = adhocOnly.uuid,
                position = 0,
            )
            env.seedTrainingExercise(
                trainingUuid = adhocTraining.uuid,
                exerciseUuid = libraryPicked.uuid,
                position = 1,
            )
            env.seedPerformed(
                sessionUuid = session.uuid,
                exerciseUuid = adhocOnly.uuid,
                position = 0,
            )
            env.seedPerformed(
                sessionUuid = session.uuid,
                exerciseUuid = libraryPicked.uuid,
                position = 1,
            )

            repository.discardAdhocSession(
                sessionUuid = session.uuid.toString(),
                trainingUuid = adhocTraining.uuid.toString(),
            )

            // Session + training are gone (and their cascaded children).
            assertNull(env.sessionDao.getById(session.uuid))
            assertNull(env.trainingDao.getById(adhocTraining.uuid))
            // The is_adhoc=1 inline-created exercise is gone.
            assertNull(env.exerciseDao.getById(adhocOnly.uuid))
            // The library exercise (is_adhoc=0) is preserved by the defence-in-depth predicate.
            assertNotNull(env.exerciseDao.getById(libraryPicked.uuid))
        }

    @Test
    fun `discardAdhocSession with no adhoc-flagged exercises just clears session and training`() =
        runTest {
            val adhocTraining = env.seedTraining(name = "Adhoc", isAdhoc = true)
            val library = env.seedExercise(name = "Library", isAdhoc = false)
            val session = env.seedSession(trainingUuid = adhocTraining.uuid)
            env.seedTrainingExercise(
                trainingUuid = adhocTraining.uuid,
                exerciseUuid = library.uuid,
                position = 0,
            )

            repository.discardAdhocSession(
                sessionUuid = session.uuid.toString(),
                trainingUuid = adhocTraining.uuid.toString(),
            )

            assertNull(env.sessionDao.getById(session.uuid))
            assertNull(env.trainingDao.getById(adhocTraining.uuid))
            assertNotNull(env.exerciseDao.getById(library.uuid))
        }
}
