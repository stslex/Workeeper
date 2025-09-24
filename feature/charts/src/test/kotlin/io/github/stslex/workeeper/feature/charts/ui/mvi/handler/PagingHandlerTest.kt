package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
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
import kotlin.test.assertEquals

internal class PagingHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val repository: ExerciseRepository = mockk(relaxed = true)

    private val commonStore: CommonDataStore = mockk(relaxed = true)
    private val mapper: ExerciseChartUiMapper = mockk(relaxed = true)

    private val initialState = ChartsStore.State(
        name = "Test Exercise",
        charts = persistentListOf(),
        startDate = PropertyHolder.DateProperty(initialValue = 0L),
        endDate = PropertyHolder.DateProperty(initialValue = 0L),
        type = ChartsType.TRAINING,
        calendarState = CalendarState.Closed
    )
    private val stateFlow: MutableStateFlow<ChartsStore.State> = MutableStateFlow(initialState)

    private val testScope = TestScope(testDispatcher)

    private val store: ChartsHandlerStore = mockk(relaxed = true) {
        every { state } returns stateFlow
        every { scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)
        every { this@mockk.updateState(any()) } answers {
            val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        coEvery { this@mockk.updateStateImmediate(any<(ChartsStore.State) -> ChartsStore.State>()) } answers {
            val transform = arg<(ChartsStore.State) -> ChartsStore.State>(0)
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }
    }

    private val handler: PagingHandler = PagingHandler(
        repository = repository,
        commonStore = commonStore,
        chartMapper = mapper,
        store = store
    )

    @Suppress("UnusedFlow")
    @Test
    fun `init action subscribes to common store date flows and updates state`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(1500000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2500000L)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Verify flows are accessed
        verify(exactly = 1) { commonStore.homeSelectedStartDate }
        verify(exactly = 1) { commonStore.homeSelectedEndDate }

        // Verify state updates for dates
        coVerify(exactly = 2) {
            store.updateStateImmediate(any<(ChartsStore.State) -> ChartsStore.State>())
        }

        // Verify repository call and state changes
        coVerify { repository.getExercises("Test Exercise", 1500000L, 2500000L) }
        assertEquals(
            PropertyHolder.DateProperty(initialValue = 1500000L),
            stateFlow.value.startDate
        )
        assertEquals(PropertyHolder.DateProperty(initialValue = 2500000L), stateFlow.value.endDate)
    }

    @Test
    fun `init action handles null values from common store correctly`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null, 1500000L, null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null, 2500000L, null)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Should only update state once for each non-null value (filterNotNull)
        coVerify(exactly = 2) {
            store.updateStateImmediate(any<(ChartsStore.State) -> ChartsStore.State>())
        }

        assertEquals(
            PropertyHolder.DateProperty(initialValue = 1500000L),
            stateFlow.value.startDate
        )
        assertEquals(PropertyHolder.DateProperty(initialValue = 2500000L), stateFlow.value.endDate)
    }

    @Test
    @Suppress("UnusedFlow")
    fun `charts subscription processes repository data through mapper`() = runTest {
        val exerciseData = listOf(
            mockk<io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel> {
                every { uuid } returns "ex1"
                every { name } returns "Push ups"
            }
        )
        val mappedCharts = listOf(
            mockk<io.github.stslex.workeeper.feature.charts.ui.mvi.model.SingleChartUiModel> {
                every { name } returns "Push ups Chart"
            }
        )

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(exerciseData)
        every { mapper.invoke(exerciseData) } returns mappedCharts

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Verify repository call and mapping
        coVerify { repository.getExercises("Test Exercise", 0L, 0L) }
        verify { mapper.invoke(exerciseData) }

        // Verify state update with mapped charts
        verify { store.updateState(any<(ChartsStore.State) -> ChartsStore.State>()) }
        assertEquals(mappedCharts.size, stateFlow.value.charts.size)
    }

    @Test
    @Suppress("UnusedFlow")
    fun `state changes trigger new repository calls with distinctUntilChanged`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Change state to trigger new repository call
        stateFlow.value = initialState.copy(name = "New Exercise")
        testScheduler.advanceUntilIdle()

        // Verify both repository calls
        coVerify { repository.getExercises("Test Exercise", 0L, 0L) }
        coVerify { repository.getExercises("New Exercise", 0L, 0L) }
    }

    @Test
    @Suppress("UnusedFlow")
    fun `distinctUntilChanged prevents duplicate repository calls for identical state`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Emit same state multiple times
        repeat(3) {
            stateFlow.value = initialState.copy(name = "Test Exercise")
            testScheduler.advanceUntilIdle()
        }

        // Should only call repository once due to distinctUntilChanged
        coVerify(exactly = 1) { repository.getExercises("Test Exercise", 0L, 0L) }
    }

    @Test
    @Suppress("UnusedFlow")
    fun `repository errors are handled gracefully in charts subscription`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        coEvery {
            repository.getExercises(
                any(),
                any(),
                any()
            )
        } throws RuntimeException("Repository error")

        // Should not crash the handler
        var exceptionThrown = false
        try {
            handler.invoke(ChartsStore.Action.Paging.Init)
            testScheduler.advanceUntilIdle()
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertEquals(false, exceptionThrown)
        coVerify { repository.getExercises("Test Exercise", 0L, 0L) }
    }

    @Test
    fun `date subscription updates work independently`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(1000000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(null) // No end date
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Only start date should be updated
        assertEquals(
            PropertyHolder.DateProperty(initialValue = 1000000L),
            stateFlow.value.startDate
        )
        assertEquals(
            PropertyHolder.DateProperty(initialValue = 0L),
            stateFlow.value.endDate
        ) // Should remain initial value

        coVerify(exactly = 1) {
            store.updateStateImmediate(any<(ChartsStore.State) -> ChartsStore.State>())
        }
    }

    @Test
    @Suppress("UnusedFlow")
    fun `charts subscription uses current state values for Triple mapping`() = runTest {
        val customState = ChartsStore.State(
            name = "Custom Exercise",
            charts = persistentListOf(),
            startDate = PropertyHolder.DateProperty(initialValue = 5000000L),
            endDate = PropertyHolder.DateProperty(initialValue = 6000000L),
            type = ChartsType.TRAINING,
            calendarState = CalendarState.Closed
        )

        stateFlow.value = customState

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Should use values from the current state
        coVerify { repository.getExercises("Custom Exercise", 5000000L, 6000000L) }
    }

    @Test
    @Suppress("UnusedFlow")
    fun `multiple state properties changes trigger repository calls`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Change multiple properties
        stateFlow.value = initialState.copy(
            name = "New Exercise",
            startDate = PropertyHolder.DateProperty(initialValue = 1000000L)
        )
        testScheduler.advanceUntilIdle()

        stateFlow.value = stateFlow.value.copy(
            endDate = PropertyHolder.DateProperty(initialValue = 2000000L)
        )
        testScheduler.advanceUntilIdle()

        // Verify all different combinations were called
        coVerify { repository.getExercises("Test Exercise", 0L, 0L) }
        coVerify { repository.getExercises("New Exercise", 1000000L, 0L) }
        coVerify { repository.getExercises("New Exercise", 1000000L, 2000000L) }
    }
}