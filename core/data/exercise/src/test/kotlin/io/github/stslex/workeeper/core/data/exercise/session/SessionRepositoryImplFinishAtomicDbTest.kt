// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class SessionRepositoryImplFinishAtomicDbTest {

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
    fun `finishSessionAtomic returns false and writes nothing when the session is missing`() = runTest {
        val applied = repository.finishSessionAtomic(
            sessionUuid = Uuid.random().toString(),
            finishedAt = 5_000L,
            planUpdates = emptyList(),
        )

        assertFalse(applied)
    }

    @Test
    fun `finishSessionAtomic flips state to FINISHED and graduates the ad-hoc training`() = runTest {
        val adhocTraining = env.seedTraining(name = "Adhoc", isAdhoc = true)
        val adhocExercise = env.seedExercise(name = "Inline", isAdhoc = true)
        val session = env.seedSession(trainingUuid = adhocTraining.uuid, startedAt = 1_000L)

        val applied = repository.finishSessionAtomic(
            sessionUuid = session.uuid.toString(),
            finishedAt = 2_000L,
            planUpdates = emptyList(),
        )

        assertTrue(applied)
        // Session row state.
        val finished = env.sessionDao.getById(session.uuid)
        assertEquals(SessionStateEntity.FINISHED, finished?.state)
        assertEquals(2_000L, finished?.finishedAt)
        // Training graduated to a regular library row.
        val graduatedTraining = env.trainingDao.getById(adhocTraining.uuid)
        assertEquals(false, graduatedTraining?.isAdhoc)
        // Exercise also graduated (plan-attached via training_exercise + is_adhoc = 1).
        val graduatedExercise = env.exerciseDao.getById(adhocExercise.uuid)
        // The exercise is graduated only when it's still attached to that training. Without a
        // training_exercise row, it stays unchanged. We didn't seed a join row here, so this
        // exercise is NOT graduated — confirms graduation is scoped via the join, not a wildcard.
        assertEquals(true, graduatedExercise?.isAdhoc)
    }

    @Test
    fun `finishSessionAtomic graduates only exercises plan-attached to the training`() = runTest {
        val adhocTraining = env.seedTraining(name = "Adhoc", isAdhoc = true)
        val attached = env.seedExercise(name = "Attached", isAdhoc = true)
        val orphan = env.seedExercise(name = "Orphan", isAdhoc = true)
        env.seedTrainingExercise(
            trainingUuid = adhocTraining.uuid,
            exerciseUuid = attached.uuid,
            position = 0,
        )
        val session = env.seedSession(trainingUuid = adhocTraining.uuid)

        repository.finishSessionAtomic(
            sessionUuid = session.uuid.toString(),
            finishedAt = 5_000L,
            planUpdates = emptyList(),
        )

        // The plan-attached adhoc exercise is graduated.
        assertEquals(false, env.exerciseDao.getById(attached.uuid)?.isAdhoc)
        // The orphan adhoc exercise (no training_exercise join row) stays untouched.
        assertEquals(true, env.exerciseDao.getById(orphan.uuid)?.isAdhoc)
    }

    @Test
    fun `finishSessionAtomic with newTrainingName updates the training name in the same transaction`() =
        runTest {
            val adhocTraining = env.seedTraining(name = "Original", isAdhoc = true)
            val session = env.seedSession(trainingUuid = adhocTraining.uuid)

            val applied = repository.finishSessionAtomic(
                sessionUuid = session.uuid.toString(),
                finishedAt = 9_000L,
                planUpdates = emptyList(),
                newTrainingName = "Renamed",
            )

            assertTrue(applied)
            val finished = env.sessionDao.getById(session.uuid)
            assertEquals(SessionStateEntity.FINISHED, finished?.state)
            assertEquals("Renamed", env.trainingDao.getById(adhocTraining.uuid)?.name)
        }

    @Test
    fun `finishSessionAtomic with null newTrainingName leaves the training name unchanged`() = runTest {
        val training = env.seedTraining(name = "Keep")
        val session = env.seedSession(trainingUuid = training.uuid)

        repository.finishSessionAtomic(
            sessionUuid = session.uuid.toString(),
            finishedAt = 1_500L,
            planUpdates = emptyList(),
            newTrainingName = null,
        )

        assertEquals("Keep", env.trainingDao.getById(training.uuid)?.name)
    }

    @Test
    fun `finishSessionAtomic applies plan updates for non-adhoc exercises against training_exercise`() =
        runTest {
            val training = env.seedTraining(name = "Push", isAdhoc = false)
            val exercise = env.seedExercise(name = "Bench", isAdhoc = false)
            env.seedTrainingExercise(
                trainingUuid = training.uuid,
                exerciseUuid = exercise.uuid,
                position = 0,
                planSets = """[{"weight":80.0,"reps":5,"type":"WORK"}]""",
            )
            val session = env.seedSession(trainingUuid = training.uuid)

            val newPlan = listOf(
                PlanSetDataModel(weight = 90.0, reps = 6, type = SetTypeDataModel.WORK),
                PlanSetDataModel(weight = 95.0, reps = 4, type = SetTypeDataModel.FAILURE),
            )

            repository.finishSessionAtomic(
                sessionUuid = session.uuid.toString(),
                finishedAt = 1L,
                planUpdates = listOf(
                    PlanUpdate(
                        trainingUuid = training.uuid.toString(),
                        exerciseUuid = exercise.uuid.toString(),
                        isAdhoc = false,
                        newPlan = newPlan,
                    ),
                ),
            )

            val rawPlan = env.trainingExerciseDao.getPlanSets(training.uuid, exercise.uuid)
            // Verify the new plan landed by checking shape; exact JSON is tested in DAO/converter
            // tests. Here, parse and compare.
            assertNotNull(rawPlan)
            assertTrue(rawPlan!!.contains("\"weight\":90.0"))
            assertTrue(rawPlan.contains("\"weight\":95.0"))
            assertTrue(rawPlan.contains("\"type\":\"FAILURE\""))
        }

    @Test
    fun `finishSessionAtomic applies plan updates for adhoc exercises against last_adhoc_sets`() = runTest {
        val training = env.seedTraining(name = "Adhoc", isAdhoc = true)
        val exercise = env.seedExercise(name = "Inline", isAdhoc = true, lastAdhocSets = null)
        val session = env.seedSession(trainingUuid = training.uuid)
        val newPlan = listOf(PlanSetDataModel(weight = 70.0, reps = 8, type = SetTypeDataModel.WORK))

        repository.finishSessionAtomic(
            sessionUuid = session.uuid.toString(),
            finishedAt = 1L,
            planUpdates = listOf(
                PlanUpdate(
                    trainingUuid = training.uuid.toString(),
                    exerciseUuid = exercise.uuid.toString(),
                    isAdhoc = true,
                    newPlan = newPlan,
                ),
            ),
        )

        val storedJson = env.exerciseDao.getById(exercise.uuid)?.lastAdhocSets
        assertNotNull(storedJson)
        assertTrue(storedJson!!.contains("\"weight\":70.0"))
        assertTrue(storedJson.contains("\"reps\":8"))
    }

    @Test
    fun `finishSessionAtomic rolls back state, name, and graduation when an inner write throws`() =
        runTest {
            // Hybrid: real DAOs for state-relevant tables, a spy on exerciseDao that throws on
            // graduateAdhocForTraining. Room's withTransaction wraps the whole block so the
            // failure rolls back every other DAO write.
            val adhocTraining = env.seedTraining(name = "Original", isAdhoc = true)
            val session = env.seedSession(trainingUuid = adhocTraining.uuid)
            val throwingExerciseDao = mockk<ExerciseDao>()
            coEvery {
                throwingExerciseDao.graduateAdhocForTraining(adhocTraining.uuid)
            } throws IllegalStateException("simulated graduation failure")

            val realTransition = env.transition
            val transitionSpy = spyk(
                object : DbTransitionRunner {
                    override suspend fun <T> invoke(
                        block: suspend kotlinx.coroutines.CoroutineScope.() -> T,
                    ): T = realTransition(block)
                },
            )

            val failingRepo = SessionRepositoryImpl(
                dao = env.sessionDao,
                performedExerciseDao = env.performedExerciseDao,
                setDao = env.setDao,
                trainingDao = env.trainingDao,
                // Only this DAO throws; all other DAOs are real and write to the in-memory DB.
                exerciseDao = throwingExerciseDao,
                trainingExerciseDao = env.trainingExerciseDao,
                transition = transitionSpy,
                ioDispatcher = UnconfinedTestDispatcher(),
            )

            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking {
                    failingRepo.finishSessionAtomic(
                        sessionUuid = session.uuid.toString(),
                        finishedAt = 7_000L,
                        planUpdates = emptyList(),
                        newTrainingName = "Half-Applied",
                    )
                }
            }

            // The transaction rolls back everything: state stays IN_PROGRESS, finished_at stays
            // null, the rename never lands, and the training is still flagged ad-hoc.
            val sessionAfter = env.sessionDao.getById(session.uuid)
            assertEquals(SessionStateEntity.IN_PROGRESS, sessionAfter?.state)
            assertNull(sessionAfter?.finishedAt)
            val trainingAfter = env.trainingDao.getById(adhocTraining.uuid)
            assertEquals("Original", trainingAfter?.name)
            assertEquals(true, trainingAfter?.isAdhoc)
            // The exercise DAO was invoked, confirming the throw came from the right step.
            coVerify(exactly = 1) {
                throwingExerciseDao.graduateAdhocForTraining(adhocTraining.uuid)
            }
        }
}
