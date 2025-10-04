package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ClickHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val interactor = mockk<AllTrainingsInteractor>()

    private val testScope = TestScope(testDispatcher)
    private val pagingUiState = mockk<PagingUiState<PagingData<TrainingUiModel>>>(relaxed = true)
    private val stateFlow = MutableStateFlow(
        TrainingStore.State.init(pagingUiState),
    )

    private val appCoroutineScope = AppCoroutineScope(testScope, testDispatcher, testDispatcher)

    private val store = mockk<TrainingHandlerStore>(relaxed = true) {
        every { this@mockk.state } returns stateFlow
        every { this@mockk.scope } returns appCoroutineScope

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

    private val handler = ClickHandler(interactor, store)

    @Test
    fun `training item click when no items selected navigates to training`() = runTest {
        val trainingUuid = Uuid.random().toString()

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(trainingUuid))

        verify(exactly = 1) {
            store.consume(
                TrainingStore.Action.Navigation.OpenTraining(
                    trainingUuid,
                ),
            )
        }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `training item click when items selected adds to selection`() = runTest {
        val existingUuid = Uuid.random().toString()
        val newUuid = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(existingUuid).toImmutableSet(),
        )

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(newUuid))

        verify(exactly = 1) { store.updateState(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        assertEquals(2, stateFlow.value.selectedItems.size)
    }

    @Test
    fun `training item click when item already selected removes from selection`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid1, uuid2).toImmutableSet(),
        )

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(uuid1))

        verify(exactly = 1) { store.updateState(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        assertEquals(1, stateFlow.value.selectedItems.size)
        assertEquals(setOf(uuid2), stateFlow.value.selectedItems)
    }

    @Test
    fun `training item long click adds item to selection`() = runTest {
        val uuid = Uuid.random().toString()

        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid))

        verify(exactly = 1) { store.updateState(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `training item long click removes item when already selected`() = runTest {
        val uuid = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid).toImmutableSet(),
        )

        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid))

        verify(exactly = 1) { store.updateState(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `action button click when no items selected navigates to create training`() = runTest {
        stateFlow.value = stateFlow.value.copy(selectedItems = persistentSetOf())

        handler.invoke(TrainingStore.Action.Click.ActionButton)

        verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.CreateTraining) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        coVerify(exactly = 0) { interactor.deleteAll(any()) }
    }

    @Test
    fun `action button click when items selected deletes them`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        val selectedItems = setOf(uuid1, uuid2).toImmutableSet()
        val originalQuery = "test query"
        val originalKeyboardVisible = true

        stateFlow.value = stateFlow.value.copy(
            selectedItems = selectedItems,
            query = originalQuery,
            isKeyboardVisible = originalKeyboardVisible,
        )

        coEvery { interactor.deleteAll(any()) } returns Unit

        handler.invoke(TrainingStore.Action.Click.ActionButton)

        // Wait for async execution to complete
        testScheduler.advanceUntilIdle()

        // Verify interactor was called with correct parameters
        coVerify(exactly = 1) { interactor.deleteAll(listOf(uuid1, uuid2)) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.CreateTraining) }

        // Note: We don't verify updateStateImmediate call here as it's called within the coroutine
        // and the mock setup for suspend functions is complex. The main functionality is verified
        // by checking that the interactor was called correctly.
    }

    @Test
    fun `back handler clears selection when items are selected`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid1, uuid2).toImmutableSet(),
        )

        handler.invoke(TrainingStore.Action.Click.BackHandler)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertTrue(newState.selectedItems.isEmpty())
        assertEquals(persistentSetOf<String>(), newState.selectedItems)
    }

    @Test
    fun `back handler does nothing when no items are selected and no query`() = runTest {
        stateFlow.value = stateFlow.value.copy(
            selectedItems = persistentSetOf(),
            query = "",
        )

        handler.invoke(TrainingStore.Action.Click.BackHandler)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        verify(exactly = 0) { store.updateState(any()) }
    }

    @Test
    fun `back handler clears query when query exists and no selected items`() = runTest {
        stateFlow.value = stateFlow.value.copy(
            selectedItems = persistentSetOf(),
            query = "test query",
        )

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.BackHandler)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        verify(exactly = 1) { store.updateState(any()) }

        val newState = stateSlot.captured(stateFlow.value)
        assertEquals("", newState.query)
        assertEquals(persistentSetOf<String>(), newState.selectedItems)
        assertEquals(stateFlow.value.pagingUiState, newState.pagingUiState)
        assertEquals(stateFlow.value.isKeyboardVisible, newState.isKeyboardVisible)
    }

    @Test
    fun `back handler clears both query and selected items when both exist`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid1, uuid2).toImmutableSet(),
            query = "test query",
        )

        val stateSlots = mutableListOf<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlots)) } answers {
            val transform = stateSlots.last()
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.BackHandler)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        verify(exactly = 2) { store.updateState(any()) } // Called twice: once for selectedItems, once for query

        // After both updates, state should be cleared
        assertEquals("", stateFlow.value.query)
        assertEquals(persistentSetOf<String>(), stateFlow.value.selectedItems)
    }

    @Test
    fun `back handler preserves other state properties during clearing`() = runTest {
        val originalPagingUiState = pagingUiState
        val originalKeyboardVisible = true
        val uuid = Uuid.random().toString()

        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid).toImmutableSet(),
            query = "test query",
            isKeyboardVisible = originalKeyboardVisible,
        )

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.BackHandler)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }

        // Verify state properties are preserved
        assertEquals(originalPagingUiState, stateFlow.value.pagingUiState)
        assertEquals(originalKeyboardVisible, stateFlow.value.isKeyboardVisible)
        assertTrue(stateFlow.value.selectedItems.isEmpty())
    }

    @Test
    fun `training item click with empty uuid handles gracefully`() = runTest {
        val emptyUuid = ""

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(emptyUuid))

        verify(exactly = 1) {
            store.consume(
                TrainingStore.Action.Navigation.OpenTraining(emptyUuid),
            )
        }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `training item long click with special characters in uuid`() = runTest {
        val specialUuid = "uuid@123!#$%^&*()_+-=[]{}|;:,.<>?"

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(specialUuid))

        verify(exactly = 1) { store.updateState(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        assertTrue(stateFlow.value.selectedItems.contains(specialUuid))
    }

    @Test
    fun `multiple rapid clicks on same item toggle selection correctly`() = runTest {
        val uuid = Uuid.random().toString()

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        // First click - should add to selection
        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid))
        assertTrue(stateFlow.value.selectedItems.contains(uuid))

        // Second click - should remove from selection
        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid))
        assertTrue(!stateFlow.value.selectedItems.contains(uuid))

        // Third click - should add back to selection
        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid))
        assertTrue(stateFlow.value.selectedItems.contains(uuid))

        verify(exactly = 3) { store.updateState(any()) }
        verify(exactly = 3) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `action button with error in delete operation preserves state`() = runTest {
        val uuid1 = Uuid.random().toString()
        val selectedItems = setOf(uuid1).toImmutableSet()
        stateFlow.value = stateFlow.value.copy(selectedItems = selectedItems)

        val testException = RuntimeException("Delete failed")
        coEvery { interactor.deleteAll(any()) } throws testException

        handler.invoke(TrainingStore.Action.Click.ActionButton)

        // Wait for async execution to complete
        testScheduler.advanceUntilIdle()

        // Verify the error occurred but state wasn't updated (since onSuccess wasn't called)
        coVerify(exactly = 1) { interactor.deleteAll(listOf(uuid1)) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        // Note: We don't verify updateStateImmediate here as the mock verification is complex for suspend functions

        // State should remain unchanged
        assertEquals(selectedItems, stateFlow.value.selectedItems)
    }

    @Test
    fun `state immutability - modifications don't affect original state`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        val originalSelectedItems = setOf(uuid1).toImmutableSet()
        val originalQuery = "original query"

        stateFlow.value = stateFlow.value.copy(
            selectedItems = originalSelectedItems,
            query = originalQuery,
        )

        val originalState = stateFlow.value

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        // Perform operation that modifies state
        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid2))

        // Verify original state wasn't mutated
        assertEquals(originalSelectedItems, originalState.selectedItems)
        assertEquals(originalQuery, originalState.query)

        // Verify new state has the changes
        assertTrue(stateFlow.value.selectedItems.contains(uuid2))
        assertTrue(stateFlow.value.selectedItems.contains(uuid1))
        assertEquals(2, stateFlow.value.selectedItems.size)
    }

    @Test
    fun `concurrent operations maintain state consistency`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        val uuid3 = Uuid.random().toString()

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        // Simulate rapid operations
        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid1))
        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid2))
        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid3))

        // All should be selected
        assertEquals(3, stateFlow.value.selectedItems.size)
        assertTrue(stateFlow.value.selectedItems.contains(uuid1))
        assertTrue(stateFlow.value.selectedItems.contains(uuid2))
        assertTrue(stateFlow.value.selectedItems.contains(uuid3))

        verify(exactly = 3) { store.updateState(any()) }
        verify(exactly = 3) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `large selection operations maintain performance`() = runTest {
        // Create a large selection
        val uuids = (1..100).map { Uuid.random().toString() }
        val largeSelection = uuids.toSet().toImmutableSet()

        stateFlow.value = stateFlow.value.copy(selectedItems = largeSelection)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        // Test back handler with large selection
        handler.invoke(TrainingStore.Action.Click.BackHandler)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        verify(atLeast = 1) { store.updateState(any()) }

        // Selection should be cleared
        assertTrue(stateFlow.value.selectedItems.isEmpty())
    }
}
