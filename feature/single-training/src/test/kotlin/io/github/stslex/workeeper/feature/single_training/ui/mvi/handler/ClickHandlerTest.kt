package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem
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
import kotlinx.collections.immutable.persistentSetOf
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
        name = PropertyHolder.StringProperty.new(initialValue = "Test Training"),
        exercises = persistentListOf(),
        labels = persistentListOf(),
        date = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        isMenuOpen = false,
        menuItems = persistentSetOf(),
    )

    private val initialState = TrainingStore.State(
        training = initialTraining,
        pendingForCreateUuid = "",
        dialogState = DialogState.Closed,
        initialTrainingUiModel = initialTraining,
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
    fun `close action triggers pop back navigation when state unchanged`() {
        handler.invoke(TrainingStore.Action.Click.Close)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `close action shows exit dialog when state has changed`() {
        val modifiedTraining = initialTraining.copy(
            name = PropertyHolder.StringProperty.new(initialValue = "Modified Training Name"),
        )
        stateFlow.value = stateFlow.value.copy(training = modifiedTraining)

        handler.invoke(TrainingStore.Action.Click.Close)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(DialogState.ConfirmDialog.ExitWithoutSaving, newState.dialogState)
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
        val validTraining = initialTraining.copy(
            name = PropertyHolder.StringProperty.new(initialValue = "Valid Training Name"),
        )
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
        val invalidTraining =
            initialTraining.copy(name = PropertyHolder.StringProperty.new(initialValue = ""))
        stateFlow.value = stateFlow.value.copy(training = invalidTraining)

        handler.invoke(TrainingStore.Action.Click.Save)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 0) { interactor.updateTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `save with blank name does nothing`() = runTest {
        val invalidTraining =
            initialTraining.copy(name = PropertyHolder.StringProperty.new(initialValue = "   "))
        stateFlow.value = stateFlow.value.copy(training = invalidTraining)

        handler.invoke(TrainingStore.Action.Click.Save)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 0) { interactor.updateTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `delete dialog open sets dialog state to delete training`() {
        handler.invoke(TrainingStore.Action.Click.DeleteDialogOpen)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(DialogState.ConfirmDialog.Delete, newState.dialogState)
    }

    @Test
    fun `dialog delete training dismiss sets dialog state to closed`() {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.ConfirmDialog.Delete)

        handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Dismiss)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(DialogState.Closed, newState.dialogState)
    }

    @Test
    fun `dialog delete training confirm with valid uuid removes training and navigates back`() =
        runTest {
            val trainingUuid = Uuid.random().toString()
            val trainingWithUuid = initialTraining.copy(uuid = trainingUuid)

            stateFlow.value = stateFlow.value.copy(
                training = trainingWithUuid,
                dialogState = DialogState.ConfirmDialog.Delete,
            )

            coEvery { interactor.removeTraining(trainingUuid) } returns Unit

            handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Confirm)

            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
            coVerify(exactly = 1) { interactor.removeTraining(trainingUuid) }
            verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.PopBack) }
        }

    @Test
    fun `dialog delete training confirm with pending uuid removes training and navigates back`() =
        runTest {
            val pendingUuid = Uuid.random().toString()
            val trainingWithoutUuid = initialTraining.copy(uuid = "")

            stateFlow.value = stateFlow.value.copy(
                training = trainingWithoutUuid,
                pendingForCreateUuid = pendingUuid,
                dialogState = DialogState.ConfirmDialog.Delete,
            )

            coEvery { interactor.removeTraining(pendingUuid) } returns Unit

            handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Confirm)

            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
            coVerify(exactly = 1) { interactor.removeTraining(pendingUuid) }
            verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.PopBack) }
        }

    @Test
    fun `dialog delete training confirm with empty uuid does nothing`() = runTest {
        val trainingWithoutUuid = initialTraining.copy(uuid = "")
        stateFlow.value =
            stateFlow.value.copy(training = trainingWithoutUuid, pendingForCreateUuid = "")

        handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Confirm)

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 0) { interactor.removeTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `dialog delete training confirm with blank uuid does nothing`() = runTest {
        val trainingWithBlankUuid = initialTraining.copy(uuid = "   ")
        stateFlow.value =
            stateFlow.value.copy(training = trainingWithBlankUuid, pendingForCreateUuid = "   ")

        handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Confirm)

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 0) { interactor.removeTraining(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `menu open sets menu open state to true`() {
        handler.invoke(TrainingStore.Action.Click.Menu.Open)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(true, newState.training.isMenuOpen)
    }

    @Test
    fun `menu close sets menu open state to false`() {
        val trainingWithMenuOpen = initialTraining.copy(isMenuOpen = true)
        stateFlow.value = stateFlow.value.copy(training = trainingWithMenuOpen)

        handler.invoke(TrainingStore.Action.Click.Menu.Close)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(false, newState.training.isMenuOpen)
    }

    @Test
    fun `menu item click updates training with item model and closes menu`() {
        val updatedTraining = initialTraining.copy(
            name = PropertyHolder.StringProperty.new(initialValue = "Updated Training"),
            isMenuOpen = true,
        )
        val menuItem = MenuItem(
            uuid = "menu-item-uuid",
            text = "Menu Item",
            itemModel = updatedTraining,
        )

        handler.invoke(TrainingStore.Action.Click.Menu.Item(menuItem))

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(false, newState.training.isMenuOpen)
        assertEquals(updatedTraining.name.value, newState.training.name.value)
    }

    @Test
    fun `create exercise with existing uuid navigates with that uuid`() {
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
    fun `create exercise with empty uuid but pending uuid navigates with pending uuid`() {
        val pendingUuid = "pending-uuid-123"
        val trainingWithoutUuid = initialTraining.copy(uuid = "")
        stateFlow.value = stateFlow.value.copy(
            training = trainingWithoutUuid,
            pendingForCreateUuid = pendingUuid,
        )

        handler.invoke(TrainingStore.Action.Click.CreateExercise)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) {
            store.consume(
                TrainingStore.Action.Navigation.CreateExercise(
                    trainingUuid = pendingUuid,
                ),
            )
        }
    }

    @Test
    fun `create exercise without uuid generates new uuid and updates state`() {
        val trainingWithoutUuid = initialTraining.copy(uuid = "")
        stateFlow.value =
            stateFlow.value.copy(training = trainingWithoutUuid, pendingForCreateUuid = "")

        handler.invoke(TrainingStore.Action.Click.CreateExercise)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }
        verify(exactly = 1) {
            store.consume(
                match { action ->
                    action is TrainingStore.Action.Navigation.CreateExercise &&
                        action.trainingUuid.isNotBlank()
                },
            )
        }

        val newState = stateSlot.captured(stateFlow.value)
        assert(newState.pendingForCreateUuid.isNotBlank())
    }

    @Test
    fun `exercise click with existing uuid navigates with that uuid`() {
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
    fun `exercise click with empty uuid but pending uuid navigates with pending uuid`() {
        val pendingUuid = "pending-uuid-456"
        val exerciseUuid = "exercise-uuid-789"
        val trainingWithoutUuid = initialTraining.copy(uuid = "")
        stateFlow.value = stateFlow.value.copy(
            training = trainingWithoutUuid,
            pendingForCreateUuid = pendingUuid,
        )

        handler.invoke(TrainingStore.Action.Click.ExerciseClick(exerciseUuid))

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) {
            store.consume(
                TrainingStore.Action.Navigation.OpenExercise(
                    exerciseUuid = exerciseUuid,
                    trainingUuid = pendingUuid,
                ),
            )
        }
    }

    @Test
    fun `exercise click without uuid generates new uuid and updates state`() {
        val exerciseUuid = "exercise-uuid-123"
        val trainingWithoutUuid = initialTraining.copy(uuid = "")
        stateFlow.value =
            stateFlow.value.copy(training = trainingWithoutUuid, pendingForCreateUuid = "")

        handler.invoke(TrainingStore.Action.Click.ExerciseClick(exerciseUuid))

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }
        verify(exactly = 1) {
            store.consume(
                match { action ->
                    action is TrainingStore.Action.Navigation.OpenExercise &&
                        action.exerciseUuid == exerciseUuid &&
                        action.trainingUuid.isNotBlank()
                },
            )
        }

        val newState = stateSlot.captured(stateFlow.value)
        assert(newState.pendingForCreateUuid.isNotBlank())
    }

    @Test
    fun `save with pending uuid uses pending uuid for update`() = runTest {
        val pendingUuid = "pending-uuid-save"
        val validTraining = initialTraining.copy(
            uuid = "",
            name = PropertyHolder.StringProperty.new(initialValue = "Valid Training Name"),
        )
        val changeModel = mockk<TrainingDomainChangeModel>()

        stateFlow.value = stateFlow.value.copy(
            training = validTraining,
            pendingForCreateUuid = pendingUuid,
        )

        every { changeMapper.invoke(validTraining.copy(uuid = pendingUuid)) } returns changeModel
        coEvery { interactor.updateTraining(changeModel) } returns Unit

        handler.invoke(TrainingStore.Action.Click.Save)

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 1) { interactor.updateTraining(changeModel) }
        verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `dialog exit without saving dismiss sets dialog state to closed`() {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.ConfirmDialog.ExitWithoutSaving)

        handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Dismiss)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(DialogState.Closed, newState.dialogState)
    }

    @Test
    fun `dialog exit without saving confirm closes dialog and navigates back`() {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.ConfirmDialog.ExitWithoutSaving)

        handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Confirm)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }
        verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.PopBack) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(DialogState.Closed, newState.dialogState)
    }

    @Test
    fun `confirm dialog confirm on delete state triggers delete flow`() = runTest {
        val trainingUuid = Uuid.random().toString()
        val trainingWithUuid = initialTraining.copy(uuid = trainingUuid)

        stateFlow.value = stateFlow.value.copy(
            training = trainingWithUuid,
            dialogState = DialogState.ConfirmDialog.Delete,
        )

        coEvery { interactor.removeTraining(trainingUuid) } returns Unit

        handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Confirm)

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 1) { interactor.removeTraining(trainingUuid) }
        verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.PopBack) }
    }

    @Test
    fun `confirm dialog confirm on closed state does nothing`() = runTest {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.Closed)

        handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Confirm)

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 0) { interactor.removeTraining(any()) }
        verify(exactly = 0) { store.consume(any()) }
        verify(exactly = 0) { store.updateState(any()) }
    }

    @Test
    fun `confirm dialog confirm on calendar state does nothing`() = runTest {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.Calendar)

        handler.invoke(TrainingStore.Action.Click.ConfirmDialog.Confirm)

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick)) }
        coVerify(exactly = 0) { interactor.removeTraining(any()) }
        verify(exactly = 0) { store.consume(any()) }
        verify(exactly = 0) { store.updateState(any()) }
    }
}
