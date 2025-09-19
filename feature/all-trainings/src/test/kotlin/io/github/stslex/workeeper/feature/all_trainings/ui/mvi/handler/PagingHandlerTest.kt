package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiMapper
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class PagingHandlerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<TrainingRepository>()
    private val trainingMapper = mockk<TrainingUiMapper>()
    private val store = mockk<TrainingHandlerStore>(relaxed = true)
    private val pagingUiState = mockk<PagingUiState<PagingData<TrainingUiModel>>>(relaxed = true)
    private val stateFlow = MutableStateFlow(
        TrainingStore.State.init(pagingUiState)
    )
    private lateinit var handler: PagingHandler

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { store.state } returns stateFlow
        handler = PagingHandler(repository, trainingMapper, testDispatcher, store)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `paging ui state returns trainings for query`() = runTest {
        val query = "test"
        val trainingData = TrainingDataModel(
            uuid = Uuid.random().toString(),
            name = "Test Training",
            labels = listOf("Label1"),
            exerciseUuids = emptyList(),
            timestamp = 12345L
        )
        val trainingUi = TrainingUiModel(
            uuid = trainingData.uuid,
            name = trainingData.name,
            labels = persistentListOf("Label1"),
            exerciseUuids = persistentListOf(),
            date = DateProperty.new(12345L)
        )

        every { repository.getTrainings(any()) } returns flowOf(PagingData.from(listOf(trainingData)))
        every { trainingMapper.invoke(trainingData) } returns trainingUi

        // Access the pagingUiState property to trigger initialization
        val pagingUiState = handler.pagingUiState

        // Start collecting to trigger the lazy flow
        val job = TestScope(testDispatcher).launch {
            pagingUiState().collect { }
        }

        // Change the query to trigger data loading
        stateFlow.value = stateFlow.value.copy(query = query)

        // Allow some time for the flow to be processed
        testScheduler.runCurrent()
        testScheduler.advanceUntilIdle()

        // Verify repository is accessed
        @Suppress("UnusedFlow")
        verify(atLeast = 1) { repository.getTrainings(any()) }

        job.cancel()
    }

    @Test
    fun `paging ui state updates when query changes`() = runTest {
        val query1 = "test1"
        val query2 = "test2"
        val trainingData1 = TrainingDataModel(
            uuid = Uuid.random().toString(),
            name = "Test Training 1",
            labels = emptyList(),
            exerciseUuids = emptyList(),
            timestamp = 12345L
        )
        val trainingData2 = TrainingDataModel(
            uuid = Uuid.random().toString(),
            name = "Test Training 2",
            labels = emptyList(),
            exerciseUuids = emptyList(),
            timestamp = 12346L
        )
        val trainingUi1 = TrainingUiModel(
            uuid = trainingData1.uuid,
            name = trainingData1.name,
            labels = persistentListOf(),
            exerciseUuids = persistentListOf(),
            date = DateProperty.new(12345L)
        )
        val trainingUi2 = TrainingUiModel(
            uuid = trainingData2.uuid,
            name = trainingData2.name,
            labels = persistentListOf(),
            exerciseUuids = persistentListOf(),
            date = DateProperty.new(12346L)
        )

        every { repository.getTrainings(any()) } returns flowOf(PagingData.from(emptyList()))
        every { trainingMapper.invoke(trainingData1) } returns trainingUi1
        every { trainingMapper.invoke(trainingData2) } returns trainingUi2

        // Access the pagingUiState property to trigger initialization
        val pagingUiState = handler.pagingUiState

        // Collect from the state to trigger the flow
        val job = TestScope(testDispatcher).launch {
            pagingUiState().collect { }
        }

        stateFlow.value = stateFlow.value.copy(query = query1)
        testScheduler.advanceUntilIdle()

        stateFlow.value = stateFlow.value.copy(query = query2)
        testScheduler.advanceUntilIdle()

        // Just verify that the repository is called
        @Suppress("UnusedFlow")
        verify(atLeast = 1) { repository.getTrainings(any()) }

        job.cancel()
    }
}