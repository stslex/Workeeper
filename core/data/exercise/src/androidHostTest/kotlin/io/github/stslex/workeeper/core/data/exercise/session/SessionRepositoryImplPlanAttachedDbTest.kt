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
 * The plan-attached axis at the repository boundary. Cases come in one-off/attached pairs, so
 * a regression that ignores the flag fails one side. See v3-redesign-spec.md §6.2.
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

            assertTrue(env.trainingExerciseDao.getByTraining(training.uuid).isEmpty())
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

        // A one-off never occupies a plan slot, so the attached exercise takes position 0.
        val plan = env.trainingExerciseDao.getByTraining(training.uuid).single()
        assertEquals(attached.uuid, plan.exerciseUuid)
        assertEquals(0, plan.position)
        val performed = env.performedExerciseDao.getBySession(session.uuid).sortedBy { it.position }
        assertEquals(listOf(0, 1), performed.map { it.position })
        assertEquals(listOf(oneOff.uuid, attached.uuid), performed.map { it.exerciseUuid })
    }

    @Test
    fun `finishSessionAtomic graduates an inline exercise added as a one-off`() = runTest {
        // Regression: a plan-table join stranded one-offs at is_adhoc = 1 forever.
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
        assertTrue(env.trainingExerciseDao.getByTraining(training.uuid).isEmpty())
    }

    @Test
    fun `finishSessionAtomic leaves an exercise outside the session untouched`() = runTest {
        // Session membership is the predicate, so an unperformed ad-hoc exercise stays ad-hoc.
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
        // The graduation regression's shape on the cancel path: a one-off survived teardown.
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

        // The `is_adhoc` half of the predicate is what spares the library row next to it.
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
        // A false finish leaves the session active, so the discarded sets must still be there.
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
