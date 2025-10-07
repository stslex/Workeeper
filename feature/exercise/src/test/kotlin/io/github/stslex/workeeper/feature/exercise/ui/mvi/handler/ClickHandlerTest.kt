package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mappers.ExerciseUiMap
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetUiType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val interactor = mockk<ExerciseInteractor>()
    private val exerciseUiMap = mockk<ExerciseUiMap>(relaxed = true)
    private val testScope = TestScope(testDispatcher)

    private val initialState = ExerciseStore.State(
        uuid = "test-uuid",
        name = PropertyHolder.StringProperty.new(initialValue = "Test Exercise"),
        sets = persistentListOf(),
        dateProperty = PropertyHolder.DateProperty.new(initialValue = 1000000L),
        dialogState = DialogState.Closed,
        isMenuOpen = false,
        menuItems = persistentSetOf(),
        trainingUuid = "training-uuid",
        labels = persistentListOf(),
        initialHash = 0,
    )

    private val stateFlow = MutableStateFlow(initialState)
    private val store = mockk<ExerciseHandlerStore>(relaxed = true) {
        every { state } returns stateFlow
        every { scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)

        // Mock the updateState function to actually update the state
        every { updateState(any()) } answers {
            val transform = arg<(ExerciseStore.State) -> ExerciseStore.State>(0)

            runCatching { stateFlow.value = transform(stateFlow.value) }
        }
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
    private val handler = ClickHandler(interactor, exerciseUiMap, testDispatcher, store)

    @Test
    fun `cancel action triggers back with confirmation navigation`() {
        handler.invoke(ExerciseStore.Action.Click.Cancel)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.consume(ExerciseStore.Action.NavigationMiddleware.BackWithConfirmation) }
    }

    @Test
    fun `save action with valid data saves exercise and navigates back`() = runTest {
        coEvery { interactor.saveItem(any()) } just runs
        val validName = PropertyHolder.StringProperty.new(initialValue = "Valid Exercise")
        val validSet = SetsUiModel(
            uuid = "set-uuid",
            reps = PropertyHolder.IntProperty.new(initialValue = 10),
            weight = PropertyHolder.DoubleProperty.new(initialValue = 50.0),
            type = SetUiType.WORK,
        )
        stateFlow.value = stateFlow.value.copy(
            name = validName,
            sets = listOf(validSet).toImmutableList(),
        )

        handler.invoke(ExerciseStore.Action.Click.Save)

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        coVerify(exactly = 1) { interactor.saveItem(any()) }
        verify(exactly = 1) { store.consume(ExerciseStore.Action.NavigationMiddleware.Back) }
    }

    @Test
    fun `save action with invalid name sends invalid params event`() {
        val invalidName = PropertyHolder.StringProperty.new(initialValue = "")
        stateFlow.value = stateFlow.value.copy(name = invalidName)

        handler.invoke(ExerciseStore.Action.Click.Save)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.InvalidParams) }
        coVerify(exactly = 0) { interactor.saveItem(any()) }
    }

    @Test
    fun `delete action sends delete snackbar event`() {
        handler.invoke(ExerciseStore.Action.Click.Delete)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DELETE)) }
    }

    @Test
    fun `confirmed delete action with valid uuid deletes exercise and navigates back`() = runTest {
        coEvery { interactor.deleteItem(any()) } just runs

        val exerciseUuid = "valid-uuid"
        stateFlow.value = stateFlow.value.copy(uuid = exerciseUuid)

        handler.invoke(ExerciseStore.Action.Click.ConfirmedDelete)

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        coVerify(exactly = 1) { interactor.deleteItem(exerciseUuid) }
        verify(exactly = 1) { store.consume(ExerciseStore.Action.NavigationMiddleware.Back) }
    }

    @Test
    fun `confirmed delete action with null uuid does nothing`() {
        coEvery { interactor.deleteItem(any()) } just runs
        stateFlow.value = stateFlow.value.copy(uuid = null)

        handler.invoke(ExerciseStore.Action.Click.ConfirmedDelete)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        coVerify(exactly = 0) { interactor.deleteItem(any()) }
    }

    @Test
    fun `pick date action opens calendar dialog`() {
        handler.invoke(ExerciseStore.Action.Click.PickDate)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(DialogState.Calendar, stateFlow.value.dialogState)
    }

    @Test
    fun `close dialog action closes dialog`() {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.Calendar)

        handler.invoke(ExerciseStore.Action.Click.CloseDialog)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(DialogState.Closed, stateFlow.value.dialogState)
    }

    @Test
    fun `open menu variants action opens menu`() {
        handler.invoke(ExerciseStore.Action.Click.OpenMenuVariants)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertTrue(stateFlow.value.isMenuOpen)
    }

    @Test
    fun `close menu variants action closes menu`() {
        stateFlow.value = stateFlow.value.copy(isMenuOpen = true)

        handler.invoke(ExerciseStore.Action.Click.CloseMenuVariants)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertFalse(stateFlow.value.isMenuOpen)
    }

    @Test
    fun `menu item click updates exercise data and closes menu`() {
        val exerciseUiModel = mockk<ExerciseUiModel> {
            every { name } returns "Menu Exercise"
            every { sets } returns persistentListOf()
            every { timestamp } returns 2000000L
        }
        val menuItem = MenuItem(
            uuid = "menu-uuid",
            text = "Menu Exercise",
            itemModel = exerciseUiModel,
        )
        stateFlow.value = stateFlow.value.copy(isMenuOpen = true)

        handler.invoke(ExerciseStore.Action.Click.OnMenuItemClick(menuItem))

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals("Menu Exercise", stateFlow.value.name.value)
        assertEquals(2000000L, stateFlow.value.dateProperty.value)
        assertFalse(stateFlow.value.isMenuOpen)
    }

    @Test
    fun `dialog sets open create action opens sets dialog with new set`() {
        handler.invoke(ExerciseStore.Action.Click.DialogSets.OpenCreate)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertTrue(stateFlow.value.dialogState is DialogState.Sets)
        val dialogState = stateFlow.value.dialogState as DialogState.Sets
        assertTrue(dialogState.set.uuid.isNotBlank())
    }

    @Test
    fun `dialog sets open edit action opens sets dialog with provided set`() {
        val existingSet = SetsUiModel(
            uuid = "existing-set",
            reps = PropertyHolder.IntProperty.new(),
            weight = PropertyHolder.DoubleProperty.new(),
            type = SetUiType.WORK,
        )

        handler.invoke(ExerciseStore.Action.Click.DialogSets.OpenEdit(existingSet))

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertTrue(stateFlow.value.dialogState is DialogState.Sets)
        val dialogState = stateFlow.value.dialogState as DialogState.Sets
        assertEquals(existingSet, dialogState.set)
    }

    @Test
    fun `dialog sets save button adds new set to list`() {
        val newSet = SetsUiModel(
            uuid = "new-set",
            reps = PropertyHolder.IntProperty.new(initialValue = 12),
            weight = PropertyHolder.DoubleProperty.new(initialValue = 60.0),
            type = SetUiType.WORK,
        )

        handler.invoke(ExerciseStore.Action.Click.DialogSets.SaveButton(newSet))

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(1, stateFlow.value.sets.size)
        assertEquals(newSet, stateFlow.value.sets.first())
        assertEquals(DialogState.Closed, stateFlow.value.dialogState)
    }

    @Test
    fun `dialog sets save button updates existing set in list`() {
        val existingSet = SetsUiModel(
            uuid = "existing-set",
            reps = PropertyHolder.IntProperty.new(initialValue = 10),
            weight = PropertyHolder.DoubleProperty.new(initialValue = 50.0),
            type = SetUiType.WORK,
        )
        val updatedSet = existingSet.copy(
            reps = PropertyHolder.IntProperty.new(initialValue = 15),
        )
        stateFlow.value = stateFlow.value.copy(sets = listOf(existingSet).toImmutableList())

        handler.invoke(ExerciseStore.Action.Click.DialogSets.SaveButton(updatedSet))

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(1, stateFlow.value.sets.size)
        assertEquals(15, stateFlow.value.sets.first().reps.value)
        assertEquals(DialogState.Closed, stateFlow.value.dialogState)
    }

    @Test
    fun `dialog sets delete button removes set from list`() {
        val setToDelete = SetsUiModel(
            uuid = "delete-me",
            reps = PropertyHolder.IntProperty.new(),
            weight = PropertyHolder.DoubleProperty.new(),
            type = SetUiType.WORK,
        )
        val setToKeep = SetsUiModel(
            uuid = "keep-me",
            reps = PropertyHolder.IntProperty.new(),
            weight = PropertyHolder.DoubleProperty.new(),
            type = SetUiType.WORK,
        )
        stateFlow.value = stateFlow.value.copy(
            sets = listOf(setToDelete, setToKeep).toImmutableList(),
        )

        handler.invoke(ExerciseStore.Action.Click.DialogSets.DeleteButton("delete-me"))

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(1, stateFlow.value.sets.size)
        assertEquals("keep-me", stateFlow.value.sets.first().uuid)
        assertEquals(DialogState.Closed, stateFlow.value.dialogState)
    }

    @Test
    fun `dialog sets cancel button closes dialog`() {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.Sets(SetsUiModel.EMPTY))

        handler.invoke(ExerciseStore.Action.Click.DialogSets.CancelButton)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.HapticClick) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(DialogState.Closed, stateFlow.value.dialogState)
    }
}
