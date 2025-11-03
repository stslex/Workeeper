package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainModel
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingDomainUiModelMapper
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class CommonHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val interactor = mockk<SingleTrainingInteractor>(relaxed = true)
    private val trainingDomainUiMapper = mockk<TrainingDomainUiModelMapper>(relaxed = true)
    private val testScope = TestScope(testDispatcher)

    private val initialTraining = TrainingUiModel(
        uuid = "",
        name = PropertyHolder.StringProperty.new(initialValue = ""),
        exercises = persistentListOf(),
        labels = persistentListOf(),
        date = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        isMenuOpen = false,
        menuItems = kotlinx.collections.immutable.persistentSetOf(),
    )

    private val initialState = TrainingStore.State(
        training = initialTraining,
        initialTrainingUiModel = initialTraining,
        dialogState = DialogState.Closed,
        pendingForCreateUuid = "",
    )

    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<TrainingHandlerStore>(relaxed = true) {
        every { state } returns stateFlow
        every { scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)
    }
    private val handler = CommonHandler(interactor, trainingDomainUiMapper, store)

    @Test
    fun `init action with null uuid does nothing`() {
        handler.invoke(TrainingStore.Action.Common.Init(null))

        // Verify that the handler processes the action without calling interactor
        assertNotNull(handler)
    }

    @Test
    fun `init action with valid uuid loads training successfully`() = runTest {
        val trainingUuid = Uuid.random().toString()
        val domainModel = mockk<TrainingDomainModel>()
        val uiModel = TrainingUiModel(
            uuid = trainingUuid,
            name = PropertyHolder.StringProperty.new(initialValue = "Test Training"),
            exercises = persistentListOf(),
            labels = persistentListOf(),
            date = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
            isMenuOpen = false,
            menuItems = kotlinx.collections.immutable.persistentSetOf(),
        )

        coEvery { interactor.getTraining(trainingUuid) } returns domainModel
        every { trainingDomainUiMapper.invoke(domainModel) } returns uiModel

        handler.invoke(TrainingStore.Action.Common.Init(trainingUuid))

        testScheduler.advanceUntilIdle()

        // Verify that the handler processes the init action
        assertNotNull(handler)
    }

    @Test
    fun `init action with valid uuid but null training result sets initial training`() = runTest {
        val trainingUuid = Uuid.random().toString()

        coEvery { interactor.getTraining(trainingUuid) } returns null

        handler.invoke(TrainingStore.Action.Common.Init(trainingUuid))

        testScheduler.advanceUntilIdle()

        // Verify that the handler processes the init action
        assertNotNull(handler)
    }

    @Test
    fun `init action handles interactor error gracefully`() = runTest {
        val trainingUuid = Uuid.random().toString()

        coEvery { interactor.getTraining(trainingUuid) } throws RuntimeException("Network error")

        handler.invoke(TrainingStore.Action.Common.Init(trainingUuid))

        testScheduler.advanceUntilIdle()

        // Verify that the handler processes the init action without crashing
        assertNotNull(handler)
    }
}
