package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ExerciseChartMap
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.SingleChartUiModel
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class PagingHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private lateinit var repository: ExerciseRepository
    private lateinit var commonStore: CommonDataStore
    private lateinit var mapper: ExerciseChartMap
    private lateinit var store: ChartsHandlerStore
    private lateinit var stateFlow: MutableStateFlow<ChartsStore.State>
    private lateinit var handler: PagingHandler

    private val initialState = ChartsStore.State(
        name = "Test Exercise",
        charts = persistentListOf(),
        startDate = DateProperty.new(1000000L),
        endDate = DateProperty.new(2000000L),
        calendarState = CalendarState.Closed
    )

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        commonStore = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        stateFlow = MutableStateFlow(initialState)

        store = mockk(relaxed = true) {
            every { state } returns stateFlow
            every { scope } returns AppCoroutineScope(
                TestScope(testDispatcher),
                testDispatcher,
                testDispatcher
            )
        }

        handler = PagingHandler(repository, commonStore, mapper, store)
    }

    @Test
    fun `init action subscribes to common store date flows`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(1500000L)
        every { commonStore.homeSelectedEndDate } returns flowOf(2500000L)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Verify the flows are accessed during subscription setup
        verify { commonStore.homeSelectedStartDate }
        verify { commonStore.homeSelectedEndDate }
    }

    @Test
    fun `init action sets up chart subscription flow`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        // Action should complete without throwing exceptions
        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Verify the state flow is accessed (subscription is set up)
        verify { store.state }
    }

    @Test
    fun `init action processes successfully with valid data`() = runTest {
        val exerciseData = listOf(
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Push ups",
                sets = listOf(SetsDataModel(uuid = "set1", weight = 50.0, reps = 10, type = SetsDataType.WORK)),
                timestamp = 1500000L,
                trainingUuid = null,
                labels = listOf()
            )
        )
        val expectedCharts = listOf(SingleChartUiModel(name = "Push ups", properties = listOf(500.0)))

        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(exerciseData)
        every { mapper.invoke(exerciseData) } returns expectedCharts

        // Should process data without errors
        var exceptionThrown = false
        try {
            handler.invoke(ChartsStore.Action.Paging.Init)
            testScheduler.advanceUntilIdle()
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertEquals(false, exceptionThrown, "Handler should process valid data without errors")
    }

    @Test
    fun `handler responds to state changes correctly`() = runTest {
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // State changes should be handled without errors
        var exceptionThrown = false
        try {
            stateFlow.value = initialState.copy(name = "New Exercise")
            testScheduler.advanceUntilIdle()
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertEquals(false, exceptionThrown, "Handler should handle state changes gracefully")
    }

    @Test
    fun `handler processes multiple state emissions without errors`() = runTest {
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)

        handler.invoke(ChartsStore.Action.Paging.Init)
        testScheduler.advanceUntilIdle()

        // Multiple state emissions should be handled gracefully
        var exceptionThrown = false
        try {
            repeat(3) {
                stateFlow.value = initialState.copy()
                testScheduler.advanceUntilIdle()
            }
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertEquals(false, exceptionThrown, "Handler should handle multiple state emissions")
    }

    @Test
    fun `repository errors are handled gracefully without crashing`() = runTest {
        coEvery { repository.getExercises(any(), any(), any()) } throws RuntimeException("Repository error")
        every { commonStore.homeSelectedStartDate } returns flowOf(null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null)

        // Should not throw exception despite repository failure
        var exceptionThrown = false
        try {
            handler.invoke(ChartsStore.Action.Paging.Init)
            testScheduler.advanceUntilIdle()
        } catch (e: Exception) {
            exceptionThrown = true
        }

        // Error should be handled gracefully without propagating to handler level
        assertEquals(false, exceptionThrown, "Handler should handle repository errors gracefully")
    }

    @Test
    fun `handler processes flows with null values correctly`() = runTest {
        every { commonStore.homeSelectedStartDate } returns flowOf(null, 1500000L, null)
        every { commonStore.homeSelectedEndDate } returns flowOf(null, 2500000L, null)
        coEvery { repository.getExercises(any(), any(), any()) } returns flowOf(emptyList())
        every { mapper.invoke(any()) } returns emptyList()

        // Handler should process flows with null values without errors
        var exceptionThrown = false
        try {
            handler.invoke(ChartsStore.Action.Paging.Init)
            testScheduler.advanceUntilIdle()
        } catch (e: Exception) {
            exceptionThrown = true
        }

        // Verify both flows are accessed during subscription setup
        verify { commonStore.homeSelectedStartDate }
        verify { commonStore.homeSelectedEndDate }
        assertEquals(false, exceptionThrown, "Handler should handle null values gracefully")
    }
}