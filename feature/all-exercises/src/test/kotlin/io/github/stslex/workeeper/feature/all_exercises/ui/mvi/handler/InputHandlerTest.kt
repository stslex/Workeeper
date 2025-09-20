package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class InputHandlerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val store = mockk<ExerciseHandlerStore>(relaxed = true)
    private val pagingUiState = mockk<PagingUiState<PagingData<ExerciseUiModel>>>(relaxed = true)

    private val initialState = ExercisesStore.State.init(pagingUiState)
    private val stateFlow = MutableStateFlow(initialState)
    private val handler = InputHandler(store)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { store.state } returns stateFlow

        // Mock the updateState function to actually update the state
        every { store.updateState(any()) } answers {
            val transform = arg<(ExercisesStore.State) -> ExercisesStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search query input action updates query state`() {
        val searchQuery = "push ups"

        handler.invoke(ExercisesStore.Action.Input.SearchQuery(searchQuery))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(searchQuery, stateFlow.value.query)
        assertEquals(initialState.items, stateFlow.value.items)
        assertEquals(initialState.selectedItems, stateFlow.value.selectedItems)
    }

    @Test
    fun `search query input action with empty string updates query state`() {
        val emptyQuery = ""

        handler.invoke(ExercisesStore.Action.Input.SearchQuery(emptyQuery))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(emptyQuery, stateFlow.value.query)
    }

    @Test
    fun `search query input action with whitespace updates query state`() {
        val whitespaceQuery = "   "

        handler.invoke(ExercisesStore.Action.Input.SearchQuery(whitespaceQuery))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(whitespaceQuery, stateFlow.value.query)
    }

    @Test
    fun `search query input action with special characters updates query state`() {
        val specialQuery = "exercise@123!#"

        handler.invoke(ExercisesStore.Action.Input.SearchQuery(specialQuery))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(specialQuery, stateFlow.value.query)
    }

    @Test
    fun `multiple search query inputs update state correctly`() {
        val firstQuery = "first query"
        val secondQuery = "second query"

        handler.invoke(ExercisesStore.Action.Input.SearchQuery(firstQuery))
        assertEquals(firstQuery, stateFlow.value.query)

        handler.invoke(ExercisesStore.Action.Input.SearchQuery(secondQuery))
        assertEquals(secondQuery, stateFlow.value.query)

        verify(exactly = 2) { store.updateState(any()) }
    }

    @Test
    fun `search query input preserves other state properties`() {
        // Set some initial selected items
        val selectedExercise = mockk<ExerciseUiModel>()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = persistentSetOf(selectedExercise),
            query = "initial query"
        )

        val newQuery = "new search query"

        handler.invoke(ExercisesStore.Action.Input.SearchQuery(newQuery))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(newQuery, stateFlow.value.query)
        assertEquals(1, stateFlow.value.selectedItems.size)
        assertEquals(selectedExercise, stateFlow.value.selectedItems.first())
        assertEquals(initialState.items, stateFlow.value.items)
    }

    @Test
    fun `search query input with very long string updates query state`() {
        val longQuery = "a".repeat(1000)

        handler.invoke(ExercisesStore.Action.Input.SearchQuery(longQuery))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(longQuery, stateFlow.value.query)
        assertEquals(1000, stateFlow.value.query.length)
    }

    @Test
    fun `search query input with unicode characters updates query state`() {
        val unicodeQuery = "测试 упражнение 🏋️‍♂️"

        handler.invoke(ExercisesStore.Action.Input.SearchQuery(unicodeQuery))

        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(unicodeQuery, stateFlow.value.query)
    }
}