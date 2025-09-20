package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStoreImpl
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Event
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
internal class AllExercisesStoreImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val pagingUiState = mockk<PagingUiState<PagingData<ExerciseUiModel>>>(relaxed = true)

    private val component = mockk<NavigationHandler> {
        every { this@mockk.invoke(any()) } just runs
    }

    private val pagingHandler = mockk<PagingHandler> {
        every { this@mockk.invoke(any()) } just runs
        every { processor } returns pagingUiState
    }

    private val clickHandler = mockk<ClickHandler> {
        every { this@mockk.invoke(any()) } just runs
    }

    private val inputHandler = mockk<InputHandler> {
        every { this@mockk.invoke(any()) } just runs
    }

    private val storeEmitter = mockk<ExerciseHandlerStoreImpl>(relaxed = true) {
        every { state } returns MutableStateFlow(
            ExercisesStore.State(
                items = pagingUiState,
                selectedItems = persistentSetOf(),
                query = ""
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

    private val analytics = mockk<StoreAnalytics<Action, Event>> {
        every { logEvent(any()) } just runs
        every { logAction(any()) } just runs
    }

    private fun createStore(): AllExercisesStoreImpl = AllExercisesStoreImpl(
        component = component,
        pagingHandler = pagingHandler,
        clickHandler = clickHandler,
        inputHandler = inputHandler,
        storeDispatchers = storeDispatchers,
        storeEmitter = storeEmitter,
        logger = logger,
        analytics = analytics
    )

    @Test
    fun `store initializes with correct initial state`() = runTest(testDispatcher) {
        val store = createStore()

        val expected = ExercisesStore.State.init(allItems = pagingUiState)
        val actual = store.state.value

        assertEquals(expected, actual)
    }

    @Test
    fun `navigation actions are handled by component`() = runTest(testDispatcher) {
        val store = createStore()

        store.init()
        store.initEmitter()
        store.consume(Action.Navigation.CreateExerciseDialog)
        advanceUntilIdle()

        verify { component.invoke(Action.Navigation.CreateExerciseDialog) }
    }

    @Test
    fun `click actions are handled by clickHandler`() = runTest(testDispatcher) {
        val store = createStore()

        val action = Action.Click.FloatButtonClick

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        verify { clickHandler.invoke(action) }
    }

    @Test
    fun `input actions are handled by inputHandler`() = runTest(testDispatcher) {
        val store = createStore()

        val action = Action.Input.SearchQuery("query")

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        verify { inputHandler.invoke(action) }
    }

    @Test
    fun `multiple actions are processed in order`() = runTest(testDispatcher) {
        val store = createStore()

        val actions = listOf(
            Action.Input.SearchQuery("q"),
            Action.Click.FloatButtonClick,
            Action.Navigation.CreateExerciseDialog,
        )

        store.init()
        store.initEmitter()
        actions.forEach { store.consume(it) }
        advanceUntilIdle()

        verify { inputHandler.invoke(actions[0] as Action.Input) }
        verify { clickHandler.invoke(actions[1] as Action.Click) }
        verify { component.invoke(actions[2] as Action.Navigation) }
    }

    @Test
    fun `store sends events through analytics`() = runTest(testDispatcher) {
        val store = createStore()

        val event = Event.HapticFeedback(HapticFeedbackType.LongPress)

        store.init()
        store.initEmitter()
        store.sendEvent(event)
        advanceUntilIdle()

        verify { analytics.logEvent(event) }
    }
}

