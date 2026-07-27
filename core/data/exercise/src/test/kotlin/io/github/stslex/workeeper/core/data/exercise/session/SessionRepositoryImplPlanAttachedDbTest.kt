// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

/**
 * The plan-attached axis (v3 §6.2) at the repository boundary.
 *
 * Every test here exists because the axis is encoded as the **absence of a
 * `training_exercise_table` row** rather than as a column, which makes it invisible to any
 * assertion that only reads the exercise. The pairs below are deliberately symmetric: each
 * one-off case has an attached counterpart, so a regression that ignores the flag entirely
 * fails one side of the pair rather than passing both.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class SessionRepositoryImplPlanAttachedDbTest {

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
    fun `attachToPlan false writes the performed row but leaves the training plan untouched`() =
        runTest {
            val training = env.seedTraining(name = "Push Day")
            val exercise = env.seedExercise(name = "Cable fly")
            val session = env.seedSession(trainingUuid = training.uuid)

            val result = repository.addExerciseToActiveSession(
                sessionUuid = session.uuid.toString(),
                trainingUuid = training.uuid.toString(),
                exerciseUuid = exercise.uuid.toString(),
                attachToPlan = false,
            )

            // The saved template is the thing a one-off must not touch.
            assertTrue(env.trainingExerciseDao.getByTraining(training.uuid).isEmpty())
            // ...but the exercise is fully part of this session.
            val performed = env.performedExerciseDao.getBySession(session.uuid).single()
            assertEquals(exercise.uuid, performed.exerciseUuid)
            assertEquals(result.performedExerciseUuid, performed.uuid.toString())
            assertFalse(result.isPlanAttached)
        }

    @Test
    fun `attachToPlan true still writes both rows`() = runTest {
        val training = env.seedTraining(name = "Push Day")
        val exercise = env.seedExercise(name = "Cable fly")
        val session = env.seedSession(trainingUuid = training.uuid)

        val result = repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = exercise.uuid.toString(),
            attachToPlan = true,
        )

        assertEquals(
            exercise.uuid,
            env.trainingExerciseDao.getByTraining(training.uuid).single().exerciseUuid,
        )
        assertNotNull(env.performedExerciseDao.getBySession(session.uuid).single())
        assertTrue(result.isPlanAttached)
    }

    @Test
    fun `a one-off does not consume a plan position from a later attached add`() = runTest {
        val training = env.seedTraining(name = "Push Day")
        val oneOff = env.seedExercise(name = "One-off")
        val attached = env.seedExercise(name = "Attached")
        val session = env.seedSession(trainingUuid = training.uuid)

        repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = oneOff.uuid.toString(),
            attachToPlan = false,
        )
        repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = attached.uuid.toString(),
            attachToPlan = true,
        )

        // The plan sees only the attached exercise, and it takes position 0 — the one-off
        // never occupied a plan slot to be skipped over.
        val plan = env.trainingExerciseDao.getByTraining(training.uuid).single()
        assertEquals(attached.uuid, plan.exerciseUuid)
        assertEquals(0, plan.position)
        // Performed positions are unaffected: both exercises are in the session, in order.
        val performed = env.performedExerciseDao.getBySession(session.uuid).sortedBy { it.position }
        assertEquals(listOf(0, 1), performed.map { it.position })
        assertEquals(listOf(oneOff.uuid, attached.uuid), performed.map { it.exerciseUuid })
    }

    @Test
    fun `finishSessionAtomic graduates an inline exercise added as a one-off`() = runTest {
        // The regression this pins: graduation used to join through training_exercise_table,
        // so a one-off inline exercise — which has no plan row by construction — would stay
        // is_adhoc = 1 forever and be filtered out of every user-facing library list.
        val training = env.seedTraining(name = "Push Day")
        val inlineOneOff = env.seedExercise(name = "Inline one-off", isAdhoc = true)
        val session = env.seedSession(trainingUuid = training.uuid)
        repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = inlineOneOff.uuid.toString(),
            attachToPlan = false,
        )

        val applied = repository.finishSessionAtomic(
            sessionUuid = session.uuid.toString(),
            finishedAt = 5_000L,
            planUpdates = emptyList(),
            newTrainingName = null,
        )

        assertTrue(applied)
        assertEquals(false, env.exerciseDao.getById(inlineOneOff.uuid)?.isAdhoc)
        // Graduation must not have resurrected a plan row.
        assertTrue(env.trainingExerciseDao.getByTraining(training.uuid).isEmpty())
    }

    @Test
    fun `finishSessionAtomic leaves an exercise outside the session untouched`() = runTest {
        // The other direction: session membership is the predicate, so an ad-hoc exercise
        // that was never performed here must not graduate.
        val training = env.seedTraining(name = "Push Day")
        val performedInline = env.seedExercise(name = "Performed", isAdhoc = true)
        val strangerInline = env.seedExercise(name = "Stranger", isAdhoc = true)
        val session = env.seedSession(trainingUuid = training.uuid)
        env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = performedInline.uuid)

        repository.finishSessionAtomic(
            sessionUuid = session.uuid.toString(),
            finishedAt = 5_000L,
            planUpdates = emptyList(),
            newTrainingName = null,
        )

        assertEquals(false, env.exerciseDao.getById(performedInline.uuid)?.isAdhoc)
        assertEquals(true, env.exerciseDao.getById(strangerInline.uuid)?.isAdhoc)
    }

    @Test
    fun `discardAdhocSession cascades an inline exercise added as a one-off`() = runTest {
        // Same shape as the graduation regression, on the cancel path: with the old
        // training_exercise_table join a one-off inline exercise survived teardown as an
        // orphan is_adhoc = 1 row.
        val training = env.seedTraining(name = "Quick start", isAdhoc = true)
        val inlineOneOff = env.seedExercise(name = "Inline one-off", isAdhoc = true)
        val libraryPick = env.seedExercise(name = "Library pick", isAdhoc = false)
        val session = env.seedSession(trainingUuid = training.uuid)
        repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = inlineOneOff.uuid.toString(),
            attachToPlan = false,
        )
        repository.addExerciseToActiveSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            exerciseUuid = libraryPick.uuid.toString(),
            attachToPlan = false,
        )

        repository.discardAdhocSession(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
        )

        // The inline row is gone; the library row it sat next to is untouched. The flag half
        // of the defence-in-depth predicate is what separates them.
        assertEquals(null, env.exerciseDao.getById(inlineOneOff.uuid))
        assertNotNull(env.exerciseDao.getById(libraryPick.uuid))
    }

    @Test
    fun `finishSessionAtomic deletes the discarded sets inside the transaction`() = runTest {
        val training = env.seedTraining(name = "Push Day")
        val exercise = env.seedExercise(name = "Bench")
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = exercise.uuid)
        val filled = env.seedSet(performedExerciseUuid = performed.uuid, position = 0, reps = 5)
        val unfilled = env.seedSet(performedExerciseUuid = performed.uuid, position = 1, reps = 0)

        val applied = repository.finishSessionAtomic(
            sessionUuid = session.uuid.toString(),
            finishedAt = 5_000L,
            planUpdates = emptyList(),
            newTrainingName = null,
            discardedSetUuids = listOf(unfilled.uuid.toString()),
        )

        assertTrue(applied)
        val remaining = env.setDao.getByPerformedExercise(performed.uuid).map { it.uuid }
        assertEquals(listOf(filled.uuid), remaining)
    }

    @Test
    fun `a failed finish does not delete the discarded sets`() = runTest {
        // The rollback property. `finishSessionAtomic` returns false for a missing session,
        // the caller reports a failed finish and leaves the session active — so the sets must
        // still be there. Deleting them before the transaction would destroy real rows on
        // behalf of a finish that never happened.
        val training = env.seedTraining(name = "Push Day")
        val exercise = env.seedExercise(name = "Bench")
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = exercise.uuid)
        val unfilled = env.seedSet(performedExerciseUuid = performed.uuid, position = 0, reps = 0)

        val applied = repository.finishSessionAtomic(
            sessionUuid = Uuid.random().toString(),
            finishedAt = 5_000L,
            planUpdates = emptyList(),
            newTrainingName = null,
            discardedSetUuids = listOf(unfilled.uuid.toString()),
        )

        assertFalse(applied)
        assertEquals(
            listOf(unfilled.uuid),
            env.setDao.getByPerformedExercise(performed.uuid).map { it.uuid },
        )
    }
}
