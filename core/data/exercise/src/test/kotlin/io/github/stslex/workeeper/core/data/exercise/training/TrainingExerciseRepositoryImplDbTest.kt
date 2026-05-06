// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.training

import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        env = RepositoryTestEnv()
        repository = TrainingExerciseRepositoryImpl(
            dao = env.trainingExerciseDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
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
