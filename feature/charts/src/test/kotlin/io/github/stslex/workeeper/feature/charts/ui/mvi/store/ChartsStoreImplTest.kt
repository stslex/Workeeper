package io.github.stslex.workeeper.feature.charts.ui.mvi.store

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStoreImpl
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Event
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChartsStoreImplTest {

    private val testDispatcher = StandardTestDispatcher()

    private val navigationHandler = mockk<NavigationHandler>(relaxed = true)
    private val pagingHandler = mockk<PagingHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }
    private val clickHandler = mockk<ClickHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }
    private val inputHandler = mockk<InputHandler>() {
        coEvery { this@mockk.invoke(any()) } just runs
    }

    private val storeEmitter = mockk<ChartsHandlerStoreImpl>(relaxed = true) {
        every { state } returns MutableStateFlow(ChartsStore.State.INITIAL)
    }

    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = testDispatcher,
        mainImmediateDispatcher = testDispatcher
    )

    private val logger = mockk<Logger> { every { i(any()) } just runs }

    private val analytics = mockk<StoreAnalytics<Action, Event>> {
        every { logEvent(any()) } just runs
        every { logAction(any()) } just runs
    }

    private val store: ChartsStoreImpl = ChartsStoreImpl(
        navigationHandler = navigationHandler,
        pagingHandler = pagingHandler,
        clickHandler = clickHandler,
        inputHandler = inputHandler,
        storeDispatchers = storeDispatchers,
        storeEmitter = storeEmitter,
        analytics = analytics,
        logger = logger
    )

    @Test
    fun `store initializes with correct initial state`() = runTest {
        val expectedState = ChartsStore.State.INITIAL

        val actualState = store.state.value

        assertEquals(expectedState, actualState)
    }

    @Test
    fun `store initializes with Paging Init action`() = runTest {
        val initAction = Action.Paging.Init

        store.init()
        store.initEmitter()
        advanceUntilIdle()

        coVerify(exactly = 1) { pagingHandler.invoke(initAction) }
        verify(exactly = 1) { logger.i("consume: $initAction") }
        verify(exactly = 1) { analytics.logAction(initAction) }
    }

    @Test
    fun `paging actions are handled by pagingHandler`() = runTest(testDispatcher) {
        val action = Action.Paging.Init

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { pagingHandler.invoke(action) }
    }


    @Test
    fun `click actions are handled by clickHandler`() = runTest(testDispatcher) {
        val action = Action.Click.Calendar.StartDate

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { clickHandler.invoke(action) }
    }

    @Test
    fun `input actions are handled by inputHandler`() = runTest(testDispatcher) {
        val action = Action.Input.ChangeStartDate(System.currentTimeMillis())

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { inputHandler.invoke(action) }
    }

    @Test
    fun `multiple actions are processed in order`() = runTest(testDispatcher) {
        val timestamp = System.currentTimeMillis()
        val actions = listOf(
            Action.Input.ChangeStartDate(timestamp),
            Action.Click.Calendar.StartDate,
            Action.Paging.Init
        )

        store.init()
        store.initEmitter()
        actions.forEach { action ->
            store.consume(action)
        }
        advanceUntilIdle()

        coVerify { inputHandler.invoke(actions[0] as Action.Input) }
        coVerify { clickHandler.invoke(actions[1] as Action.Click) }
        coVerify { pagingHandler.invoke(actions[2] as Action.Paging) }
    }

    @Test
    fun `store properly handles all calendar click actions`() = runTest(testDispatcher) {
        val calendarActions = listOf(
            Action.Click.Calendar.StartDate,
            Action.Click.Calendar.EndDate,
            Action.Click.Calendar.Close
        )

        store.init()
        store.initEmitter()
        calendarActions.forEach { action ->
            store.consume(action)
        }
        advanceUntilIdle()

        calendarActions.forEach { action ->
            coVerify { clickHandler.invoke(action) }
        }
    }

    @Test
    fun `store properly handles date input actions`() = runTest(testDispatcher) {
        val currentTime = System.currentTimeMillis()
        val startDateAction = Action.Input.ChangeStartDate(currentTime - 86400000) // 1 day ago
        val endDateAction = Action.Input.ChangeEndDate(currentTime)

        store.init()
        store.initEmitter()
        store.consume(startDateAction)
        store.consume(endDateAction)
        advanceUntilIdle()

        coVerify { inputHandler.invoke(startDateAction) }
        coVerify { inputHandler.invoke(endDateAction) }
    }

    @Test
    fun `store sends events through storeEmitter`() = runTest(testDispatcher) {
        val event = Event.HapticFeedback(HapticFeedbackType.LongPress)

        store.init()
        store.initEmitter()
        store.sendEvent(event)
        advanceUntilIdle()

        verify { analytics.logEvent(event) }
    }

    @Test
    fun `store sends different haptic feedback types correctly`() = runTest(testDispatcher) {
        val events = listOf(
            Event.HapticFeedback(HapticFeedbackType.LongPress),
            Event.HapticFeedback(HapticFeedbackType.TextHandleMove)
        )

        store.init()
        store.initEmitter()
        events.forEach { event ->
            store.sendEvent(event)
        }
        advanceUntilIdle()

        events.forEach { event ->
            verify { analytics.logEvent(event) }
        }
    }

    @Test
    @Disabled("time delay unexpected cause validation")
    fun `initial state has correct default values`() = runTest {
        val expectedEndDate = System.currentTimeMillis()
        val delta = 1000L // 1 second tolerance
        val expectedStartDate = expectedEndDate - (7L * 24 * 60 * 60 * 2000 + delta) // 7 days ago

        val state = store.state.value

        assertEquals("", state.name)
        assertEquals(0, state.charts.size)
        assertEquals(CalendarState.Closed, state.calendarState)

        // Check dates within tolerance due to timing
        val actualStartTime = state.startDate.value
        val actualEndTime = state.endDate.value

        assert(kotlin.math.abs(actualStartTime - expectedStartDate) < delta) {
            "Start date should be approximately 7 days ago"
        }
        assert(kotlin.math.abs(actualEndTime - expectedEndDate) < delta) {
            "End date should be approximately current time"
        }
    }

    @Test
    fun `state data classes have proper immutable structure`() = runTest {
        val state = ChartsStore.State.INITIAL
        val startDate = PropertyHolder.DateProperty(initialValue = System.currentTimeMillis())
        val endDate = PropertyHolder.DateProperty(initialValue = System.currentTimeMillis() + 86400000)

        // Verify state is data class with copy functionality
        val newState = state.copy(
            name = "Test Charts",
            startDate = startDate,
            endDate = endDate,
            calendarState = CalendarState.Opened.StartDate
        )

        assertEquals("Test Charts", newState.name)
        assertEquals(startDate, newState.startDate)
        assertEquals(endDate, newState.endDate)
        assertEquals(CalendarState.Opened.StartDate, newState.calendarState)

        // Original state should be unchanged
        assertEquals("", state.name)
        assertEquals(CalendarState.Closed, state.calendarState)
    }

}
