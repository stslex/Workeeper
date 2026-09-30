// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.training

import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
internal class TrainingExerciseRepositoryImplDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: TrainingExerciseRepositoryImpl

    @BeforeEach
    fun setup() {
        // GUARD: plan reads log through `traceExecutionTime`; stub the holder or Firebase
        // resolves. See documentation/testing.md.
        mockkObject(FirebaseCrashlyticsHolder)
        every { FirebaseCrashlyticsHolder.log(any()) } returns Unit
        env = RepositoryTestEnv()
        repository = TrainingExerciseRepositoryImpl(
            dao = env.trainingExerciseDao,
            transition = env.transition,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
        unmockkObject(FirebaseCrashlyticsHolder)
    }

    @Test
    fun `setPlan persists the JSON for a training_exercise row and getPlan parses it back`() = runTest {
        val (trainingUuid, exerciseUuid) = seedTrainingAndExerciseRow()
        val plan = listOf(
            PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK),
            PlanSetDataModel(weight = 90.0, reps = 4, type = SetTypeDataModel.FAILURE),
        )

        repository.setPlan(trainingUuid.toString(), exerciseUuid.toString(), plan)

        val parsed = repository.getPlan(trainingUuid.toString(), exerciseUuid.toString())
        assertEquals(plan, parsed)
    }

    @Test
    fun `setPlan with null clears the plan_sets column`() = runTest {
        val (trainingUuid, exerciseUuid) = seedTrainingAndExerciseRow(
            planSets = """[{"weight":80.0,"reps":5,"type":"WORK"}]""",
        )

        repository.setPlan(trainingUuid.toString(), exerciseUuid.toString(), planSets = null)

        assertNull(repository.getPlan(trainingUuid.toString(), exerciseUuid.toString()))
    }

    @Test
    fun `detachExercise deletes the pair row - the one-off toggle's OFF write`() = runTest {
        val (trainingUuid, exerciseUuid) = seedTrainingAndExerciseRow()

        repository.detachExercise(trainingUuid.toString(), exerciseUuid.toString())

        // v3 §6.2: the row's absence IS the one-off flag.
        val plans = repository.getPlans(trainingUuid.toString(), listOf(exerciseUuid.toString()))
        assertFalse(plans.containsKey(exerciseUuid.toString()))
    }

    @Test
    fun `attachExercise re-inserts the pair row with the given plan`() = runTest {
        val (trainingUuid, exerciseUuid) = seedTrainingAndExerciseRow()
        repository.detachExercise(trainingUuid.toString(), exerciseUuid.toString())
        val plan = listOf(PlanSetDataModel(weight = 60.0, reps = 8, type = SetTypeDataModel.WORK))

        repository.attachExercise(trainingUuid.toString(), exerciseUuid.toString(), plan)

        val plans = repository.getPlans(trainingUuid.toString(), listOf(exerciseUuid.toString()))
        assertTrue(plans.containsKey(exerciseUuid.toString()))
        assertEquals(plan, plans[exerciseUuid.toString()])
    }

    @Test
    fun `getPlan returns null when no row exists for the pair`() = runTest {
        assertNull(repository.getPlan(Uuid.random().toString(), Uuid.random().toString()))
    }

    @Test
    fun `getRowsForTraining returns rows ordered by position with parsed plans`() = runTest {
        val trainingUuid = Uuid.random()
        val firstExerciseUuid = Uuid.random()
        val secondExerciseUuid = Uuid.random()
        seedExercise(firstExerciseUuid, "First")
        seedExercise(secondExerciseUuid, "Second")
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        // Insert second-position row first to verify ordering by position.
        env.trainingExerciseDao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = secondExerciseUuid,
                    position = 1,
                    planSets = """[{"weight":40.0,"reps":12,"type":"WORK"}]""",
                ),
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = firstExerciseUuid,
                    position = 0,
                    planSets = null,
                ),
            ),
        )

        val rows = repository.getRowsForTraining(trainingUuid.toString())

        assertEquals(2, rows.size)
        assertEquals(0, rows[0].position)
        assertEquals(firstExerciseUuid.toString(), rows[0].exerciseUuid)
        assertNull(rows[0].planSets)
        assertEquals(1, rows[1].position)
        assertEquals(secondExerciseUuid.toString(), rows[1].exerciseUuid)
        assertEquals(
            listOf(
                PlanSetDataModel(weight = 40.0, reps = 12, type = SetTypeDataModel.WORK),
            ),
            rows[1].planSets,
        )
    }

    @Test
    fun `getPlans with empty exerciseUuids returns empty Map and does not call the DAO`() =
        runTest {
            val spiedDao = spyk<TrainingExerciseDao>(env.trainingExerciseDao)
            val repositoryWithSpy = TrainingExerciseRepositoryImpl(
                dao = spiedDao,
                transition = env.transition,
                ioDispatcher = UnconfinedTestDispatcher(),
            )

            val result = repositoryWithSpy.getPlans(Uuid.random().toString(), emptyList())

            assertTrue(result.isEmpty())
            coVerify(exactly = 0) { spiedDao.getPlanSetsBatch(any(), any()) }
        }

    @Test
    fun `getPlans returns a map with non-null roundtripped plans, preserved nulls, and missing pairs absent`() =
        runTest {
            val trainingUuid = Uuid.random()
            val exerciseWithPlanUuid = Uuid.random()
            val exerciseWithNullPlanUuid = Uuid.random()
            val exerciseNotAttachedUuid = Uuid.random()
            seedExercise(exerciseWithPlanUuid, "Bench")
            seedExercise(exerciseWithNullPlanUuid, "Squat")
            seedExercise(exerciseNotAttachedUuid, "Lunge")
            env.trainingDao.insert(
                TrainingEntity(
                    uuid = trainingUuid,
                    name = "Push",
                    description = null,
                    isAdhoc = false,
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                ),
            )
            // Write through `setPlan` so the JSON is the production shape.
            env.trainingExerciseDao.insert(
                listOf(
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = exerciseWithPlanUuid,
                        position = 0,
                    ),
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = exerciseWithNullPlanUuid,
                        position = 1,
                        planSets = null,
                    ),
                ),
            )
            val plan = listOf(
                PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
                PlanSetDataModel(weight = 110.0, reps = 4, type = SetTypeDataModel.WORK),
            )
            repository.setPlan(trainingUuid.toString(), exerciseWithPlanUuid.toString(), plan)

            val result = repository.getPlans(
                trainingUuid = trainingUuid.toString(),
                exerciseUuids = listOf(
                    exerciseWithPlanUuid.toString(),
                    exerciseWithNullPlanUuid.toString(),
                    exerciseNotAttachedUuid.toString(),
                ),
            )

            assertEquals(plan, result[exerciseWithPlanUuid.toString()])
            assertTrue(result.containsKey(exerciseWithNullPlanUuid.toString()))
            assertNull(result[exerciseWithNullPlanUuid.toString()])
            assertFalse(result.containsKey(exerciseNotAttachedUuid.toString()))
        }

    @Test
    fun `getPlans distinguishes a row with empty list plan from a row with null plan_sets`() =
        runTest {
            val trainingUuid = Uuid.random()
            val nullPlanExercise = Uuid.random()
            val emptyPlanExercise = Uuid.random()
            seedExercise(nullPlanExercise, "NullPlan")
            seedExercise(emptyPlanExercise, "EmptyPlan")
            env.trainingDao.insert(
                TrainingEntity(
                    uuid = trainingUuid,
                    name = "Push",
                    description = null,
                    isAdhoc = false,
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                ),
            )
            env.trainingExerciseDao.insert(
                listOf(
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = nullPlanExercise,
                        position = 0,
                        planSets = null,
                    ),
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = emptyPlanExercise,
                        position = 1,
                        // Empty list JSON — distinguishable from null at read time.
                        planSets = "[]",
                    ),
                ),
            )

            val result = repository.getPlans(
                trainingUuid = trainingUuid.toString(),
                exerciseUuids = listOf(
                    nullPlanExercise.toString(),
                    emptyPlanExercise.toString(),
                ),
            )

            // Only null entries get the loadSession fallback; an empty-list plan stays empty.
            assertNull(result[nullPlanExercise.toString()])
            assertNotNull(result[emptyPlanExercise.toString()])
            assertEquals(emptyList<PlanSetDataModel>(), result[emptyPlanExercise.toString()])
        }

    private suspend fun seedExercise(uuid: Uuid, name: String) {
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = name,
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
    }

    private suspend fun seedTrainingAndExerciseRow(
        planSets: String? = null,
    ): Pair<Uuid, Uuid> {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedExercise(exerciseUuid, "Bench-$exerciseUuid")
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        env.trainingExerciseDao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    planSets = planSets,
                ),
            ),
        )
        return trainingUuid to exerciseUuid
    }
}
