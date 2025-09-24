package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

internal class ExerciseInteractorImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val exerciseRepository = mockk<ExerciseRepository>()
    private val trainingRepository = mockk<TrainingRepository>()
    private val interactor: ExerciseInteractor = ExerciseInteractorImpl(
        exerciseRepository = exerciseRepository,
        trainingRepository = trainingRepository,
        dispatcher = testDispatcher,
    )

    @Test
    fun `save item with no training uuid saves exercise only`() = runTest(testDispatcher) {
        val exerciseChangeModel = createExerciseChangeDataModel(
            name = "Test Exercise",
            trainingUuid = null,
        )
        val savedExercise = createExerciseDataModel(
            uuid = Uuid.random().toString(),
            name = "Test Exercise",
            trainingUuid = null,
        )

        coEvery { exerciseRepository.saveItem(any()) } returns Unit
        coEvery { exerciseRepository.getExercise(any()) } returns savedExercise

        interactor.saveItem(exerciseChangeModel)

        coVerify(exactly = 1) { exerciseRepository.saveItem(any()) }
        coVerify(exactly = 1) { exerciseRepository.getExercise(any()) }
        coVerify(exactly = 0) { trainingRepository.getTraining(any()) }
        coVerify(exactly = 0) { trainingRepository.updateTraining(any()) }
    }

    @Test
    fun `save item with training uuid but exercise not found in training adds exercise to training`() =
        runTest(testDispatcher) {
            val trainingUuid = Uuid.random().toString()
            val exerciseUuid = Uuid.random().toString()

            val exerciseChangeModel = createExerciseChangeDataModel(
                name = "Test Exercise",
                trainingUuid = trainingUuid,
            )
            val savedExercise = createExerciseDataModel(
                uuid = exerciseUuid,
                name = "Test Exercise",
                trainingUuid = trainingUuid,
            )
            val existingTraining = createTrainingDataModel(
                uuid = trainingUuid,
                name = "Test Training",
                exerciseUuids = emptyList(),
            )
            val expectedUpdatedTraining = TrainingChangeDataModel(
                uuid = trainingUuid,
                name = "Test Training",
                exerciseUuids = listOf(exerciseUuid),
                labels = listOf("Test Label"),
                timestamp = 1234567890L,
            )

            coEvery { exerciseRepository.saveItem(any()) } returns Unit
            coEvery { exerciseRepository.getExercise(any()) } returns savedExercise
            coEvery { trainingRepository.getTraining(trainingUuid) } returns existingTraining
            coEvery { trainingRepository.updateTraining(expectedUpdatedTraining) } returns Unit

            interactor.saveItem(exerciseChangeModel)

            coVerify(exactly = 1) { exerciseRepository.saveItem(any()) }
            coVerify(exactly = 1) { exerciseRepository.getExercise(any()) }
            coVerify(exactly = 1) { trainingRepository.getTraining(trainingUuid) }
            coVerify(exactly = 1) { trainingRepository.updateTraining(expectedUpdatedTraining) }
        }

    @Test
    fun `save item with training uuid and exercise already in training does not update training`() =
        runTest(testDispatcher) {
            val trainingUuid = Uuid.random().toString()
            val exerciseUuid = Uuid.random().toString()

            val exerciseChangeModel = createExerciseChangeDataModel(
                name = "Test Exercise",
                trainingUuid = trainingUuid,
            )
            val savedExercise = createExerciseDataModel(
                uuid = exerciseUuid,
                name = "Test Exercise",
                trainingUuid = trainingUuid,
            )
            val existingTraining = createTrainingDataModel(
                uuid = trainingUuid,
                name = "Test Training",
                exerciseUuids = listOf(exerciseUuid), // Exercise already in training
            )

            coEvery { exerciseRepository.saveItem(any()) } returns Unit
            coEvery { exerciseRepository.getExercise(any()) } returns savedExercise
            coEvery { trainingRepository.getTraining(trainingUuid) } returns existingTraining

            interactor.saveItem(exerciseChangeModel)

            coVerify(exactly = 1) { exerciseRepository.saveItem(any()) }
            coVerify(exactly = 1) { exerciseRepository.getExercise(any()) }
            coVerify(exactly = 1) { trainingRepository.getTraining(trainingUuid) }
            coVerify(exactly = 0) { trainingRepository.updateTraining(any()) }
        }

    @Test
    fun `save item with training uuid but training not found does not update training`() =
        runTest(testDispatcher) {
            val trainingUuid = Uuid.random().toString()
            val exerciseUuid = Uuid.random().toString()

            val exerciseChangeModel = createExerciseChangeDataModel(
                name = "Test Exercise",
                trainingUuid = trainingUuid,
            )
            val savedExercise = createExerciseDataModel(
                uuid = exerciseUuid,
                name = "Test Exercise",
                trainingUuid = trainingUuid,
            )

            coEvery { exerciseRepository.saveItem(any()) } returns Unit
            coEvery { exerciseRepository.getExercise(any()) } returns savedExercise
            coEvery { trainingRepository.getTraining(trainingUuid) } returns null

            interactor.saveItem(exerciseChangeModel)

            coVerify(exactly = 1) { exerciseRepository.saveItem(any()) }
            coVerify(exactly = 1) { exerciseRepository.getExercise(any()) }
            coVerify(exactly = 1) { trainingRepository.getTraining(trainingUuid) }
            coVerify(exactly = 0) { trainingRepository.updateTraining(any()) }
        }

    @Test
    fun `save item when exercise not found after save does not update training`() =
        runTest(testDispatcher) {
            val trainingUuid = Uuid.random().toString()

            val exerciseChangeModel = createExerciseChangeDataModel(
                name = "Test Exercise",
                trainingUuid = trainingUuid,
            )

            coEvery { exerciseRepository.saveItem(any()) } returns Unit
            coEvery { exerciseRepository.getExercise(any()) } returns null

            interactor.saveItem(exerciseChangeModel)

            coVerify(exactly = 1) { exerciseRepository.saveItem(any()) }
            coVerify(exactly = 1) { exerciseRepository.getExercise(any()) }
            coVerify(exactly = 0) { trainingRepository.getTraining(any()) }
            coVerify(exactly = 0) { trainingRepository.updateTraining(any()) }
        }

    @Test
    fun `delete item by uuid`() = runTest(testDispatcher) {
        coEvery { exerciseRepository.deleteItem(any()) } just runs
        val uuid = "test_uuid"
        interactor.deleteItem(uuid)

        coVerify(exactly = 1) { exerciseRepository.deleteItem(uuid) }
    }

    @Test
    fun `get item by uuid`() = runTest(testDispatcher) {
        val uuid = "test_uuid"
        val testExercise = createExerciseDataModel("uuid", "test_name", "training_uuid")
        coEvery { exerciseRepository.getExercise(uuid) } returns testExercise
        val exercise = interactor.getExercise(uuid)

        coVerify(exactly = 1) { exerciseRepository.getExercise(uuid) }
        assertEquals(testExercise, exercise)
    }

    @Test
    fun `get items by uuid`() = runTest(testDispatcher) {
        val testQuery = "test_query"
        val testExercises = Array(10) {
            createExerciseDataModel("uuid_$it", "testQuery_$it", "training_uuid")
        }.toList()

        coEvery { exerciseRepository.searchItems(testQuery) } returns testExercises

        val exercises = interactor.searchItems(testQuery)

        coVerify(exactly = 1) { exerciseRepository.searchItems(testQuery) }
        assertEquals(testExercises, exercises)
    }

    private fun createExerciseChangeDataModel(
        uuid: String? = null,
        name: String,
        trainingUuid: String?,
    ) = ExerciseChangeDataModel(
        uuid = uuid,
        name = name,
        trainingUuid = trainingUuid,
        sets = listOf(
            SetsDataModel(
                uuid = Uuid.random().toString(),
                reps = 10,
                weight = 50.0,
                type = SetsDataType.WORK,
            ),
        ),
        labels = listOf("Test Label"),
        timestamp = 1234567890L,
    )

    @Suppress("SameParameterValue")
    private fun createExerciseDataModel(
        uuid: String,
        name: String,
        trainingUuid: String?,
    ) = ExerciseDataModel(
        uuid = uuid,
        name = name,
        trainingUuid = trainingUuid,
        sets = listOf(
            SetsDataModel(
                uuid = Uuid.random().toString(),
                reps = 10,
                weight = 50.0,
                type = SetsDataType.WORK,
            ),
        ),
        labels = listOf("Test Label"),
        timestamp = 1234567890L,
    )

    @Suppress("SameParameterValue")
    private fun createTrainingDataModel(
        uuid: String,
        name: String,
        exerciseUuids: List<String>,
    ) = TrainingDataModel(
        uuid = uuid,
        name = name,
        exerciseUuids = exerciseUuids,
        labels = listOf("Test Label"),
        timestamp = 1234567890L,
    )
}
