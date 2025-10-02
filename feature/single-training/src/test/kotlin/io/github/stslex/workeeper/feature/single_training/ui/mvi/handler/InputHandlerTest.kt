package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class InputHandlerTest {

    private val initialTraining = TrainingUiModel(
        uuid = "test-uuid",
        name = "Initial Training",
        exercises = persistentListOf(),
        labels = persistentListOf(),
        date = PropertyHolder.DateProperty.new(initialValue = 1000000L),
    )

    private val initialState = TrainingStore.State(
        training = initialTraining,
        dialogState = DialogState.Closed,
        pendingForCreateUuid = "",
    )

    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<TrainingHandlerStore>(relaxed = true) {
        every { state } returns stateFlow

        // Mock the updateState function to actually update the state
        every { updateState(any()) } answers {
            val transform = arg<(TrainingStore.State) -> TrainingStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }
    }

    private val handler = InputHandler(store)

    @Test
    fun `name input action updates training name`() {
        val newName = "Updated Training Name"

        handler.invoke(TrainingStore.Action.Input.Name(newName))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(newName, stateFlow.value.training.name)
        assertEquals(initialTraining.uuid, stateFlow.value.training.uuid)
        assertEquals(initialTraining.exercises, stateFlow.value.training.exercises)
        assertEquals(initialTraining.labels, stateFlow.value.training.labels)
        assertEquals(initialTraining.date, stateFlow.value.training.date)
    }

    @Test
    fun `name input action with empty string updates training name`() {
        val emptyName = ""

        handler.invoke(TrainingStore.Action.Input.Name(emptyName))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(emptyName, stateFlow.value.training.name)
    }

    @Test
    fun `name input action with whitespace updates training name`() {
        val whitespaceName = "   "

        handler.invoke(TrainingStore.Action.Input.Name(whitespaceName))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(whitespaceName, stateFlow.value.training.name)
    }

    @Test
    fun `date input action updates training date`() {
        val newTimestamp = 2000000L

        handler.invoke(TrainingStore.Action.Input.Date(newTimestamp))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(newTimestamp, stateFlow.value.training.date.value)
        assertEquals(initialTraining.uuid, stateFlow.value.training.uuid)
        assertEquals(initialTraining.name, stateFlow.value.training.name)
        assertEquals(initialTraining.exercises, stateFlow.value.training.exercises)
        assertEquals(initialTraining.labels, stateFlow.value.training.labels)
    }

    @Test
    fun `date input action with zero timestamp updates training date`() {
        val zeroTimestamp = 0L

        handler.invoke(TrainingStore.Action.Input.Date(zeroTimestamp))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(zeroTimestamp, stateFlow.value.training.date.value)
    }

    @Test
    fun `date input action with negative timestamp updates training date`() {
        val negativeTimestamp = -1000L

        handler.invoke(TrainingStore.Action.Input.Date(negativeTimestamp))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(negativeTimestamp, stateFlow.value.training.date.value)
    }

    @Test
    fun `multiple input actions update state correctly`() {
        val newName = "Multiple Action Test"
        val newTimestamp = 3000000L

        handler.invoke(TrainingStore.Action.Input.Name(newName))
        handler.invoke(TrainingStore.Action.Input.Date(newTimestamp))

        verify(exactly = 2) { store.updateState(any()) }
        assertEquals(newName, stateFlow.value.training.name)
        assertEquals(newTimestamp, stateFlow.value.training.date.value)
    }

    @Test
    fun `input actions preserve dialog state`() {
        // Set dialog state to Calendar
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.Calendar)

        handler.invoke(TrainingStore.Action.Input.Name("Test"))

        assertEquals(DialogState.Calendar, stateFlow.value.dialogState)

        handler.invoke(TrainingStore.Action.Input.Date(5000000L))

        assertEquals(DialogState.Calendar, stateFlow.value.dialogState)
    }

    @Test
    fun `state update transformation is captured correctly`() {
        val newName = "Captured Name"
        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()

        every { store.updateState(capture(stateSlot)) } returns Unit

        handler.invoke(TrainingStore.Action.Input.Name(newName))

        verify(exactly = 1) { store.updateState(any()) }

        // Test the captured transformation function
        val transformedState = stateSlot.captured(initialState)
        assertEquals(newName, transformedState.training.name)
        assertEquals(initialState.training.uuid, transformedState.training.uuid)
        assertEquals(initialState.dialogState, transformedState.dialogState)
    }
}
