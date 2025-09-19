package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.navigation.Screen
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
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ClickHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val repository = mockk<ExerciseRepository>(relaxed = true)
    private val store: ExerciseHandlerStore = mockk<ExerciseHandlerStore>(relaxed = true)
    private val testScope = TestScope(testDispatcher)

    private val pagingUiState = mockk<PagingUiState<PagingData<ExerciseUiModel>>>(relaxed = true)

    private val initialState = ExercisesStore.State(
        items = pagingUiState,
        selectedItems = persistentSetOf(),
        query = ""
    )

    private val stateFlow = MutableStateFlow(initialState)
    private val handler = ClickHandler(repository, store)

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
            dateProperty = DateProperty.new(System.currentTimeMillis())
        )

        val exercise2 = ExerciseUiModel(
            uuid = exerciseUuid2.toString(),
            name = "Exercise 2",
            dateProperty = DateProperty.new(System.currentTimeMillis())
        )

        val selectedItems = setOf(exercise1, exercise2).toImmutableSet()
        stateFlow.value = stateFlow.value.copy(selectedItems = selectedItems)

        coEvery { repository.deleteAllItems(any()) } returns Unit

        handler.invoke(ExercisesStore.Action.Click.FloatButtonClick)

        // Verify immediate behavior
        verify { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.Confirm)) }

        // Advance scheduler to let async operations complete
        testScheduler.advanceUntilIdle()

        // Verify async operations eventually complete
        coVerify { repository.deleteAllItems(any()) }
    }

    @Test
    fun `item click with no selection navigates to exercise`() {
        val exercise = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Test Exercise",
            dateProperty = DateProperty.new(System.currentTimeMillis()),
        )

        handler.invoke(ExercisesStore.Action.Click.Item(exercise))

        verify {
            store.consume(
                ExercisesStore.Action.Navigation.OpenExercise(
                    Screen.Exercise.Data(
                        exercise.uuid
                    )
                )
            )
        }
        verify { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
    }

    @Test
    fun `item click with existing selection triggers long click`() {
        val exercise1 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 1",
            dateProperty = DateProperty.new(System.currentTimeMillis())
        )

        val exercise2 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 2",
            dateProperty = DateProperty.new(System.currentTimeMillis())
        )

        stateFlow.value = stateFlow.value.copy(selectedItems = setOf(exercise1).toImmutableSet())

        handler.invoke(ExercisesStore.Action.Click.Item(exercise2))

        verify { store.consume(ExercisesStore.Action.Click.LonkClick(exercise2)) }
        verify { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
    }

    @Test
    fun `long click adds item to selection when not selected`() {
        val exercise = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Test Exercise",
            dateProperty = DateProperty.new(System.currentTimeMillis())
        )

        handler.invoke(ExercisesStore.Action.Click.LonkClick(exercise))

        verify { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.LongPress)) }

        val stateSlot = slot<(ExercisesStore.State) -> ExercisesStore.State>()
        verify { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertTrue(newState.selectedItems.contains(exercise))
        assertEquals(1, newState.selectedItems.size)
    }

    @Test
    fun `long click removes item from selection when already selected`() {
        val exercise1 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 1",
            dateProperty = DateProperty.new(System.currentTimeMillis())
        )

        val exercise2 = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "Exercise 2",
            dateProperty = DateProperty.new(System.currentTimeMillis())
        )

        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(exercise1, exercise2).toImmutableSet()
        )

        handler.invoke(ExercisesStore.Action.Click.LonkClick(exercise1))

        verify { store.sendEvent(ExercisesStore.Event.HapticFeedback(HapticFeedbackType.LongPress)) }

        val stateSlot = slot<(ExercisesStore.State) -> ExercisesStore.State>()
        verify { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals(1, newState.selectedItems.size)
        assertTrue(newState.selectedItems.contains(exercise2))
        assertTrue(!newState.selectedItems.contains(exercise1))
    }
}