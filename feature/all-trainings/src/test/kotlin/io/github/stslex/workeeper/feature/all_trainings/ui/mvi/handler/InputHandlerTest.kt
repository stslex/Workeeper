package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class InputHandlerTest {

    private val pagingUiState = mockk<PagingUiState<PagingData<TrainingUiModel>>>(relaxed = true)

    private val initialState = TrainingStore.State(
        pagingUiState = pagingUiState,
        query = "",
        selectedItems = persistentSetOf(),
        isKeyboardVisible = false,
    )

    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<TrainingHandlerStore>(relaxed = true) {
        every { state } returns stateFlow
    }

    private val handler = InputHandler(store)

    @Test
    fun `search query action updates state query`() {
        val newQuery = "test query"
        val action = TrainingStore.Action.Input.SearchQuery(newQuery)

        handler.invoke(action)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(initialState)
        assertEquals(newQuery, newState.query)
        assertEquals(initialState.pagingUiState, newState.pagingUiState)
        assertEquals(initialState.selectedItems, newState.selectedItems)
        assertEquals(initialState.isKeyboardVisible, newState.isKeyboardVisible)
    }

    @Test
    fun `keyboard change action with visible true updates state`() {
        val action = TrainingStore.Action.Input.KeyboardChange(isVisible = true)

        handler.invoke(action)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(initialState)
        assertEquals(true, newState.isKeyboardVisible)
        assertEquals(initialState.query, newState.query)
        assertEquals(initialState.pagingUiState, newState.pagingUiState)
        assertEquals(initialState.selectedItems, newState.selectedItems)
    }

    @Test
    fun `keyboard change action with visible false updates state`() {
        val stateWithKeyboardVisible = initialState.copy(isKeyboardVisible = true)
        val action = TrainingStore.Action.Input.KeyboardChange(isVisible = false)

        handler.invoke(action)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateWithKeyboardVisible)
        assertEquals(false, newState.isKeyboardVisible)
        assertEquals(stateWithKeyboardVisible.query, newState.query)
        assertEquals(stateWithKeyboardVisible.pagingUiState, newState.pagingUiState)
        assertEquals(stateWithKeyboardVisible.selectedItems, newState.selectedItems)
    }

    @Test
    fun `search query with empty string updates state`() {
        val action = TrainingStore.Action.Input.SearchQuery("")

        handler.invoke(action)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(initialState)
        assertEquals("", newState.query)
    }

    @Test
    fun `search query with whitespace updates state correctly`() {
        val queryWithSpaces = "  test query  "
        val action = TrainingStore.Action.Input.SearchQuery(queryWithSpaces)

        handler.invoke(action)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(initialState)
        assertEquals(queryWithSpaces, newState.query)
    }

    @Test
    fun `multiple input actions are processed correctly`() {
        val searchAction = TrainingStore.Action.Input.SearchQuery("search")
        val keyboardAction = TrainingStore.Action.Input.KeyboardChange(true)

        handler.invoke(searchAction)
        handler.invoke(keyboardAction)

        verify(exactly = 2) { store.updateState(any()) }
    }

    @Test
    fun `search query with special characters updates state`() {
        val specialQuery = "test@#$%^&*()query"
        val action = TrainingStore.Action.Input.SearchQuery(specialQuery)

        handler.invoke(action)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(initialState)
        assertEquals(specialQuery, newState.query)
    }

    @Test
    fun `keyboard visibility changes preserve other state properties`() {
        val stateWithQuery = initialState.copy(
            query = "existing query",
            selectedItems = persistentSetOf("item1", "item2"),
        )
        val action = TrainingStore.Action.Input.KeyboardChange(true)

        handler.invoke(action)

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateWithQuery)
        assertEquals(true, newState.isKeyboardVisible)
        assertEquals("existing query", newState.query)
        assertEquals(persistentSetOf("item1", "item2"), newState.selectedItems)
        assertEquals(stateWithQuery.pagingUiState, newState.pagingUiState)
    }
}
