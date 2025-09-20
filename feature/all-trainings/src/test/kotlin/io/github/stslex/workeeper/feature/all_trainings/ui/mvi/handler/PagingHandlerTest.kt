package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiMapper
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PagingHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val repository = mockk<TrainingRepository>(relaxed = true)
    private val trainingMapper = mockk<TrainingUiMapper>(relaxed = true)

    private val initialQuery = "initial_query"

    private val store = mockk<TrainingHandlerStore>(relaxed = true)
    private val handler: PagingHandler = PagingHandler(repository, trainingMapper, testDispatcher, store)

    private val initialState = TrainingStore.State(
        pagingUiState = handler.pagingUiState,
        query = initialQuery,
        selectedItems = persistentSetOf()
    )
    private val stateFlow = MutableStateFlow(initialState)

    @Suppress("UnusedFlow")
    @Test
    fun `pagingUiState transforms data correctly with finite flow`() = runTest(testDispatcher) {
        val testData = getTestData()
        val expectedData = getExpectedData()

        every { repository.getTrainings(any()) } returns flowOf(getNotLoadingData(testData))
        every { store.state } returns stateFlow

        // Setup mapper to return expected UI models
        every { trainingMapper.invoke(any()) } answers {
            val dataModel = it.invocation.args[0] as TrainingDataModel
            TrainingUiModel(
                uuid = dataModel.uuid,
                name = dataModel.name,
                labels = persistentListOf(),
                exerciseUuids = persistentListOf(),
                date = DateProperty.new(dataModel.timestamp)
            )
        }

        val result = handler.pagingUiState.invoke().asSnapshot()

        assertEquals(expectedData, result)
        verify(exactly = 1) { repository.getTrainings(initialQuery) }
    }

    @Suppress("UnusedFlow")
    @Test
    fun `pagingUiState transforms data correctly with empty pagingData`() = runTest(testDispatcher) {
        every { repository.getTrainings(any()) } returns flowOf(getNotLoadingData(emptyList()))
        every { store.state } returns stateFlow

        val result = handler.pagingUiState.invoke().asSnapshot()

        assertEquals(emptyList<TrainingUiModel>(), result)
        verify(exactly = 1) { repository.getTrainings(initialQuery) }
    }

    @Suppress("UnusedFlow")
    @Test
    fun `pagingUiState transforms data correctly on query changes`() = runTest(testDispatcher) {
        val expectedQuery = "new_expected_query"
        val expectedData = getExpectedData()
        every { repository.getTrainings(initialQuery) } returns flowOf(getNotLoadingData(emptyList()))
        every { repository.getTrainings(expectedQuery) } returns flowOf(
            getNotLoadingData(
                getTestData()
            )
        )
        every { store.state } returns stateFlow

        // Setup mapper to return expected UI models
        every { trainingMapper.invoke(any()) } answers {
            val dataModel = it.invocation.args[0] as TrainingDataModel
            TrainingUiModel(
                uuid = dataModel.uuid,
                name = dataModel.name,
                labels = persistentListOf(),
                exerciseUuids = persistentListOf(),
                date = DateProperty.new(dataModel.timestamp)
            )
        }

        val pagingUiState = handler.pagingUiState.invoke()
        val emptySnapshot = pagingUiState.asSnapshot()

        assertEquals(emptyList<TrainingUiModel>(), emptySnapshot)
        verify(exactly = 1) { repository.getTrainings(initialQuery) }

        stateFlow.update { it.copy(query = expectedQuery) }

        val dataSnapshot = pagingUiState.asSnapshot()

        assertEquals(expectedData, dataSnapshot)
        verify(exactly = 1) { repository.getTrainings(expectedQuery) }
    }

    @Test
    fun `pagingUiState uses distinctUntilChanged for query optimization`() = runTest(testDispatcher) {
        every { repository.getTrainings(any()) } returns flowOf(getNotLoadingData(emptyList()))
        every { store.state } returns stateFlow

        val pagingUiState = handler.pagingUiState.invoke()

        // Multiple updates with the same query should not trigger repository calls
        repeat(3) {
            stateFlow.update { it.copy(query = initialQuery) }
        }

        pagingUiState.asSnapshot()

        // Should only call repository once due to distinctUntilChanged
        verify(exactly = 1) { repository.getTrainings(initialQuery) }
    }

    @Test
    fun `trainingMapper is called for each data item`() = runTest(testDispatcher) {
        val testData = getTestData()
        every { repository.getTrainings(any()) } returns flowOf(getNotLoadingData(testData))
        every { store.state } returns stateFlow

        // Setup mapper to return expected UI models
        every { trainingMapper.invoke(any()) } answers {
            val dataModel = it.invocation.args[0] as TrainingDataModel
            TrainingUiModel(
                uuid = dataModel.uuid,
                name = dataModel.name,
                labels = persistentListOf(),
                exerciseUuids = persistentListOf(),
                date = DateProperty.new(dataModel.timestamp)
            )
        }

        handler.pagingUiState.invoke().asSnapshot()

        // Verify mapper was called for each item
        verify(exactly = testData.size) { trainingMapper.invoke(any()) }
    }

    private fun getNotLoadingData(data: List<TrainingDataModel>): PagingData<TrainingDataModel> =
        PagingData.from(
            data,
            LoadStates(
                refresh = LoadState.NotLoading(true),
                prepend = LoadState.NotLoading(true),
                append = LoadState.NotLoading(true),
            )
        )

    private fun getTestData(): List<TrainingDataModel> = listOf(
        TrainingDataModel(
            uuid = "t1",
            name = "Morning Workout",
            labels = persistentListOf(),
            exerciseUuids = persistentListOf(),
            timestamp = 1000L
        ),
        TrainingDataModel(
            uuid = "t2",
            name = "Evening Training",
            labels = persistentListOf(),
            exerciseUuids = persistentListOf(),
            timestamp = 2000L
        )
    )

    private fun getExpectedData(): List<TrainingUiModel> = listOf(
        TrainingUiModel(
            uuid = "t1",
            name = "Morning Workout",
            labels = persistentListOf(),
            exerciseUuids = persistentListOf(),
            date = DateProperty.new(1000L)
        ),
        TrainingUiModel(
            uuid = "t2",
            name = "Evening Training",
            labels = persistentListOf(),
            exerciseUuids = persistentListOf(),
            date = DateProperty.new(2000L)
        )
    )
}
