// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.exercise

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class ExerciseDaoLinkedTrainingsCountTest : BaseDatabaseTest() {

    private val exerciseDao get() = database.exerciseDao
    private val trainingDao get() = database.trainingDao
    private val trainingExerciseDao get() = database.trainingExerciseDao

    @BeforeEach
    fun setup() = initDb()

    @AfterEach
    fun teardown() = clearDb()

    @Test
    fun `exercise referenced by zero trainings reports zero`() = runTest {
        val exerciseUuid = Uuid.random()
        seedExercise(exerciseUuid)

        val result = exerciseDao.observeLinkedTrainingsCount(exerciseUuid).first()

        assertEquals(0, result)
    }

    @Test
    fun `exercise referenced by a single active training reports one`() = runTest {
        val exerciseUuid = Uuid.random()
        val trainingUuid = Uuid.random()
        seedExercise(exerciseUuid)
        seedTraining(trainingUuid, archived = false, isAdhoc = false)
        seedTrainingExercise(trainingUuid, exerciseUuid)

        val result = exerciseDao.observeLinkedTrainingsCount(exerciseUuid).first()

        assertEquals(1, result)
    }

    @Test
    fun `exercise referenced by three distinct active trainings reports three`() = runTest {
        val exerciseUuid = Uuid.random()
        seedExercise(exerciseUuid)
        repeat(3) {
            val trainingUuid = Uuid.random()
            seedTraining(trainingUuid, archived = false, isAdhoc = false)
            seedTrainingExercise(trainingUuid, exerciseUuid)
        }

        val result = exerciseDao.observeLinkedTrainingsCount(exerciseUuid).first()

        assertEquals(3, result)
    }

    @Test
    fun `archived training is excluded from count`() = runTest {
        val exerciseUuid = Uuid.random()
        val activeUuid = Uuid.random()
        val archivedUuid = Uuid.random()
        seedExercise(exerciseUuid)
        seedTraining(activeUuid, archived = false, isAdhoc = false)
        seedTraining(archivedUuid, archived = true, isAdhoc = false)
        seedTrainingExercise(activeUuid, exerciseUuid)
        seedTrainingExercise(archivedUuid, exerciseUuid)

        val result = exerciseDao.observeLinkedTrainingsCount(exerciseUuid).first()

        assertEquals(1, result)
    }

    @Test
    fun `adhoc training is excluded from count`() = runTest {
        val exerciseUuid = Uuid.random()
        val libraryUuid = Uuid.random()
        val adhocUuid = Uuid.random()
        seedExercise(exerciseUuid)
        seedTraining(libraryUuid, archived = false, isAdhoc = false)
        seedTraining(adhocUuid, archived = false, isAdhoc = true)
        seedTrainingExercise(libraryUuid, exerciseUuid)
        seedTrainingExercise(adhocUuid, exerciseUuid)

        val result = exerciseDao.observeLinkedTrainingsCount(exerciseUuid).first()

        assertEquals(1, result)
    }

    private suspend fun seedExercise(uuid: Uuid) {
        exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Exercise-$uuid",
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

    private suspend fun seedTraining(uuid: Uuid, archived: Boolean, isAdhoc: Boolean) {
        trainingDao.insert(
            TrainingEntity(
                uuid = uuid,
                name = "Training-$uuid",
                description = null,
                isAdhoc = isAdhoc,
                archived = archived,
                createdAt = 0L,
                archivedAt = if (archived) 0L else null,
            ),
        )
    }

    private suspend fun seedTrainingExercise(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        position: Int = 0,
    ) {
        trainingExerciseDao.insert(
            TrainingExerciseEntity(
                trainingUuid = trainingUuid,
                exerciseUuid = exerciseUuid,
                position = position,
                planSets = null,
            ),
        )
    }
}
