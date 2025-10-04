package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsState
import io.github.stslex.workeeper.feature.charts.mvi.model.SingleChartUiModel
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class InputHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val commonStore = mockk<CommonDataStore>(relaxed = true)
    private val testScope = TestScope(testDispatcher)
    private val initialState = ChartsStore.State.INITIAL
    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<ChartsHandlerStore>(relaxed = true) {
        every { this@mockk.scope } returns AppCoroutineScope(
            testScope,
            testDispatcher,
            testDispatcher,
        )
        every { state } returns stateFlow

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
            val action = arg<suspend CoroutineScope.() -> Any>(4)
            testScope.launch { runCatching { action() } }
        }
    }

    private val handler = InputHandler(commonStore, store)

    @Test
    fun `process query action updates state name`() = runTest {
        val queryName = "Test Query"

        handler.invoke(ChartsStore.Action.Input.Query(queryName))

        testScheduler.advanceUntilIdle()

        val stateSlot = slot<(ChartsStore.State) -> ChartsStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(initialState)
        assertEquals(queryName, newState.name)
    }

    @Test
    fun `process query action updates state with empty name`() = runTest {
        val initialState = ChartsStore.State.INITIAL.copy(name = "Initial")
        val queryName = ""

        handler.invoke(ChartsStore.Action.Input.Query(queryName))

        testScheduler.advanceUntilIdle()

        val stateSlot = slot<(ChartsStore.State) -> ChartsStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(initialState)
        assertEquals(queryName, newState.name)
    }

    @Test
    fun `change start date action updates common store start date`() = runTest {
        val startTimestamp = 1500000L

        coEvery { commonStore.setHomeSelectedStartDate(startTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp) }
    }

    @Test
    fun `change end date action updates common store end date`() = runTest {
        val endTimestamp = 2500000L

        coEvery { commonStore.setHomeSelectedEndDate(endTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp) }
    }

    @Test
    fun `change start date action with zero timestamp`() = runTest {
        val zeroTimestamp = 0L

        coEvery { commonStore.setHomeSelectedStartDate(zeroTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(zeroTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(zeroTimestamp) }
    }

    @Test
    fun `change end date action with negative timestamp`() = runTest {
        val negativeTimestamp = -1000L

        coEvery { commonStore.setHomeSelectedEndDate(negativeTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(negativeTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(negativeTimestamp) }
    }

    @Test
    fun `change start date action with large timestamp`() = runTest {
        val largeTimestamp = Long.MAX_VALUE

        coEvery { commonStore.setHomeSelectedStartDate(largeTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(largeTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(largeTimestamp) }
    }

    @Test
    fun `multiple date change actions work correctly`() = runTest {
        val startTimestamp1 = 1000000L
        val startTimestamp2 = 2000000L
        val endTimestamp1 = 3000000L
        val endTimestamp2 = 4000000L

        coEvery { commonStore.setHomeSelectedStartDate(any()) } returns Unit
        coEvery { commonStore.setHomeSelectedEndDate(any()) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp1))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp1))
        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp2))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp2))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp1) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp2) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp1) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp2) }
    }

    @Test
    fun `change start date action handles store error gracefully`() = runTest {
        val startTimestamp = 1500000L

        coEvery { commonStore.setHomeSelectedStartDate(startTimestamp) } throws RuntimeException("Store error")

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp))

        testScheduler.advanceUntilIdle()

        // Verify the method was called despite the error
        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp) }
    }

    @Test
    fun `change end date action handles store error gracefully`() = runTest {
        val endTimestamp = 2500000L

        coEvery { commonStore.setHomeSelectedEndDate(endTimestamp) } throws RuntimeException("Store error")

        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp))

        testScheduler.advanceUntilIdle()

        // Verify the method was called despite the error
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp) }
    }

    @Test
    fun `launch function is called for both input actions`() = runTest {
        val startTimestamp = 1000000L
        val endTimestamp = 2000000L

        coEvery { commonStore.setHomeSelectedStartDate(any()) } returns Unit
        coEvery { commonStore.setHomeSelectedEndDate(any()) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp))

        testScheduler.advanceUntilIdle()

        // Verify launch was called twice (once for each action)
        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp) }
    }

    @Test
    fun `same timestamp can be set multiple times`() = runTest {
        val sameTimestamp = 1500000L

        coEvery { commonStore.setHomeSelectedStartDate(sameTimestamp) } returns Unit
        coEvery { commonStore.setHomeSelectedEndDate(sameTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(sameTimestamp))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(sameTimestamp))
        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(sameTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 2) { commonStore.setHomeSelectedStartDate(sameTimestamp) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(sameTimestamp) }
    }

    @Test
    fun `current timestamp values work correctly`() = runTest {
        val currentTime = System.currentTimeMillis()

        coEvery { commonStore.setHomeSelectedStartDate(currentTime) } returns Unit
        coEvery { commonStore.setHomeSelectedEndDate(currentTime) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(currentTime))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(currentTime))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(currentTime) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(currentTime) }
    }

    @Test
    fun `scroll to chart updates selected index and sends haptic feedback`() = runTest {
        val chartContent = ChartsState.Content(
            charts = persistentListOf(
                mockk<SingleChartUiModel>(relaxed = true),
                mockk<SingleChartUiModel>(relaxed = true),
                mockk<SingleChartUiModel>(relaxed = true),
            ),
            chartsTitles = persistentListOf("Chart 1", "Chart 2", "Chart 3"),
            selectedChartIndex = 0,
        )
        stateFlow.value = initialState.copy(chartState = chartContent)

        every { store.updateState(any()) } answers {
            val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(ChartsStore.Action.Input.CurrentChartPageChange(1))

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.ScrollChartHeader(1)) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(1, (stateFlow.value.chartState as ChartsState.Content).selectedChartIndex)
    }

    @Test
    fun `scroll to chart with same index does not trigger any updates or events`() = runTest {
        val chartContent = ChartsState.Content(
            charts = persistentListOf(
                mockk<SingleChartUiModel>(relaxed = true),
                mockk<SingleChartUiModel>(relaxed = true),
            ),
            chartsTitles = persistentListOf("Chart 1", "Chart 2"),
            selectedChartIndex = 1,
        )
        stateFlow.value = initialState.copy(chartState = chartContent)

        every { store.updateState(any()) } answers {
            val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(ChartsStore.Action.Input.CurrentChartPageChange(1))

        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { store.sendEvent(any<ChartsStore.Event.HapticFeedback>()) }
        verify(exactly = 0) { store.sendEvent(any<ChartsStore.Event.ScrollChartHeader>()) }
        verify(exactly = 0) { store.updateState(any()) }
        assertEquals(1, (stateFlow.value.chartState as ChartsState.Content).selectedChartIndex)
    }

    @Test
    fun `scroll to chart with loading state does not change chart state but sends events`() =
        runTest {
            val initialChartState = ChartsState.Loading
            stateFlow.value = initialState.copy(chartState = initialChartState)

            every { store.updateState(any()) } answers {
                val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
                val newState = transform(stateFlow.value)
                stateFlow.value = newState
            }

            handler.invoke(ChartsStore.Action.Input.CurrentChartPageChange(2))

            testScheduler.advanceUntilIdle()

            verify(exactly = 1) {
                store.sendEvent(
                    ChartsStore.Event.HapticFeedback(
                        HapticFeedbackType.VirtualKey,
                    ),
                )
            }
            verify(exactly = 1) { store.sendEvent(ChartsStore.Event.ScrollChartHeader(2)) }
            assertEquals(initialChartState, stateFlow.value.chartState)
        }

    @Test
    fun `scroll to chart with empty state does not change chart state but sends events`() =
        runTest {
            val initialChartState = ChartsState.Empty
            stateFlow.value = initialState.copy(chartState = initialChartState)

            every { store.updateState(any()) } answers {
                val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
                val newState = transform(stateFlow.value)
                stateFlow.value = newState
            }

            handler.invoke(ChartsStore.Action.Input.CurrentChartPageChange(0))

            testScheduler.advanceUntilIdle()

            verify(exactly = 1) {
                store.sendEvent(
                    ChartsStore.Event.HapticFeedback(
                        HapticFeedbackType.VirtualKey,
                    ),
                )
            }
            verify(exactly = 1) { store.sendEvent(ChartsStore.Event.ScrollChartHeader(0)) }
            assertEquals(initialChartState, stateFlow.value.chartState)
        }

    @Test
    fun `scroll to chart with zero index works correctly`() = runTest {
        val chartContent = ChartsState.Content(
            charts = persistentListOf(
                mockk<SingleChartUiModel>(relaxed = true),
                mockk<SingleChartUiModel>(relaxed = true),
            ),
            chartsTitles = persistentListOf("Chart 1", "Chart 2"),
            selectedChartIndex = 1,
        )
        stateFlow.value = initialState.copy(chartState = chartContent)

        every { store.updateState(any()) } answers {
            val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(ChartsStore.Action.Input.CurrentChartPageChange(0))

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.ScrollChartHeader(0)) }
        verify(exactly = 1) { store.updateState(any()) }
        assertEquals(0, (stateFlow.value.chartState as ChartsState.Content).selectedChartIndex)
    }

    @Test
    fun `multiple scroll to chart actions work correctly`() = runTest {
        val chartContent = ChartsState.Content(
            charts = persistentListOf(
                mockk<SingleChartUiModel>(relaxed = true),
                mockk<SingleChartUiModel>(relaxed = true),
                mockk<SingleChartUiModel>(relaxed = true),
                mockk<SingleChartUiModel>(relaxed = true),
            ),
            chartsTitles = persistentListOf("Chart 1", "Chart 2", "Chart 3", "Chart 4"),
            selectedChartIndex = 0,
        )
        stateFlow.value = initialState.copy(chartState = chartContent)

        every { store.updateState(any()) } answers {
            val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(ChartsStore.Action.Input.CurrentChartPageChange(1))
        testScheduler.advanceUntilIdle()
        assertEquals(1, (stateFlow.value.chartState as ChartsState.Content).selectedChartIndex)

        handler.invoke(ChartsStore.Action.Input.CurrentChartPageChange(2))
        testScheduler.advanceUntilIdle()
        assertEquals(2, (stateFlow.value.chartState as ChartsState.Content).selectedChartIndex)

        handler.invoke(ChartsStore.Action.Input.CurrentChartPageChange(3))
        testScheduler.advanceUntilIdle()
        assertEquals(3, (stateFlow.value.chartState as ChartsState.Content).selectedChartIndex)

        verify(exactly = 3) { store.sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey)) }
        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.ScrollChartHeader(1)) }
        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.ScrollChartHeader(2)) }
        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.ScrollChartHeader(3)) }
        verify(exactly = 3) { store.updateState(any()) }
    }

    @Test
    fun `scroll to chart preserves other state properties`() = runTest {
        val chartContent = ChartsState.Content(
            charts = persistentListOf(
                mockk<SingleChartUiModel>(relaxed = true),
                mockk<SingleChartUiModel>(relaxed = true),
            ),
            chartsTitles = persistentListOf("Chart 1", "Chart 2"),
            selectedChartIndex = 0,
        )
        stateFlow.value = initialState.copy(chartState = chartContent)

        val originalCharts = (stateFlow.value.chartState as ChartsState.Content).charts
        val originalTitles = (stateFlow.value.chartState as ChartsState.Content).chartsTitles

        every { store.updateState(any()) } answers {
            val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(ChartsStore.Action.Input.CurrentChartPageChange(1))

        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { store.sendEvent(ChartsStore.Event.ScrollChartHeader(1)) }
        val updatedContent = stateFlow.value.chartState as ChartsState.Content
        assertEquals(1, updatedContent.selectedChartIndex)
        assertEquals(originalCharts, updatedContent.charts)
        assertEquals(originalTitles, updatedContent.chartsTitles)
    }
}
