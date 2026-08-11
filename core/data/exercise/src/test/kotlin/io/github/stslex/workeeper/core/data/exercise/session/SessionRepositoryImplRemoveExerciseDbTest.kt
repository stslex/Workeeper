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
 * `removeExerciseFromSession` (v3 §6.1 "deleted: excluded, plan cleaned") against a real DB.
 * The predicates under test are the adhoc-lifecycle rules from `CLAUDE.md`: session
 * membership via `performed_exercise_table` decides the inline-exercise cleanup, and the
 * `is_adhoc` flag alone protects every library exercise.
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

        // Stranded `is_adhoc = 1` rows are invisible to every list — the cleanup is the
        // per-exercise sibling of the cancel cascade.
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
        // The ad-hoc shape, exactly as production builds it: `createAdhocSession` and
        // `addExerciseToActiveSession` BOTH insert `training_exercise_table` rows even for
        // an ad-hoc training, and that table holds `onDelete = RESTRICT` on exercise_uuid.
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

        // The removal must SUCCEED regardless: the orphan cleanup may never take the whole
        // transaction down with it, because that silently resurrects the exercise the user
        // deleted once the undo window closes.
        assertTrue(env.performedExerciseDao.getBySession(session.uuid).isEmpty())
        assertTrue(env.setDao.getByPerformedExercise(performed.uuid).isEmpty())
        // Still referenced by a plan row, so it is NOT an orphan and must survive.
        assertNotNull(env.exerciseDao.getById(inline.uuid))
    }

    @Test
    fun `an adhoc session cleans the plan row and then the inline exercise`() = runTest {
        // The production path after the handler stopped exempting ad-hoc sessions: the pair
        // row goes first, so the orphan cleanup finds nothing referencing the exercise and
        // completes instead of tripping the FK.
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
        // The hardened predicate's own case: session membership gone, but a DIFFERENT
        // training still plans it, so it survives (and the FK is never provoked).
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
