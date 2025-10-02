package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ClickHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val interactor = mockk<SingleTrainingInteractor>(relaxed = true)
    private val changeMapper = mockk<TrainingChangeMapper>(relaxed = true)
    private val testScope = TestScope(testDispatcher)

    private val testTrainingUuid = "test-training-uuid"

    private val initialTraining = TrainingUiModel(
        uuid = testTrainingUuid,
        name = "Test Training",
        exercises = persistentListOf(),
        labels = persistentListOf(),
        date = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
    )

    private val initialState = TrainingStore.State(
        training = initialTraining,
        pendingForCreateUuid = "",
        dialogState = DialogState.Closed,
    )

    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<TrainingHandlerStore>(relaxed = true) {
        every { state } returns stateFlow
        every { scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)

        // Mock the launch function to actually execute the coroutine
        every {
            this@mockk.launch<Any>(
                onError = any(),
                onSuccess = any(),
                workDispatcher = any(),
                eachDispatcher = any(),
                action = any(),
            )
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any>(4)

            testScope.launch { runCatching { onSuccess(this, action()) } }
        }
    }
    private val handler = ClickHandler(interactor, changeMapper, testDispatcher, store)

    @Test
    fun `create exercise click action triggers navigation to new exercise screen`() {
        handler.invoke(TrainingStore.Action.Click.CreateExercise)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) {
            store.consume(
                TrainingStore.Action.Navigation.CreateExercise(
                    trainingUuid = testTrainingUuid,
                ),
            )
        }
    }

    @Test
    fun `exercise click action triggers navigation to current exercise screen`() {
        val exerciseUuid = "expected_uuid"
        handler.invoke(TrainingStore.Action.Click.ExerciseClick(exerciseUuid))

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) {
            store.consume(
                TrainingStore.Action.Navigation.OpenExercise(
                    exerciseUuid = exerciseUuid,
                    trainingUuid = testTrainingUuid,
                ),
            )
        }
    }

    @Test
    fun `close action triggers pop back navigation`() {
        handler.invoke(TrainingStore.Action.Click.Close)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `open calendar picker sets dialog state to calendar`() {
        handler.invoke(TrainingStore.Action.Click.OpenCalendarPicker)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(DialogState.Calendar, newState.dialogState)
    }

    @Test
    fun `close calendar picker sets dialog state to closed`() {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.Calendar)

        handler.invoke(TrainingStore.Action.Click.CloseCalendarPicker)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

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

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 1) { interactor.updateTraining(changeModel) }
        verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `save with empty name does nothing`() = runTest {
        val invalidTraining = initialTraining.copy(name = "")
        stateFlow.value = stateFlow.value.copy(training = invalidTraining)

        handler.invoke(TrainingStore.Action.Click.Save)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 0) { interactor.updateTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `save with blank name does nothing`() = runTest {
        val invalidTraining = initialTraining.copy(name = "   ")
        stateFlow.value = stateFlow.value.copy(training = invalidTraining)

        handler.invoke(TrainingStore.Action.Click.Save)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
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

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 1) { interactor.removeTraining(trainingUuid) }
        verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `delete with empty uuid does nothing`() = runTest {
        val trainingWithoutUuid = initialTraining.copy(uuid = "")
        stateFlow.value = stateFlow.value.copy(training = trainingWithoutUuid)

        handler.invoke(TrainingStore.Action.Click.Delete)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 0) { interactor.removeTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `delete with blank uuid does nothing`() = runTest {
        val trainingWithBlankUuid = initialTraining.copy(uuid = "   ")
        stateFlow.value = stateFlow.value.copy(training = trainingWithBlankUuid)

        handler.invoke(TrainingStore.Action.Click.Delete)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 0) { interactor.removeTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }
}
