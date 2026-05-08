// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.training

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class TrainingExerciseDaoTest : BaseDatabaseTest() {

    private val dao
        get() = database.trainingExerciseDao
    private val trainingDao
        get() = database.trainingDao
    private val exerciseDao
        get() = database.exerciseDao

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `getPlanSets returns null when no plan stored`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)

        dao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                ),
            ),
        )

        assertNull(dao.getPlanSets(trainingUuid, exerciseUuid))
    }

    @Test
    fun `updatePlanSets persists json then getPlanSets returns it`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        dao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                ),
            ),
        )

        val json = """[{"weight":100.0,"reps":5,"type":"WORK"}]"""
        dao.updatePlanSets(trainingUuid, exerciseUuid, json)

        assertEquals(json, dao.getPlanSets(trainingUuid, exerciseUuid))
    }

    @Test
    fun `updatePlanSets with null clears stored json`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        dao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    planSets = """[{"weight":100.0,"reps":5,"type":"WORK"}]""",
                ),
            ),
        )

        dao.updatePlanSets(trainingUuid, exerciseUuid, null)

        assertNull(dao.getPlanSets(trainingUuid, exerciseUuid))
    }

    @Test
    fun `getPlanSetsBatch with empty exerciseUuids returns empty list`() = runTest {
        val rows = dao.getPlanSetsBatch(Uuid.random(), emptyList())

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `getPlanSetsBatch returns empty list when training uuid is not in database`() = runTest {
        val unknownTraining = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedExercise(exerciseUuid, "Bench")

        val rows = dao.getPlanSetsBatch(unknownTraining, listOf(exerciseUuid))

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `getPlanSetsBatch returns rows with non-null and null planSets and skips missing pairs`() =
        runTest {
            val trainingUuid = Uuid.random()
            val exerciseWithPlanUuid = Uuid.random()
            val exerciseWithNullPlanUuid = Uuid.random()
            val exerciseNotAttachedUuid = Uuid.random()
            // Library exercises must exist before training_exercise FK can resolve.
            seedExercise(exerciseWithPlanUuid, "Bench")
            seedExercise(exerciseWithNullPlanUuid, "Squat")
            seedExercise(exerciseNotAttachedUuid, "Lunge")
            trainingDao.insert(
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
            dao.insert(
                listOf(
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = exerciseWithPlanUuid,
                        position = 0,
                        planSets = """[{"weight":80.0,"reps":5,"type":"WORK"}]""",
                    ),
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = exerciseWithNullPlanUuid,
                        position = 1,
                        planSets = null,
                    ),
                    // exerciseNotAttachedUuid is intentionally NOT inserted into
                    // training_exercise_table for this training — it should not surface.
                ),
            )

            val rows = dao.getPlanSetsBatch(
                trainingUuid = trainingUuid,
                exerciseUuids = listOf(
                    exerciseWithPlanUuid,
                    exerciseWithNullPlanUuid,
                    exerciseNotAttachedUuid,
                ),
            )

            // Existing rows are returned regardless of planSets nullability; non-existing
            // pair (training, exercise) is silently absent from the result.
            assertEquals(2, rows.size)
            val byExerciseUuid = rows.associateBy { it.exerciseUuid }
            assertEquals(
                """[{"weight":80.0,"reps":5,"type":"WORK"}]""",
                byExerciseUuid[exerciseWithPlanUuid]?.planSets,
            )
            assertNull(byExerciseUuid[exerciseWithNullPlanUuid]?.planSets)
            assertNull(byExerciseUuid[exerciseNotAttachedUuid])
        }

    @Test
    fun `getPlanSetsBatch row exerciseUuid matches the requested input`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        dao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    planSets = """[{"weight":80.0,"reps":5,"type":"WORK"}]""",
                ),
            ),
        )

        val rows = dao.getPlanSetsBatch(trainingUuid, listOf(exerciseUuid))

        assertEquals(exerciseUuid, rows.single().exerciseUuid)
    }

    private suspend fun seedTrainingAndExercise(trainingUuid: Uuid, exerciseUuid: Uuid) {
        trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push Day",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        seedExercise(exerciseUuid, "Bench")
    }

    private suspend fun seedExercise(exerciseUuid: Uuid, name: String) {
        exerciseDao.insert(
            ExerciseEntity(
                uuid = exerciseUuid,
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
}
