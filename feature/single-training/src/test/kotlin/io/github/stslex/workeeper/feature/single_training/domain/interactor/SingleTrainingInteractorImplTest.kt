package io.github.stslex.workeeper.feature.single_training.domain.interactor

import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainChangeModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SingleTrainingInteractorImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val trainingRepository = mockk<TrainingRepository>()
    private val exerciseRepository = mockk<ExerciseRepository>()
    private val interactor: SingleTrainingInteractor = SingleTrainingInteractorImpl(
        trainingRepository = trainingRepository,
        exerciseRepository = exerciseRepository,
        defaultDispatcher = testDispatcher,
    )

    @Test
    fun `get training with exercises`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random().toString()
        val exerciseUuid1 = Uuid.random().toString()
        val exerciseUuid2 = Uuid.random().toString()

        val trainingData = TrainingDataModel(
            uuid = trainingUuid,
            name = "Test Training",
            exerciseUuids = listOf(exerciseUuid1, exerciseUuid2),
            labels = listOf("Label1", "Label2"),
            timestamp = 1234567890L,
        )

        val exercise1 = createExerciseDataModel(exerciseUuid1, "Exercise 1")
        val exercise2 = createExerciseDataModel(exerciseUuid2, "Exercise 2")

        coEvery { trainingRepository.getTraining(trainingUuid) } returns trainingData
        coEvery { exerciseRepository.getExercise(exerciseUuid1) } returns exercise1
        coEvery { exerciseRepository.getExercise(exerciseUuid2) } returns exercise2

        val result = interactor.getTraining(trainingUuid)

        coVerify(exactly = 1) { trainingRepository.getTraining(trainingUuid) }
        coVerify(exactly = 1) { exerciseRepository.getExercise(exerciseUuid1) }
        coVerify(exactly = 1) { exerciseRepository.getExercise(exerciseUuid2) }

        assertEquals(trainingUuid, result?.uuid)
        assertEquals("Test Training", result?.name)
        assertEquals(2, result?.exercises?.size)
        assertEquals("Exercise 1", result?.exercises?.get(0)?.name)
        assertEquals("Exercise 2", result?.exercises?.get(1)?.name)
        assertEquals(listOf("Label1", "Label2"), result?.labels)
    }

    @Test
    fun `get training when training not found returns null`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random().toString()

        coEvery { trainingRepository.getTraining(trainingUuid) } returns null

        val result = interactor.getTraining(trainingUuid)

        coVerify(exactly = 1) { trainingRepository.getTraining(trainingUuid) }
        assertNull(result)
    }

    @Test
    fun `get training filters null exercises`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random().toString()
        val exerciseUuid1 = Uuid.random().toString()
        val exerciseUuid2 = Uuid.random().toString()
        val exerciseUuid3 = Uuid.random().toString()

        val trainingData = TrainingDataModel(
            uuid = trainingUuid,
            name = "Test Training",
            exerciseUuids = listOf(exerciseUuid1, exerciseUuid2, exerciseUuid3),
            labels = emptyList(),
            timestamp = 1234567890L,
        )

        val exercise1 = createExerciseDataModel(exerciseUuid1, "Exercise 1")

        coEvery { trainingRepository.getTraining(trainingUuid) } returns trainingData
        coEvery { exerciseRepository.getExercise(exerciseUuid1) } returns exercise1
        coEvery { exerciseRepository.getExercise(exerciseUuid2) } returns null
        coEvery { exerciseRepository.getExercise(exerciseUuid3) } returns null

        val result = interactor.getTraining(trainingUuid)

        assertEquals(1, result?.exercises?.size)
        assertEquals("Exercise 1", result?.exercises?.get(0)?.name)
    }

    @Test
    @Suppress("UnusedFlow")
    fun `get training flow with exercises`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random().toString()
        val exerciseUuid1 = Uuid.random().toString()
        val exerciseUuid2 = Uuid.random().toString()

        val trainingData = TrainingDataModel(
            uuid = trainingUuid,
            name = "Test Training",
            exerciseUuids = listOf(exerciseUuid1, exerciseUuid2),
            labels = listOf("Label1", "Label2"),
            timestamp = 1234567890L,
        )

        val exercise1 = createExerciseDataModel(exerciseUuid1, "Exercise 1")
        val exercise2 = createExerciseDataModel(exerciseUuid2, "Exercise 2")

        coEvery { trainingRepository.subscribeForTraining(trainingUuid) } returns flowOf(
            trainingData,
        )
        coEvery { exerciseRepository.getExercise(exerciseUuid1) } returns exercise1
        coEvery { exerciseRepository.getExercise(exerciseUuid2) } returns exercise2

        val result = interactor.subscribeForTraining(trainingUuid).firstOrNull()

        coVerify(exactly = 1) { trainingRepository.subscribeForTraining(trainingUuid) }
        coVerify(exactly = 1) { exerciseRepository.getExercise(exerciseUuid1) }
        coVerify(exactly = 1) { exerciseRepository.getExercise(exerciseUuid2) }

        assertEquals(trainingUuid, result?.uuid)
        assertEquals("Test Training", result?.name)
        assertEquals(2, result?.exercises?.size)
        assertEquals("Exercise 1", result?.exercises?.get(0)?.name)
        assertEquals("Exercise 2", result?.exercises?.get(1)?.name)
        assertEquals(listOf("Label1", "Label2"), result?.labels)
    }

    @Suppress("UnusedFlow")
    @Test
    fun `get training flow when training not found returns null`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random().toString()

        coEvery { trainingRepository.subscribeForTraining(trainingUuid) } returns flowOf()

        val result = interactor.subscribeForTraining(trainingUuid).firstOrNull()

        coVerify(exactly = 1) { trainingRepository.subscribeForTraining(trainingUuid) }
        assertNull(result)
    }

    @Test
    fun `get training flow filters null exercises`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random().toString()
        val exerciseUuid1 = Uuid.random().toString()
        val exerciseUuid2 = Uuid.random().toString()
        val exerciseUuid3 = Uuid.random().toString()

        val trainingData = TrainingDataModel(
            uuid = trainingUuid,
            name = "Test Training",
            exerciseUuids = listOf(exerciseUuid1, exerciseUuid2, exerciseUuid3),
            labels = emptyList(),
            timestamp = 1234567890L,
        )

        val exercise1 = createExerciseDataModel(exerciseUuid1, "Exercise 1")

        coEvery {
            trainingRepository.subscribeForTraining(trainingUuid)
        } returns flowOf(trainingData)
        coEvery { exerciseRepository.getExercise(exerciseUuid1) } returns exercise1
        coEvery { exerciseRepository.getExercise(exerciseUuid2) } returns null
        coEvery { exerciseRepository.getExercise(exerciseUuid3) } returns null

        val result = interactor.subscribeForTraining(trainingUuid).firstOrNull()

        assertEquals(1, result?.exercises?.size)
        assertEquals("Exercise 1", result?.exercises?.get(0)?.name)
    }

    @Test
    fun `remove training deletes training and associated exercises`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random().toString()

        coEvery { trainingRepository.removeTraining(trainingUuid) } returns Unit
        coEvery { exerciseRepository.deleteByTrainingUuid(trainingUuid) } returns Unit

        interactor.removeTraining(trainingUuid)

        coVerify(exactly = 1) { trainingRepository.removeTraining(trainingUuid) }
        coVerify(exactly = 1) { exerciseRepository.deleteByTrainingUuid(trainingUuid) }
    }

    @Test
    fun `update training with removed exercises`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random().toString()
        val exerciseUuid1 = Uuid.random().toString()
        val exerciseUuid2 = Uuid.random().toString()
        val exerciseUuid3 = Uuid.random().toString()

        val existingTraining = TrainingDataModel(
            uuid = trainingUuid,
            name = "Existing Training",
            exerciseUuids = listOf(exerciseUuid1, exerciseUuid2, exerciseUuid3),
            labels = emptyList(),
            timestamp = 1234567890L,
        )

        val updatedTraining = TrainingDomainChangeModel(
            uuid = trainingUuid,
            name = "Updated Training",
            exercisesUuids = listOf(exerciseUuid1, exerciseUuid3), // exerciseUuid2 removed
            labels = listOf("New Label"),
            timestamp = 1234567891L,
        )

        val expectedChangeModel = TrainingChangeDataModel(
            uuid = trainingUuid,
            name = "Updated Training",
            exerciseUuids = listOf(exerciseUuid1, exerciseUuid3),
            labels = listOf("New Label"),
            timestamp = 1234567891L,
        )

        coEvery { trainingRepository.getTraining(trainingUuid) } returns existingTraining
        coEvery { exerciseRepository.deleteItem(exerciseUuid2) } returns Unit
        coEvery { trainingRepository.updateTraining(expectedChangeModel) } returns Unit

        interactor.updateTraining(updatedTraining)

        coVerify(exactly = 1) { trainingRepository.getTraining(trainingUuid) }
        coVerify(exactly = 1) { exerciseRepository.deleteItem(exerciseUuid2) }
        coVerify(exactly = 1) { trainingRepository.updateTraining(expectedChangeModel) }
    }

    @Test
    fun `update training when new training`() = runTest(testDispatcher) {
        val exerciseUuid1 = Uuid.random().toString()

        val newTraining = TrainingDomainChangeModel(
            uuid = null, // New training
            name = "New Training",
            exercisesUuids = listOf(exerciseUuid1),
            labels = listOf("Label"),
            timestamp = 1234567890L,
        )

        val expectedChangeModel = TrainingChangeDataModel(
            uuid = null,
            name = "New Training",
            exerciseUuids = listOf(exerciseUuid1),
            labels = listOf("Label"),
            timestamp = 1234567890L,
        )

        coEvery { trainingRepository.updateTraining(expectedChangeModel) } returns Unit

        interactor.updateTraining(newTraining)

        coVerify(exactly = 0) { trainingRepository.getTraining(any()) }
        coVerify(exactly = 0) { exerciseRepository.deleteItem(any()) }
        coVerify(exactly = 1) { trainingRepository.updateTraining(expectedChangeModel) }
    }

    @Test
    fun `update training with no exercise changes`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random().toString()
        val exerciseUuid1 = Uuid.random().toString()
        val exerciseUuid2 = Uuid.random().toString()

        val existingTraining = TrainingDataModel(
            uuid = trainingUuid,
            name = "Existing Training",
            exerciseUuids = listOf(exerciseUuid1, exerciseUuid2),
            labels = emptyList(),
            timestamp = 1234567890L,
        )

        val updatedTraining = TrainingDomainChangeModel(
            uuid = trainingUuid,
            name = "Updated Training",
            exercisesUuids = listOf(exerciseUuid1, exerciseUuid2),
            labels = listOf("New Label"),
            timestamp = 1234567891L,
        )

        val expectedChangeModel = TrainingChangeDataModel(
            uuid = trainingUuid,
            name = "Updated Training",
            exerciseUuids = listOf(exerciseUuid1, exerciseUuid2),
            labels = listOf("New Label"),
            timestamp = 1234567891L,
        )

        coEvery { trainingRepository.getTraining(trainingUuid) } returns existingTraining
        coEvery { trainingRepository.updateTraining(expectedChangeModel) } returns Unit

        interactor.updateTraining(updatedTraining)

        coVerify(exactly = 1) { trainingRepository.getTraining(trainingUuid) }
        coVerify(exactly = 0) { exerciseRepository.deleteItem(any()) }
        coVerify(exactly = 1) { trainingRepository.updateTraining(expectedChangeModel) }
    }

    private fun createExerciseDataModel(
        uuid: String,
        name: String,
    ) = ExerciseDataModel(
        uuid = uuid,
        name = name,
        sets = listOf(
            SetsDataModel(
                uuid = Uuid.random().toString(),
                reps = 10,
                weight = 50.0,
                type = SetsDataType.WORK,
            ),
        ),
        labels = listOf("Test"),
        trainingUuid = null,
        timestamp = 1234567890L,
    )
}
