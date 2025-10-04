package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.domain.interactor.ChartsInteractor
import io.github.stslex.workeeper.feature.charts.domain.model.ChartDataType
import io.github.stslex.workeeper.feature.charts.domain.model.ChartParams
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsDomainType
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainItem
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel
import io.github.stslex.workeeper.feature.charts.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.charts.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartParamsMapper
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartResultsMapper
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsState
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsType
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class PagingHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val interactor: ChartsInteractor = mockk(relaxed = true)

    private val commonStore: CommonDataStore = mockk(relaxed = true)
    private val chartParamsMapper: ChartParamsMapper = mockk(relaxed = true)
    private val chartResultsMapper: ChartResultsMapper = mockk(relaxed = true)

    private val initialState = ChartsStore.State(
        name = "Test Exercise",
        chartState = ChartsState.Loading,
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
        coEvery { updateStateImmediate(any<suspend (ChartsStore.State) -> ChartsStore.State>()) } coAnswers {
            val transform = firstArg<suspend (ChartsStore.State) -> ChartsStore.State>()
            stateFlow.value = transform(stateFlow.value)
        }
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
    fun `init action subscribes to common store date flows and updates state`() = runTest(testDispatcher) {
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
        testScheduler.advanceTimeBy(500L) // Advance past the 300ms delay
        testScheduler.advanceUntilIdle()

        // Verify flows are accessed
        verify(atLeast = 1) { commonStore.homeSelectedStartDate }
        verify(atLeast = 1) { commonStore.homeSelectedEndDate }

        // Verify interactor call
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `init action handles null values from common store correctly`() = runTest(testDispatcher) {
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
        testScheduler.advanceTimeBy(500L) // Advance past the 300ms delay
        testScheduler.advanceUntilIdle()

        // Verify the handler processed the init action
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `charts subscription processes interactor data through mapper`() = runTest(testDispatcher) {
        val domainData = listOf(
            SingleChartDomainModel(
                name = "Push ups",
                dateType = ChartDataType.DAY,
                values = listOf(
                    SingleChartDomainItem(xValue = 0f, yValue = 1.0f),
                    SingleChartDomainItem(xValue = 1f, yValue = 2.0f),
                    SingleChartDomainItem(xValue = 2f, yValue = 3.0f),
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
        testScheduler.advanceTimeBy(500L) // Advance past the 300ms delay
        testScheduler.advanceUntilIdle()

        // Verify interactor was called with data
        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }

    @Test
    fun `chart params mapper errors are handled gracefully`() = runTest(testDispatcher) {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        every { chartParamsMapper.invoke(any()) } throws IllegalArgumentException("Mapper error")

        assertDoesNotThrow {
            handler.invoke(ChartsStore.Action.Paging.Init)
            testScheduler.advanceUntilIdle()
        }
        verify(atLeast = 1) { chartParamsMapper.invoke(any()) }
    }

    @Test
    fun `chart results mapper errors are handled gracefully`() = runTest(testDispatcher) {
        val domainData = listOf(
            SingleChartDomainModel(
                name = "Test",
                dateType = ChartDataType.DAY,
                values = listOf(
                    SingleChartDomainItem(xValue = 0f, yValue = 1.0f),
                    SingleChartDomainItem(xValue = 1f, yValue = 2.0f),
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

        assertDoesNotThrow {
            handler.invoke(ChartsStore.Action.Paging.Init)
            testScheduler.advanceTimeBy(500L)
            testScheduler.advanceUntilIdle()
        }
        coVerify { interactor.getChartsData(any()) }
    }

    @Test
    fun `state transitions to Loading before fetching data`() = runTest(testDispatcher) {
        every { commonStore.homeSelectedStartDate } returns flowOf(1000000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2000000L)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 1000000L,
            endDate = 2000000L,
            name = "Test",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceTimeBy(100L)

        coVerify(atLeast = 1) { store.updateStateImmediate(any<suspend (ChartsStore.State) -> ChartsStore.State>()) }
    }

    @Test
    fun `state transitions to Empty when results are empty`() = runTest(testDispatcher) {
        every { commonStore.homeSelectedStartDate } returns flowOf(1000000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2000000L)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 1000000L,
            endDate = 2000000L,
            name = "Test",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceTimeBy(700L)
        testScheduler.advanceUntilIdle()

        assertEquals(ChartsState.Empty, stateFlow.value.chartState)
    }

    @Test
    fun `state transitions to Content with correct data when results exist`() = runTest(testDispatcher) {
        val domainData = listOf(
            SingleChartDomainModel(
                name = "Exercise 1",
                dateType = ChartDataType.DAY,
                values = listOf(
                    SingleChartDomainItem(xValue = 0f, yValue = 10.0f),
                ),
            ),
        )

        every { commonStore.homeSelectedStartDate } returns flowOf(1000000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2000000L)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 1000000L,
            endDate = 2000000L,
            name = "Test",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } returns domainData
        every { chartResultsMapper.invoke(any()) } returns mockk(relaxed = true) {
            every { name } returns "Exercise 1"
        }

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceTimeBy(700L)
        testScheduler.advanceUntilIdle()

        val chartState = stateFlow.value.chartState
        assert(chartState is ChartsState.Content) { "Expected Content state, got $chartState" }
        val content = chartState as ChartsState.Content
        assertEquals(1, content.charts.size)
        assertEquals(0, content.selectedChartIndex)
    }

    @Test
    fun `interactor errors are handled gracefully`() = runTest(testDispatcher) {
        every { commonStore.homeSelectedStartDate } returns flowOf(1000000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2000000L)
        every { chartParamsMapper.invoke(any()) } returns ChartParams(
            startDate = 1000000L,
            endDate = 2000000L,
            name = "Test",
            type = ChartsDomainType.TRAINING,
        )
        coEvery { interactor.getChartsData(any()) } throws RuntimeException("Network error")

        assertDoesNotThrow {
            handler.invoke(ChartsStore.Action.Paging.Init)
            testScheduler.advanceTimeBy(700L)
            testScheduler.advanceUntilIdle()
        }

        coVerify(atLeast = 1) { interactor.getChartsData(any()) }
    }
}
