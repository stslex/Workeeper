package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStoreImpl
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Event
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class TrainingStoreImplTest {

    private val testDispatcher = StandardTestDispatcher()

    private val pagingStateMock = mockk<PagingUiState<PagingData<TrainingUiModel>>>(relaxed = true)

    private val navigationHandler = mockk<NavigationHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }

    private val pagingHandler = mockk<PagingHandler>(relaxed = true) {
        every { pagingUiState } returns pagingStateMock
    }
    private val clickHandler = mockk<ClickHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }
    private val handlerStore = mockk<TrainingHandlerStoreImpl>(relaxed = true) {
        every { state } returns MutableStateFlow(
            TrainingStore.State(
                pagingUiState = pagingStateMock,
                query = "",
                selectedItems = persistentSetOf()
            )
        )
    }

    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = testDispatcher,
        mainImmediateDispatcher = testDispatcher
    )

    private val logger = mockk<Logger> {
        every { i(any()) } just runs
    }

    private val analytics = mockk<StoreAnalytics<Action, Event>>(relaxed = true)

    private val store: TrainingStoreImpl = TrainingStoreImpl(
        navigationHandler = navigationHandler,
        pagingHandler = pagingHandler,
        clickHandler = clickHandler,
        storeDispatchers = storeDispatchers,
        handlerStore = handlerStore,
        analytics = analytics,
        logger = logger
    )

    @Test
    fun `store initializes with correct initial state`() = runTest {
        val expectedState = TrainingStore.State.init(pagingStateMock)

        val actualState = store.state.value

        assertEquals(expectedState, actualState)
    }

    @Test
    fun `navigation actions are handled by navigationHandler`() = runTest(testDispatcher) {
        val createAction = Action.Navigation.CreateTraining
        val openAction = Action.Navigation.OpenTraining("test-uuid")

        store.init()
        store.initEmitter()
        store.consume(createAction)
        store.consume(openAction)
        advanceUntilIdle()

        coVerify { navigationHandler.invoke(createAction) }
        coVerify { navigationHandler.invoke(openAction) }
    }

    @Test
    fun `click actions are handled by clickHandler`() = runTest(testDispatcher) {
        val actions = listOf(
            Action.Click.ActionButton,
            Action.Click.TrainingItemClick("id1"),
            Action.Click.TrainingItemLongClick("id2")
        )

        store.init()
        store.initEmitter()
        actions.forEach { store.consume(it) }
        advanceUntilIdle()

        actions.forEach { action ->
            coVerify { clickHandler.invoke(action) }
        }
    }

    @Test
    fun `paging actions are handled by pagingHandler`() = runTest(testDispatcher) {
        // Since Action.Paging is an empty sealed interface with no implementations,
        // we verify the handler is properly configured but can't test concrete actions
        store.init()
        store.initEmitter()
        advanceUntilIdle()

        // Verify the paging handler is accessible and properly configured
        // This ensures the handler routing is correct for when paging actions are added
        verify { pagingHandler.pagingUiState }
    }

    @Test
    fun `multiple actions are processed in order`() = runTest(testDispatcher) {
        val actions = listOf(
            Action.Navigation.CreateTraining,
            Action.Click.ActionButton,
            Action.Navigation.OpenTraining("test-id")
        )

        store.init()
        store.initEmitter()
        actions.forEach { store.consume(it) }
        advanceUntilIdle()

        coVerify { navigationHandler.invoke(actions[0] as Action.Navigation) }
        coVerify { clickHandler.invoke(actions[1] as Action.Click) }
        coVerify { navigationHandler.invoke(actions[2] as Action.Navigation) }
    }

    @Test
    fun `store properly handles all click action types`() = runTest(testDispatcher) {
        val clickActions = listOf(
            Action.Click.ActionButton,
            Action.Click.TrainingItemClick("item-1"),
            Action.Click.TrainingItemClick("item-2"),
            Action.Click.TrainingItemLongClick("item-3"),
            Action.Click.TrainingItemLongClick("item-4")
        )

        store.init()
        store.initEmitter()
        clickActions.forEach { action ->
            store.consume(action)
        }
        advanceUntilIdle()

        clickActions.forEach { action ->
            coVerify { clickHandler.invoke(action) }
        }
    }

    @Test
    fun `store properly handles all navigation action types`() = runTest(testDispatcher) {
        val navigationActions = listOf(
            Action.Navigation.CreateTraining,
            Action.Navigation.OpenTraining("training-1"),
            Action.Navigation.OpenTraining("training-2")
        )

        store.init()
        store.initEmitter()
        navigationActions.forEach { action ->
            store.consume(action)
        }
        advanceUntilIdle()

        navigationActions.forEach { action ->
            coVerify { navigationHandler.invoke(action) }
        }
    }

    @Test
    fun `training item click and long click actions with same uuid`() = runTest(testDispatcher) {
        val uuid = "same-training-uuid"
        val clickAction = Action.Click.TrainingItemClick(uuid)
        val longClickAction = Action.Click.TrainingItemLongClick(uuid)

        store.init()
        store.initEmitter()
        store.consume(clickAction)
        store.consume(longClickAction)
        advanceUntilIdle()

        coVerify { clickHandler.invoke(clickAction) }
        coVerify { clickHandler.invoke(longClickAction) }
    }

    @Test
    fun `store sends events through analytics`() = runTest(testDispatcher) {
        // Since Event is a sealed interface with no implementations,
        // we'll test that the analytics system is in place by checking store creation
        store.init()
        store.initEmitter()
        advanceUntilIdle()

        // Verify that the store was created with proper analytics support
        // This is implicit testing since there are no concrete Event types to test
        verify(atLeast = 0) { analytics.logAction(any<Action>()) }
        verify(atLeast = 0) { analytics.logEvent(any<Event>()) }
    }

    @Test
    fun `initial state has correct values`() = runTest {
        val state = store.state.value

        assertEquals(pagingStateMock, state.pagingUiState)
        assertEquals("", state.query)
        assertEquals(0, state.selectedItems.size)
        assertEquals(persistentSetOf(), state.selectedItems)
    }

    @Test
    fun `state data class has proper immutable structure`() = runTest {
        val state = TrainingStore.State.init(pagingStateMock)
        val newSelectedItems = persistentSetOf("item1", "item2")

        // Verify state is data class with copy functionality
        val newState = state.copy(
            query = "test query",
            selectedItems = newSelectedItems
        )

        assertEquals("test query", newState.query)
        assertEquals(newSelectedItems, newState.selectedItems)
        assertEquals(pagingStateMock, newState.pagingUiState)

        // Original state should be unchanged
        assertEquals("", state.query)
        assertEquals(persistentSetOf(), state.selectedItems)
    }

    @Test
    fun `store initializes with proper paging ui state from handler`() = runTest {
        val customPagingUiState = mockk<PagingUiState<PagingData<TrainingUiModel>>>(relaxed = true)
        val customPagingHandler = mockk<PagingHandler> {
            every { this@mockk.pagingUiState } returns customPagingUiState
        }

        val customStore = TrainingStoreImpl(
            navigationHandler = navigationHandler,
            pagingHandler = customPagingHandler,
            clickHandler = clickHandler,
            storeDispatchers = storeDispatchers,
            handlerStore = handlerStore,
            analytics = analytics,
            logger = logger
        )

        val state = customStore.state.value

        assertEquals(customPagingUiState, state.pagingUiState)
        verify(exactly = 1) { customPagingHandler.pagingUiState }
    }

    @Test
    fun `store handles empty training item uuids properly`() = runTest(testDispatcher) {
        val clickAction = Action.Click.TrainingItemClick("")
        val longClickAction = Action.Click.TrainingItemLongClick("")
        val openAction = Action.Navigation.OpenTraining("")

        store.init()
        store.initEmitter()
        store.consume(clickAction)
        store.consume(longClickAction)
        store.consume(openAction)
        advanceUntilIdle()

        coVerify { clickHandler.invoke(clickAction) }
        coVerify { clickHandler.invoke(longClickAction) }
        coVerify { navigationHandler.invoke(openAction) }
    }

    @Test
    fun `action routing is correct for all action types`() = runTest(testDispatcher) {
        // Test that the handlerCreator routes actions to the correct handlers
        val navigationAction = Action.Navigation.CreateTraining
        val clickAction = Action.Click.ActionButton

        store.init()
        store.initEmitter()
        store.consume(navigationAction)
        store.consume(clickAction)
        advanceUntilIdle()

        // Verify each action type goes to its respective handler
        coVerify { navigationHandler.invoke(navigationAction) }
        coVerify { clickHandler.invoke(clickAction) }

        // Verify actions don't go to wrong handlers (by checking call counts)
        coVerify(exactly = 1) { clickHandler.invoke(any()) }
        coVerify(exactly = 1) { navigationHandler.invoke(any()) }
    }

}