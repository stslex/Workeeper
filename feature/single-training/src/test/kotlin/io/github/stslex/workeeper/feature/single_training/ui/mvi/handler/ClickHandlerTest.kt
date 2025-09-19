package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainChangeModel
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingChangeMapper
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

internal class ClickHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val interactor = mockk<SingleTrainingInteractor>(relaxed = true)
    private val changeMapper = mockk<TrainingChangeMapper>(relaxed = true)
    private val store = mockk<TrainingHandlerStore>(relaxed = true)
    private val testScope = TestScope(testDispatcher)

    private val initialTraining = TrainingUiModel(
        uuid = "",
        name = "",
        exercises = persistentListOf(),
        labels = persistentListOf(),
        date = DateProperty.new(System.currentTimeMillis())
    )

    private val initialState = TrainingStore.State(
        training = initialTraining,
        dialogState = DialogState.Closed
    )

    private val stateFlow = MutableStateFlow(initialState)
    private val handler = ClickHandler(interactor, changeMapper, testDispatcher, store)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { store.state } returns stateFlow
        every { store.scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)

        // Mock the launch function to actually execute the coroutine
        every {
            store.launch<Any>(
                onError = any(),
                onSuccess = any(),
                workDispatcher = any(),
                eachDispatcher = any(),
                action = any()
            )
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any>(4)

            testScope.launch {
                try {
                    val result = action()
                    onSuccess(this, result)
                } catch (_: Exception) {
                    // Handle error if needed
                }
            }
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `close action triggers pop back navigation`() {
        handler.invoke(TrainingStore.Action.Click.Close)

        verify { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `open calendar picker sets dialog state to calendar`() {
        handler.invoke(TrainingStore.Action.Click.OpenCalendarPicker)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(DialogState.Calendar, newState.dialogState)
    }

    @Test
    fun `close calendar picker sets dialog state to closed`() {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.Calendar)

        handler.invoke(TrainingStore.Action.Click.CloseCalendarPicker)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(DialogState.Closed, newState.dialogState)
    }

    @Test
    fun `save with valid name updates training and navigates back`() = runTest {
        val validTraining = initialTraining.copy(name = "Valid Training Name")
        val changeModel = mockk<TrainingDomainChangeModel>()

        stateFlow.value = stateFlow.value.copy(training = validTraining)

        every { changeMapper.invoke(validTraining) } returns changeModel
        coEvery { interactor.updateTraining(changeModel) } returns Unit

        handler.invoke(TrainingStore.Action.Click.Save)

        testScheduler.advanceUntilIdle()

        coVerify { interactor.updateTraining(changeModel) }
        verify { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `save with empty name does nothing`() = runTest {
        val invalidTraining = initialTraining.copy(name = "")
        stateFlow.value = stateFlow.value.copy(training = invalidTraining)

        handler.invoke(TrainingStore.Action.Click.Save)

        coVerify(exactly = 0) { interactor.updateTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `save with blank name does nothing`() = runTest {
        val invalidTraining = initialTraining.copy(name = "   ")
        stateFlow.value = stateFlow.value.copy(training = invalidTraining)

        handler.invoke(TrainingStore.Action.Click.Save)

        coVerify(exactly = 0) { interactor.updateTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `delete with valid uuid removes training and navigates back`() = runTest {
        val trainingUuid = Uuid.random().toString()
        val trainingWithUuid = initialTraining.copy(uuid = trainingUuid)

        stateFlow.value = stateFlow.value.copy(training = trainingWithUuid)

        coEvery { interactor.removeTraining(trainingUuid) } returns Unit

        handler.invoke(TrainingStore.Action.Click.Delete)

        testScheduler.advanceUntilIdle()

        coVerify { interactor.removeTraining(trainingUuid) }
        verify { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `delete with empty uuid does nothing`() = runTest {
        val trainingWithoutUuid = initialTraining.copy(uuid = "")
        stateFlow.value = stateFlow.value.copy(training = trainingWithoutUuid)

        handler.invoke(TrainingStore.Action.Click.Delete)

        coVerify(exactly = 0) { interactor.removeTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `delete with blank uuid does nothing`() = runTest {
        val trainingWithBlankUuid = initialTraining.copy(uuid = "   ")
        stateFlow.value = stateFlow.value.copy(training = trainingWithBlankUuid)

        handler.invoke(TrainingStore.Action.Click.Delete)

        coVerify(exactly = 0) { interactor.removeTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }
}