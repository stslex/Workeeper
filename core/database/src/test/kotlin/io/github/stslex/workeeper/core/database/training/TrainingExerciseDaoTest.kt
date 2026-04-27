// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.training

import io.github.stslex.workeeper.core.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.exercise.ExerciseTypeEntity
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
        exerciseDao.insert(
            ExerciseEntity(
                uuid = exerciseUuid,
                name = "Bench",
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
