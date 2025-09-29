package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ClickHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val repository = mockk<ExerciseRepository>(relaxed = true)

    private val testScope = TestScope(testDispatcher)

    private val pagingUiState = mockk<PagingUiState<PagingData<ExerciseUiModel>>>(relaxed = true)

    private val initialState = ExercisesStore.State(
        items = pagingUiState,
        selectedItems = persistentSetOf(),
        query = "",
    )

    private val stateFlow = MutableStateFlow(initialState)

    private val store: ExerciseHandlerStore = mockk<ExerciseHandlerStore>(relaxed = true) {
        every { this@mockk.state } returns stateFlow
        every { this@mockk.scope } returns AppCoroutineScope(
            testScope,
            testDispatcher,
            testDispatcher,
        )

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

    private val handler = ClickHandler(repository, store)

    @Test
    fun `float button click with no selected items navigates to create exercise`() {
        handler.invoke(ExercisesStore.Action.Click.FloatButtonClick)

        verify { store.consume(ExercisesStore.Action.Navigation.CreateExerciseDialog) }
        verify { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `float button click with selected items deletes them`() = runTest {
        val exerciseUuid1 = Uuid.random()
        val exerciseUuid2 = Uuid.random()

        val exercise1 = ExerciseUiModel(
            uuid = exerciseUuid1.toString(),
            name = "Exercise 1",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        val exercise2 = ExerciseUiModel(
            uuid = exerciseUuid2.toString(),
            name = "Exercise 2",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        val selectedItems = setOf(exercise1.uuid, exercise2.uuid).toImmutableSet()
        stateFlow.value = stateFlow.value.copy(selectedItems = selectedItems)

        coEvery { repository.deleteAllItems(any()) } returns Unit

        handler.invoke(ExercisesStore.Action.Click.FloatButtonClick)

        // Verify immediate behavior
        verify(exactly = 1) { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.Confirm)) }

        // Advance scheduler to let async operations complete
        testScheduler.advanceUntilIdle()

        // Verify async operations eventually complete
        coVerify(exactly = 1) { repository.deleteAllItems(any()) }
    }

    @Test
    fun `item click with no selection navigates to exercise`() {
        val exercise = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Test Exercise",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        handler.invoke(ExercisesStore.Action.Click.Item(exercise.uuid))

        verify(exactly = 1) {
            store.consume(
                ExercisesStore.Action.Navigation.OpenExercise(exercise.uuid),
            )
        }
        verify(exactly = 1) { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
    }

    @Test
    fun `item click with existing selection triggers long click`() {
        val exercise1 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 1",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        val exercise2 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 2",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        stateFlow.value =
            stateFlow.value.copy(selectedItems = setOf(exercise1.uuid).toImmutableSet())

        handler.invoke(ExercisesStore.Action.Click.Item(exercise2.uuid))

        verify(exactly = 1) { store.consume(ExercisesStore.Action.Click.LonkClick(exercise2.uuid)) }
    }

    @Test
    fun `long click adds item to selection when not selected`() {
        val exercise = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Test Exercise",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        handler.invoke(ExercisesStore.Action.Click.LonkClick(exercise.uuid))

        verify(exactly = 1) { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }

        val stateSlot = slot<(ExercisesStore.State) -> ExercisesStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertTrue(newState.selectedItems.contains(exercise.uuid))
        assertEquals(1, newState.selectedItems.size)
    }

    @Test
    fun `long click removes item from selection when already selected`() {
        val exercise1 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 1",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        val exercise2 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 2",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(exercise1.uuid, exercise2.uuid).toImmutableSet(),
        )

        handler.invoke(ExercisesStore.Action.Click.LonkClick(exercise1.uuid))

        verify(exactly = 1) { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }

        val stateSlot = slot<(ExercisesStore.State) -> ExercisesStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(1, newState.selectedItems.size)
        assertTrue(newState.selectedItems.contains(exercise2.uuid))
        assertTrue(!newState.selectedItems.contains(exercise1.uuid))
    }

    @Test
    fun `back handler clears selection when items are selected`() {
        val exercise1 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 1",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        val exercise2 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 2",
            dateProperty = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
        )

        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(exercise1.uuid, exercise2.uuid).toImmutableSet(),
        )

        handler.invoke(ExercisesStore.Action.Click.BackHandler)

        val stateSlot = slot<(ExercisesStore.State) -> ExercisesStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertTrue(newState.selectedItems.isEmpty())
        assertEquals(persistentSetOf<String>(), newState.selectedItems)
    }

    @Test
    fun `back handler does nothing when no items are selected`() {
        stateFlow.value = stateFlow.value.copy(selectedItems = persistentSetOf())

        handler.invoke(ExercisesStore.Action.Click.BackHandler)

        verify(exactly = 0) { store.updateState(any()) }
    }
}
