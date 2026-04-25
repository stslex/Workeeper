package io.github.stslex.workeeper.feature.all_trainings.domain

import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AllTrainingsInteractorImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val trainingRepository = mockk<TrainingRepository>()
    private val interactor: AllTrainingsInteractor = AllTrainingsInteractorImpl(
        trainingRepository = trainingRepository,
        defaultDispatcher = testDispatcher,
    )

    @BeforeEach
    fun bindMain() = Dispatchers.setMain(testDispatcher)

    @AfterEach
    fun unbindMain() = Dispatchers.resetMain()

    @Test
    fun `delete all trainings`() = runTest(testDispatcher) {
        val trainingsUuids = listOf(
            Uuid.random().toString(),
            Uuid.random().toString(),
            Uuid.random().toString(),
        )

        coEvery { trainingRepository.removeAll(trainingsUuids) } returns Unit

        interactor.deleteAll(trainingsUuids)

        coVerify(exactly = 1) { trainingRepository.removeAll(trainingsUuids) }
    }

    @Test
    fun `delete all with empty list`() = runTest(testDispatcher) {
        val emptyList = emptyList<String>()

        coEvery { trainingRepository.removeAll(emptyList) } returns Unit

        interactor.deleteAll(emptyList)

        coVerify(exactly = 1) { trainingRepository.removeAll(emptyList) }
    }

    @Test
    fun `delete all with single training`() = runTest(testDispatcher) {
        val singleUuid = listOf(Uuid.random().toString())

        coEvery { trainingRepository.removeAll(singleUuid) } returns Unit

        interactor.deleteAll(singleUuid)

        coVerify(exactly = 1) { trainingRepository.removeAll(singleUuid) }
    }
}
