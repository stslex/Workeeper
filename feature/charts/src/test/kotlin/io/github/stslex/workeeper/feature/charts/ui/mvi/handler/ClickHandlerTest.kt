package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.SingleChartUiModel
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ClickHandlerTest {

    private val initialState = ChartsStore.State(
        name = "",
        charts = persistentListOf(),
        startDate = PropertyHolder.DateProperty(initialValue = 1000000L),
        endDate = PropertyHolder.DateProperty(initialValue = 2000000L),
        calendarState = CalendarState.Closed
    )

    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<ChartsHandlerStore>(relaxed = true) {
        every { this@mockk.state } returns stateFlow

        // Mock the updateState function to actually update the state
        every { this@mockk.updateState(any()) } answers {
            val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }
    }

    private val handler = ClickHandler(store)

    @Test
    fun `start date calendar click opens start date calendar and sends haptic feedback`() {
        handler.invoke(ChartsStore.Action.Click.Calendar.StartDate)

        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(CalendarState.Opened.StartDate, stateFlow.value.calendarState)

        // Verify other state properties are preserved
        assertEquals(initialState.name, stateFlow.value.name)
        assertEquals(initialState.charts, stateFlow.value.charts)
        assertEquals(initialState.startDate, stateFlow.value.startDate)
        assertEquals(initialState.endDate, stateFlow.value.endDate)
    }

    @Test
    fun `end date calendar click opens end date calendar and sends haptic feedback`() {
        handler.invoke(ChartsStore.Action.Click.Calendar.EndDate)

        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(CalendarState.Opened.EndDate, stateFlow.value.calendarState)

        // Verify other state properties are preserved
        assertEquals(initialState.name, stateFlow.value.name)
        assertEquals(initialState.charts, stateFlow.value.charts)
        assertEquals(initialState.startDate, stateFlow.value.startDate)
        assertEquals(initialState.endDate, stateFlow.value.endDate)
    }

    @Test
    fun `close calendar click closes calendar and sends haptic feedback`() {
        // First open a calendar
        stateFlow.value = stateFlow.value.copy(calendarState = CalendarState.Opened.StartDate)

        handler.invoke(ChartsStore.Action.Click.Calendar.Close)

        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(CalendarState.Closed, stateFlow.value.calendarState)
    }

    @Test
    fun `multiple calendar actions work correctly`() {
        // Open start date calendar
        handler.invoke(ChartsStore.Action.Click.Calendar.StartDate)
        assertEquals(CalendarState.Opened.StartDate, stateFlow.value.calendarState)

        // Switch to end date calendar
        handler.invoke(ChartsStore.Action.Click.Calendar.EndDate)
        assertEquals(CalendarState.Opened.EndDate, stateFlow.value.calendarState)

        // Close calendar
        handler.invoke(ChartsStore.Action.Click.Calendar.Close)
        assertEquals(CalendarState.Closed, stateFlow.value.calendarState)

        verify(exactly = 3) { store.sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
        verify(exactly = 3) { store.updateState(any()) }
    }

    @Test
    fun `calendar actions preserve state when transitioning between states`() {
        val testState = initialState.copy(
            name = "Test Exercise",
            startDate = PropertyHolder.DateProperty(initialValue = 5000000L),
            endDate = PropertyHolder.DateProperty(initialValue = 6000000L)
        )
        stateFlow.value = testState

        // Open start date calendar
        handler.invoke(ChartsStore.Action.Click.Calendar.StartDate)

        assertEquals(CalendarState.Opened.StartDate, stateFlow.value.calendarState)
        assertEquals("Test Exercise", stateFlow.value.name)
        assertEquals(5000000L, stateFlow.value.startDate.value)
        assertEquals(6000000L, stateFlow.value.endDate.value)
        assertEquals(testState.charts, stateFlow.value.charts)

        // Switch to end date calendar
        handler.invoke(ChartsStore.Action.Click.Calendar.EndDate)

        assertEquals(CalendarState.Opened.EndDate, stateFlow.value.calendarState)
        assertEquals("Test Exercise", stateFlow.value.name)
        assertEquals(5000000L, stateFlow.value.startDate.value)
        assertEquals(6000000L, stateFlow.value.endDate.value)
        assertEquals(testState.charts, stateFlow.value.charts)
    }

    @Test
    fun `close calendar from different opened states works correctly`() {
        // Test closing from StartDate state
        stateFlow.value = stateFlow.value.copy(calendarState = CalendarState.Opened.StartDate)
        handler.invoke(ChartsStore.Action.Click.Calendar.Close)
        assertEquals(CalendarState.Closed, stateFlow.value.calendarState)

        // Test closing from EndDate state
        stateFlow.value = stateFlow.value.copy(calendarState = CalendarState.Opened.EndDate)
        handler.invoke(ChartsStore.Action.Click.Calendar.Close)
        assertEquals(CalendarState.Closed, stateFlow.value.calendarState)

        verify(exactly = 2) { store.sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
        verify(exactly = 2) { store.updateState(any()) }
    }

    @Test
    fun `haptic feedback is always virtual key type for calendar actions`() {
        val actions = listOf(
            ChartsStore.Action.Click.Calendar.StartDate,
            ChartsStore.Action.Click.Calendar.EndDate,
            ChartsStore.Action.Click.Calendar.Close
        )

        actions.forEach { action ->
            handler.invoke(action)
        }

        // Verify all haptic feedback events use VirtualKey type
        verify(exactly = 3) {
            store.sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey))
        }
    }

    @Test
    fun `state transformation preserves exact state values`() {
        val originalCharts = persistentListOf<SingleChartUiModel>()
        val testState = ChartsStore.State(
            name = "Preserved Exercise",
            charts = originalCharts,
            startDate = PropertyHolder.DateProperty(initialValue = 1234567L),
            endDate = PropertyHolder.DateProperty(initialValue = 7654321L),
            calendarState = CalendarState.Closed
        )
        stateFlow.value = testState

        handler.invoke(ChartsStore.Action.Click.Calendar.StartDate)

        val newState = stateFlow.value
        assertEquals(CalendarState.Opened.StartDate, newState.calendarState)
        assertEquals("Preserved Exercise", newState.name)
        assertEquals(originalCharts, newState.charts)
        assertEquals(1234567L, newState.startDate.value)
        assertEquals(7654321L, newState.endDate.value)
    }
}