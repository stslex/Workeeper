package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiMapper
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class PagingHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val repository = mockk<TrainingRepository>(relaxed = true)
    private val trainingMapper = mockk<TrainingUiMapper>(relaxed = true)

    private val pagingUiState = mockk<PagingUiState<PagingData<TrainingUiModel>>>(relaxed = true)
    private val stateFlow = MutableStateFlow(
        TrainingStore.State.init(pagingUiState)
    )
    private val store = mockk<TrainingHandlerStore>(relaxed = true) {
        every { this@mockk.state } returns stateFlow
    }
    private val handler = PagingHandler(repository, trainingMapper, testDispatcher, store)

    @Test
    fun `paging ui state transforms data correctly with finite flow`() = runTest(testDispatcher) {
        // Создаем тестовые данные
        val testData = listOf(
            TrainingDataModel(
                uuid = "t1",
                name = "Morning Workout",
                labels = emptyList(),
                exerciseUuids = emptyList(),
                timestamp = 1000L
            ),
            TrainingDataModel(
                uuid = "t2",
                name = "Evening Training",
                labels = emptyList(),
                exerciseUuids = emptyList(),
                timestamp = 2000L
            )
        )

        // Мокаем mapper для преобразования данных
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

        // Мокаем repository для возврата конечного потока
        coEvery { repository.getTrainings("") } returns flow {
            emit(PagingData.from(testData))
        }

        // Получаем данные из paging ui state
        val result = handler.pagingUiState.invoke()
        testScheduler.advanceUntilIdle()

        // Используем asSnapshot для проверки данных
        val snapshot = result.asSnapshot()
        assertEquals(2, snapshot.size)
        assertEquals("t1", snapshot[0].uuid)
        assertEquals("Morning Workout", snapshot[0].name)
        assertEquals("t2", snapshot[1].uuid)
        assertEquals("Evening Training", snapshot[1].name)

        // Проверяем, что repository был вызван
        verify(exactly = 1) { repository.getTrainings("") }
        // Проверяем, что mapper был вызван для каждого элемента
        verify(exactly = 2) { trainingMapper.invoke(any()) }
    }

    @Test
    fun `paging ui state handles empty data correctly`() = runTest(testDispatcher) {
        // Мокаем repository для возврата пустых данных
        coEvery { repository.getTrainings(any()) } returns flow {
            emit(PagingData.empty<TrainingDataModel>())
        }

        val result = handler.pagingUiState.invoke()
        testScheduler.advanceUntilIdle()

        val snapshot = result.asSnapshot()
        assertEquals(0, snapshot.size)

        verify(exactly = 1) { repository.getTrainings("") }
        // Mapper не должен вызываться для пустых данных
        verify(exactly = 0) { trainingMapper.invoke(any()) }
    }

    @Test
    fun `paging ui state reacts to query changes correctly`() = runTest(testDispatcher) {
        val testData1 = listOf(
            TrainingDataModel(
                uuid = "t1",
                name = "Training 1",
                labels = emptyList(),
                exerciseUuids = emptyList(),
                timestamp = 1000L
            )
        )

        val testData2 = listOf(
            TrainingDataModel(
                uuid = "t2",
                name = "Training 2",
                labels = emptyList(),
                exerciseUuids = emptyList(),
                timestamp = 2000L
            )
        )

        // Мокаем mapper
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

        // Мокаем разные ответы для разных запросов
        coEvery { repository.getTrainings("query1") } returns flow {
            emit(PagingData.from(testData1))
        }

        coEvery { repository.getTrainings("query2") } returns flow {
            emit(PagingData.from(testData2))
        }

        // Устанавливаем первый запрос
        stateFlow.value = TrainingStore.State.init(pagingUiState).copy(query = "query1")
        val result1 = handler.pagingUiState.invoke()
        testScheduler.advanceUntilIdle()

        val snapshot1 = result1.asSnapshot()
        assertEquals(1, snapshot1.size)
        assertEquals("Training 1", snapshot1[0].name)

        // Изменяем запрос
        stateFlow.value = TrainingStore.State.init(pagingUiState).copy(query = "query2")
        val result2 = handler.pagingUiState.invoke()
        testScheduler.advanceUntilIdle()

        val snapshot2 = result2.asSnapshot()
        assertEquals(1, snapshot2.size)
        assertEquals("Training 2", snapshot2[0].name)

        // Проверяем, что repository был вызван с правильными запросами
        verify(exactly = 1) { repository.getTrainings("query1") }
        verify(exactly = 1) { repository.getTrainings("query2") }
    }
}
