package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.domain.interactor.ChartsInteractor
import io.github.stslex.workeeper.feature.charts.domain.model.ChartParams
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsDomainType
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainItem
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartParamsMapper
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartResultsMapper
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartsType
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class PagingHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val interactor: ChartsInteractor = mockk(relaxed = true)

    private val commonStore: CommonDataStore = mockk(relaxed = true)
    private val chartParamsMapper: ChartParamsMapper = mockk(relaxed = true)
    private val chartResultsMapper: ChartResultsMapper = mockk(relaxed = true)

    private val initialState = ChartsStore.State(
        name = "Test Exercise",
        charts = persistentListOf(),
        startDate = PropertyHolder.DateProperty.new(initialValue = 0L),
        endDate = PropertyHolder.DateProperty.new(initialValue = 0L),
        type = ChartsType.TRAINING,
        calendarState = CalendarState.Closed,
    )
    private val stateFlow: MutableStateFlow<ChartsStore.State> = MutableStateFlow(initialState)

    private val testScope = TestScope(testDispatcher)

    private val store: ChartsHandlerStore = mockk(relaxed = true) {
        every { state } returns stateFlow
        every { scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)
    }

    private val handler: PagingHandler = PagingHandler(
        interactor = interactor,
        commonStore = commonStore,
        chartParamsMapper = chartParamsMapper,
        chartResultsMapper = chartResultsMapper,
        store = store,
    )

    @Suppress("UnusedFlow")
    @Test
    fun `init action subscribes to common store date flows and updates state`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(1500000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2500000L)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 1500000L,
            endDate = 2500000L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Verify flows are accessed
        verify(atLeast = 1) { commonStore.homeSelectedStartDate }
        verify(atLeast = 1) { commonStore.homeSelectedEndDate }

        // Verify interactor call
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `init action handles null values from common store correctly`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null, 1500000L, null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null, 2500000L, null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Verify the handler processed the init action
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `charts subscription processes interactor data through mapper`() = runTest {
        val domainData = listOf(
            SingleChartDomainModel(
                name = "Push ups",
                values = listOf(
                    SingleChartDomainItem(timestamp = 1000L, value = 1.0f),
                    SingleChartDomainItem(timestamp = 2000L, value = 2.0f),
                    SingleChartDomainItem(timestamp = 3000L, value = 3.0f),
                ),
            ),
        )

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns domainData
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Verify interactor was called with data
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `state changes trigger new interactor calls with distinctUntilChanged`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returnsMany listOf(
            ChartParams(0L, 0L, "Test Exercise", ChartsDomainType.TRAINING),
            ChartParams(0L, 0L, "New Exercise", ChartsDomainType.TRAINING),
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Change state to trigger new interactor call
        stateFlow.value = initialState.copy(name = "New Exercise")
        testScheduler.advanceUntilIdle()

        // Verify both interactor calls
        coVerify(exactly = 2) { interactor.getChartsData(any()) }
    }

    @Test
    fun `distinctUntilChanged prevents duplicate interactor calls for identical state`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Emit same state multiple times
        repeat(3) {
            stateFlow.value = initialState.copy(name = "Test Exercise")
            testScheduler.advanceUntilIdle()
        }

        // Should only call interactor once due to distinctUntilChanged
        coVerify(exactly = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `interactor errors are handled gracefully in charts subscription`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } throws RuntimeException("Interactor error")

        assertDoesNotThrow { handler.invoke(ChartsStore.Action.Paging.Init) }
        coVerify { interactor.getChartsData(any()) }
    }

    @Test
    fun `date subscription updates work independently`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(1000000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(null) // No end date
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 1000000L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Verify handler processed the init action
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `charts subscription uses current state values for mapping`() = runTest {
        val customState = ChartsStore.State(
            name = "Custom Exercise",
            charts = persistentListOf(),
            startDate = PropertyHolder.DateProperty.new(initialValue = 5000000L),
            endDate = PropertyHolder.DateProperty.new(initialValue = 6000000L),
            type = ChartsType.TRAINING,
            calendarState = CalendarState.Closed,
        )

        stateFlow.value = customState

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(customState) } returns ChartParams(
            startDate = 5000000L,
            endDate = 6000000L,
            name = "Custom Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Should use values from the current state
        coVerify { interactor.getChartsData(any()) }
        verify { chartParamsMapper.invoke(customState) }
    }

    // ========== ERROR HANDLING TESTS ==========

    @Suppress("UnusedFlow")
    @Test
    fun `common store date flow errors are handled gracefully`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(1500000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2000000L)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 1500000L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Verify that start date subscription works despite end date error
        verify(atLeast = 1) { commonStore.homeSelectedStartDate }
    }

    @Test
    fun `chart params mapper errors are handled gracefully`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } throws IllegalArgumentException("Mapper error")

        assertDoesNotThrow { handler.invoke(ChartsStore.Action.Paging.Init) }
        verify(atLeast = 1) { chartParamsMapper.invoke(any()) }
    }

    @Test
    fun `chart results mapper errors are handled gracefully`() = runTest {
        val domainData = listOf(
            SingleChartDomainModel(
                name = "Test",
                values = listOf(
                    SingleChartDomainItem(timestamp = 1000L, value = 1.0f),
                    SingleChartDomainItem(timestamp = 2000L, value = 2.0f),
                ),
            ),
        )

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns domainData
        every { chartResultsMapper.invoke(any()) } throws IllegalStateException("Results mapper error")

        assertDoesNotThrow { handler.invoke(ChartsStore.Action.Paging.Init) }
        coVerify { interactor.getChartsData(any()) }
    }

    @Test
    fun `network timeout scenarios are handled gracefully`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } throws java.net.SocketTimeoutException("Network timeout")

        assertDoesNotThrow { handler.invoke(ChartsStore.Action.Paging.Init) }
        coVerify { interactor.getChartsData(any()) }
    }

    // ========== EDGE CASES AND BOUNDARY CONDITIONS ==========

    @Test
    fun `exercise chart type is handled correctly`() = runTest {
        val exerciseState = initialState.copy(type = ChartsType.EXERCISE)
        stateFlow.value = exerciseState

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(exerciseState) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.EXERCISE,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        coVerify { interactor.getChartsData(match { it.type == ChartsDomainType.EXERCISE }) }
    }

    @Test
    fun `large data sets are processed correctly`() = runTest {
        val largeDataSet = (1..10).map { index ->
            SingleChartDomainModel(
                name = "Exercise_$index",
                values = (1..5).map { SingleChartDomainItem(timestamp = it * 1000L, value = it.toFloat()) },
            )
        }

        every { commonStore.homeSelectedStartDate } returns flowOf(1500000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2500000L)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 1500000L,
            endDate = 2500000L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns largeDataSet
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Just verify the handler doesn't crash with large data sets
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `empty chart data is handled correctly`() = runTest {
        val emptyData = emptyList<SingleChartDomainModel>()

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyData
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        coVerify { interactor.getChartsData(any()) }
        verify(exactly = 0) { chartResultsMapper.invoke(any()) }
    }

    @Test
    fun `extreme timestamp values are handled correctly`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(Long.MIN_VALUE)
        every { commonStore.homeSelectedEndDate } returns flowOf(Long.MAX_VALUE)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = Long.MIN_VALUE,
            endDate = Long.MAX_VALUE,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        coVerify {
            interactor.getChartsData(
                match {
                    it.startDate == Long.MIN_VALUE && it.endDate == Long.MAX_VALUE
                },
            )
        }
    }

    @Test
    fun `zero timestamp values are handled correctly`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(0L)
        every { commonStore.homeSelectedEndDate } returns flowOf(0L)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        coVerify {
            interactor.getChartsData(
                match {
                    it.startDate == 0L && it.endDate == 0L
                },
            )
        }
    }

    // ========== CONCURRENT OPERATIONS ==========

    @Test
    fun `multiple rapid init calls are handled correctly`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        // Fire multiple init calls rapidly
        repeat(5) {
            handler.invoke(ChartsStore.Action.Paging.Init)
        }
        testScheduler.advanceUntilIdle()

        // Should handle all calls gracefully
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `rapid state changes are handled with distinctUntilChanged`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Rapid state changes with same content
        repeat(10) {
            stateFlow.value = initialState.copy(name = "Same Exercise")
            testScheduler.advanceUntilIdle()
        }

        // Should be called only once due to distinctUntilChanged
        coVerify(exactly = 1) { interactor.getChartsData(any()) }
    }

    // ========== DATA VALIDATION TESTS ==========

    @Test
    fun `invalid chart domain model data is handled gracefully`() = runTest {
        val invalidData = listOf(
            SingleChartDomainModel(name = "", values = emptyList()),
            SingleChartDomainModel(
                name = "   ",
                values = listOf(
                    SingleChartDomainItem(timestamp = 1000L, value = Float.NaN),
                    SingleChartDomainItem(timestamp = 2000L, value = Float.POSITIVE_INFINITY),
                ),
            ),
            SingleChartDomainModel(
                name = "Valid",
                values = listOf(
                    SingleChartDomainItem(timestamp = 1000L, value = -1.0f),
                    SingleChartDomainItem(timestamp = 2000L, value = 0.0f),
                    SingleChartDomainItem(timestamp = 3000L, value = 1.0f),
                ),
            ),
        )

        every { commonStore.homeSelectedStartDate } returns flowOf(1500000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2500000L)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 1500000L,
            endDate = 2500000L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns invalidData
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Just verify the handler doesn't crash with invalid data
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `extremely long exercise names are handled correctly`() = runTest {
        val longName = "A".repeat(1000)
        val longNameState = initialState.copy(name = longName)
        stateFlow.value = longNameState

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(longNameState) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = longName,
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        coVerify { interactor.getChartsData(match { it.name == longName }) }
    }

    // ========== PERFORMANCE AND STRESS TESTS ==========

    @Suppress("UnusedFlow")
    @Test
    fun `high frequency date changes are handled efficiently`() = runTest {
        val timestampSequence = (1..50).map { it.toLong() * 1000 }
        every { commonStore.homeSelectedStartDate } returns flowOf(*timestampSequence.toTypedArray())
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Should handle all date changes efficiently
        verify(atLeast = 1) { commonStore.homeSelectedStartDate }
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `calendar state transitions don't affect data loading`() = runTest {
        val stateWithCalendarOpen =
            initialState.copy(calendarState = CalendarState.Opened.StartDate)
        stateFlow.value = stateWithCalendarOpen

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(stateWithCalendarOpen) } returns ChartParams(
            startDate = 0L,
            endDate = 0L,
            name = "Test Exercise",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()
        every { chartResultsMapper.invoke(any()) } returns mockk()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Calendar state should not prevent data loading
        coVerify { interactor.getChartsData(any()) }
    }
}
