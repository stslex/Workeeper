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
        val finished = env.sessionDao.getById(session.uuid)
        assertEquals(SessionStateEntity.FINISHED, finished?.state)
        assertEquals(2_000L, finished?.finishedAt)
        val graduatedTraining = env.trainingDao.getById(adhocTraining.uuid)
        assertEquals(false, graduatedTraining?.isAdhoc)
        val graduatedExercise = env.exerciseDao.getById(adhocExercise.uuid)
        // Graduation is scoped by session membership; no performed row was seeded, so no
        // graduation.
        assertEquals(true, graduatedExercise?.isAdhoc)
    }

    @Test
    fun `finishSessionAtomic graduates only exercises performed in the session`() = runTest {
        // `performed` is deliberately not plan-attached — the case a plan-table join missed.
        val adhocTraining = env.seedTraining(name = "Adhoc", isAdhoc = true)
        val performed = env.seedExercise(name = "Performed one-off", isAdhoc = true)
        val planOnly = env.seedExercise(name = "Plan-only", isAdhoc = true)
        val session = env.seedSession(trainingUuid = adhocTraining.uuid)
        env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = performed.uuid)
        env.seedTrainingExercise(
            trainingUuid = adhocTraining.uuid,
            exerciseUuid = planOnly.uuid,
            position = 0,
        )

        repository.finishSessionAtomic(
            sessionUuid = session.uuid.toString(),
            finishedAt = 5_000L,
            planUpdates = emptyList(),
        )

        assertEquals(false, env.exerciseDao.getById(performed.uuid)?.isAdhoc)
        assertEquals(true, env.exerciseDao.getById(planOnly.uuid)?.isAdhoc)
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
            // Real DAOs plus a spy that throws on graduateAdhocForSession, so the surrounding
            // transaction has to roll back every other write.
            val adhocTraining = env.seedTraining(name = "Original", isAdhoc = true)
            val session = env.seedSession(trainingUuid = adhocTraining.uuid)
            val throwingExerciseDao = mockk<ExerciseDao>()
            coEvery {
                throwingExerciseDao.graduateAdhocForSession(session.uuid)
            } throws IllegalStateException("simulated graduation failure")

            val realTransition = env.transition
            val transitionSpy = spyk(
                object : DbTransitionRunner {
                    override fun addAfterMutationCommitListener(listener: () -> Unit) {
                        realTransition.addAfterMutationCommitListener(listener)
                    }

                    override suspend fun <T> invoke(
                        block: suspend kotlinx.coroutines.CoroutineScope.() -> T,
                    ): T = realTransition(block)

                    override suspend fun <T> mutate(
                        block: suspend kotlinx.coroutines.CoroutineScope.() -> T,
                    ): T = realTransition.mutate(block)
                },
            )

            val failingRepo = SessionRepositoryImpl(
                dao = env.sessionDao,
                performedExerciseDao = env.performedExerciseDao,
                setDao = env.setDao,
                trainingDao = env.trainingDao,
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

            val sessionAfter = env.sessionDao.getById(session.uuid)
            assertEquals(SessionStateEntity.IN_PROGRESS, sessionAfter?.state)
            assertNull(sessionAfter?.finishedAt)
            val trainingAfter = env.trainingDao.getById(adhocTraining.uuid)
            assertEquals("Original", trainingAfter?.name)
            assertEquals(true, trainingAfter?.isAdhoc)
            coVerify(exactly = 1) {
                throwingExerciseDao.graduateAdhocForSession(session.uuid)
            }
        }
}
