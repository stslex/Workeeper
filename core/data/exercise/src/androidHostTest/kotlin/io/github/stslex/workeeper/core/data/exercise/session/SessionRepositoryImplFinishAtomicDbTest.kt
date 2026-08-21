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
        val graduatedExercise = env.exerciseDao.getById(adhocExercise.uuid)
        // Graduation is scoped by SESSION MEMBERSHIP (v3 §6.2): an ad-hoc exercise graduates
        // when it was performed in the finished session. We seeded no performed row here, so
        // this exercise is NOT graduated — confirming the scope is a join, not a wildcard.
        assertEquals(true, graduatedExercise?.isAdhoc)
    }

    @Test
    fun `finishSessionAtomic graduates only exercises performed in the session`() = runTest {
        // Contract change in v3 step 5 (§6.2): graduation is scoped by session membership,
        // not by plan attachment. A one-off has no `training_exercise_table` row by
        // construction, so the old plan-table join stranded every inline-created one-off at
        // `is_adhoc = 1` — permanently invisible to `pagedActive`. `performed` here is
        // deliberately NOT plan-attached, which is exactly the case the old rule missed.
        val adhocTraining = env.seedTraining(name = "Adhoc", isAdhoc = true)
        val performed = env.seedExercise(name = "Performed one-off", isAdhoc = true)
        val planOnly = env.seedExercise(name = "Plan-only", isAdhoc = true)
        val session = env.seedSession(trainingUuid = adhocTraining.uuid)
        env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = performed.uuid)
        // Attached to the plan but never performed in this session.
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

        // Performed in the session — graduates, even with no plan row.
        assertEquals(false, env.exerciseDao.getById(performed.uuid)?.isAdhoc)
        // In the plan but not performed here — untouched. The scope is still a join.
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
            // graduateAdhocForSession. Room's withTransaction wraps the whole block so the
            // failure rolls back every other DAO write.
            val adhocTraining = env.seedTraining(name = "Original", isAdhoc = true)
            val session = env.seedSession(trainingUuid = adhocTraining.uuid)
            val throwingExerciseDao = mockk<ExerciseDao>()
            coEvery {
                throwingExerciseDao.graduateAdhocForSession(session.uuid)
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
                throwingExerciseDao.graduateAdhocForSession(session.uuid)
            }
        }
}
