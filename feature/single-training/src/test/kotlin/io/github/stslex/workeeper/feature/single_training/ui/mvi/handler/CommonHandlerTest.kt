package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainModel
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingDomainUiModelMapper
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class CommonHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val interactor = mockk<SingleTrainingInteractor>(relaxed = true)
    private val trainingDomainUiMapper = mockk<TrainingDomainUiModelMapper>(relaxed = true)
    private val store = mockk<TrainingHandlerStore>(relaxed = true)
    private val testScope = TestScope(testDispatcher)

    private val initialState = TrainingStore.State(
        training = TrainingUiModel(
            uuid = "",
            name = "",
            exercises = persistentListOf(),
            labels = persistentListOf(),
            date = DateProperty.new(System.currentTimeMillis())
        ),
        dialogState = DialogState.Closed
    )

    private val stateFlow = MutableStateFlow(initialState)
    private val handler = CommonHandler(interactor, trainingDomainUiMapper, store)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { store.state } returns stateFlow
        every { store.scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init action with null uuid does nothing`() {
        handler.invoke(TrainingStore.Action.Common.Init(null))

        coVerify(exactly = 0) { interactor.getTraining(any()) }
    }

    @Test
    fun `init action with valid uuid loads training successfully`() = runTest {
        val trainingUuid = Uuid.random().toString()
        val domainModel = mockk<TrainingDomainModel>()
        val uiModel = TrainingUiModel(
            uuid = trainingUuid,
            name = "Test Training",
            exercises = persistentListOf(),
            labels = persistentListOf(),
            date = DateProperty.new(System.currentTimeMillis())
        )

        coEvery { interactor.getTraining(trainingUuid) } returns domainModel
        every { trainingDomainUiMapper.invoke(domainModel) } returns uiModel

        handler.invoke(TrainingStore.Action.Common.Init(trainingUuid))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { interactor.getTraining(trainingUuid) }
    }

    @Test
    fun `init action with valid uuid but null training result sets initial training`() = runTest {
        val trainingUuid = Uuid.random().toString()

        coEvery { interactor.getTraining(trainingUuid) } returns null

        handler.invoke(TrainingStore.Action.Common.Init(trainingUuid))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { interactor.getTraining(trainingUuid) }
    }

    @Test
    fun `init action handles interactor error gracefully`() = runTest {
        val trainingUuid = Uuid.random().toString()

        coEvery { interactor.getTraining(trainingUuid) } throws RuntimeException("Network error")

        handler.invoke(TrainingStore.Action.Common.Init(trainingUuid))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { interactor.getTraining(trainingUuid) }
    }
}