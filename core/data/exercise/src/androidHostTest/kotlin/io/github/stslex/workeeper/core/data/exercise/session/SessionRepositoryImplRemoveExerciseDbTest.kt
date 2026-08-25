// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

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

/**
 * `removeExerciseFromSession` against a real DB: session membership decides the inline-exercise
 * cleanup, and `is_adhoc` alone protects every library exercise.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class SessionRepositoryImplRemoveExerciseDbTest {

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
    fun `removes sets and the performed row`() = runTest {
        val training = env.seedTraining()
        val exercise = env.seedExercise()
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = exercise.uuid)
        env.seedSet(performedExerciseUuid = performed.uuid, position = 0)
        env.seedSet(performedExerciseUuid = performed.uuid, position = 1)

        repository.removeExerciseFromSession(
            performedExerciseUuid = performed.uuid.toString(),
            exerciseUuid = exercise.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            removeFromPlan = false,
        )

        assertTrue(env.setDao.getByPerformedExercise(performed.uuid).isEmpty())
        assertTrue(env.performedExerciseDao.getBySession(session.uuid).isEmpty())
    }

    @Test
    fun `removeFromPlan deletes exactly the pair's plan row`() = runTest {
        val training = env.seedTraining()
        val exercise = env.seedExercise()
        val other = env.seedExercise(name = "Other")
        env.seedTrainingExercise(trainingUuid = training.uuid, exerciseUuid = exercise.uuid, position = 0)
        env.seedTrainingExercise(trainingUuid = training.uuid, exerciseUuid = other.uuid, position = 1)
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = exercise.uuid)

        repository.removeExerciseFromSession(
            performedExerciseUuid = performed.uuid.toString(),
            exerciseUuid = exercise.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            removeFromPlan = true,
        )

        val remaining = env.trainingExerciseDao.getByTraining(training.uuid)
        assertEquals(listOf(other.uuid), remaining.map { it.exerciseUuid })
    }

    @Test
    fun `keep-in-plan leaves the plan row alone`() = runTest {
        val training = env.seedTraining()
        val exercise = env.seedExercise()
        env.seedTrainingExercise(trainingUuid = training.uuid, exerciseUuid = exercise.uuid)
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = exercise.uuid)

        repository.removeExerciseFromSession(
            performedExerciseUuid = performed.uuid.toString(),
            exerciseUuid = exercise.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            removeFromPlan = false,
        )

        assertEquals(1, env.trainingExerciseDao.getByTraining(training.uuid).size)
    }

    @Test
    fun `an inline-created exercise with no other session membership is cleaned up`() = runTest {
        val training = env.seedTraining()
        val inline = env.seedExercise(isAdhoc = true)
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = inline.uuid)

        repository.removeExerciseFromSession(
            performedExerciseUuid = performed.uuid.toString(),
            exerciseUuid = inline.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            removeFromPlan = false,
        )

        // A stranded `is_adhoc = 1` row would be invisible to every user-facing list.
        assertNull(env.exerciseDao.getById(inline.uuid))
    }

    @Test
    fun `an inline-created exercise still performed elsewhere survives`() = runTest {
        val training = env.seedTraining()
        val inline = env.seedExercise(isAdhoc = true)
        val session = env.seedSession(trainingUuid = training.uuid)
        val otherSession = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = inline.uuid)
        env.seedPerformed(sessionUuid = otherSession.uuid, exerciseUuid = inline.uuid)

        repository.removeExerciseFromSession(
            performedExerciseUuid = performed.uuid.toString(),
            exerciseUuid = inline.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            removeFromPlan = false,
        )

        assertNotNull(env.exerciseDao.getById(inline.uuid))
    }

    @Test
    fun `REPRO an adhoc-session inline exercise whose plan row survives the removal`() = runTest {
        // Both ad-hoc write paths insert plan rows, and that table is `onDelete = RESTRICT`.
        val training = env.seedTraining(isAdhoc = true)
        val inline = env.seedExercise(isAdhoc = true)
        env.seedTrainingExercise(trainingUuid = training.uuid, exerciseUuid = inline.uuid)
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = inline.uuid)
        env.seedSet(performedExerciseUuid = performed.uuid)

        repository.removeExerciseFromSession(
            performedExerciseUuid = performed.uuid.toString(),
            exerciseUuid = inline.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            removeFromPlan = false,
        )

        // GUARD: the orphan cleanup may never fail the removal — that resurrects the exercise.
        assertTrue(env.performedExerciseDao.getBySession(session.uuid).isEmpty())
        assertTrue(env.setDao.getByPerformedExercise(performed.uuid).isEmpty())
        assertNotNull(env.exerciseDao.getById(inline.uuid))
    }

    @Test
    fun `an adhoc session cleans the plan row and then the inline exercise`() = runTest {
        // The pair row goes first, so the orphan cleanup finds no reference and never trips the FK.
        val training = env.seedTraining(isAdhoc = true)
        val inline = env.seedExercise(isAdhoc = true)
        env.seedTrainingExercise(trainingUuid = training.uuid, exerciseUuid = inline.uuid)
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = inline.uuid)

        repository.removeExerciseFromSession(
            performedExerciseUuid = performed.uuid.toString(),
            exerciseUuid = inline.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            removeFromPlan = true,
        )

        assertTrue(env.trainingExerciseDao.getByTraining(training.uuid).isEmpty())
        assertTrue(env.performedExerciseDao.getBySession(session.uuid).isEmpty())
        assertNull(env.exerciseDao.getById(inline.uuid))
    }

    @Test
    fun `an inline exercise planned by another training is not an orphan`() = runTest {
        // Session membership gone, but a different training still plans it, so it survives.
        val training = env.seedTraining()
        val otherTraining = env.seedTraining(name = "Other")
        val inline = env.seedExercise(isAdhoc = true)
        env.seedTrainingExercise(trainingUuid = otherTraining.uuid, exerciseUuid = inline.uuid)
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = inline.uuid)

        repository.removeExerciseFromSession(
            performedExerciseUuid = performed.uuid.toString(),
            exerciseUuid = inline.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            removeFromPlan = true,
        )

        assertNotNull(env.exerciseDao.getById(inline.uuid))
        assertEquals(1, env.trainingExerciseDao.getByTraining(otherTraining.uuid).size)
    }

    @Test
    fun `a library exercise is never deleted by the removal`() = runTest {
        val training = env.seedTraining()
        val library = env.seedExercise(isAdhoc = false)
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = library.uuid)

        repository.removeExerciseFromSession(
            performedExerciseUuid = performed.uuid.toString(),
            exerciseUuid = library.uuid.toString(),
            trainingUuid = training.uuid.toString(),
            removeFromPlan = true,
        )

        assertNotNull(env.exerciseDao.getById(library.uuid))
    }
}
