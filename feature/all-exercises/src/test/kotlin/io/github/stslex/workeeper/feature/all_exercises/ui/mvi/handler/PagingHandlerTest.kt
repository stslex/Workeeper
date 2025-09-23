package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore
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
    private val repository = mockk<ExerciseRepository>(relaxed = true)

    private val initialQuery = "initial_query"

    private val store = mockk<ExerciseHandlerStore>(relaxed = true)
    private val handler: PagingHandler = PagingHandler(repository, testDispatcher, store)

    private val initialState = ExercisesStore.State(
        items = handler.processor,
        selectedItems = persistentSetOf(),
        query = initialQuery
    )
    private val stateFlow = MutableStateFlow(initialState)

    @Suppress("UnusedFlow")
    @Test
    fun `processor transforms data correctly with finite flow`() = runTest(testDispatcher) {
        val testData = getTestData()
        val expectedData = getExpectedData()

        every { repository.getExercises(any()) } returns flowOf(getNotLoadingData(testData))
        every { store.state } returns stateFlow

        val result = handler.processor.invoke().asSnapshot()

        assertEquals(expectedData, result)
        verify(exactly = 1) { repository.getExercises(initialQuery) }
    }

    @Suppress("UnusedFlow")
    @Test
    fun `processor transforms data correctly with empty pagingData`() = runTest(testDispatcher) {
        every { repository.getExercises(any()) } returns flowOf(getNotLoadingData(emptyList()))
        every { store.state } returns stateFlow

        val result = handler.processor.invoke().asSnapshot()

        assertEquals(emptyList<ExerciseUiModel>(), result)
        verify(exactly = 1) { repository.getExercises(initialQuery) }
    }

    @Suppress("UnusedFlow")
    @Test
    fun `processor transforms data correctly on query changes`() = runTest(testDispatcher) {
        val expectedQuery = "new_expected_query"
        val expectedData = getExpectedData()
        every { repository.getExercises(initialQuery) } returns flowOf(getNotLoadingData(emptyList()))
        every { repository.getExercises(expectedQuery) } returns flowOf(
            getNotLoadingData(
                getTestData()
            )
        )
        every { store.state } returns stateFlow

        val processor = handler.processor.invoke()
        val emptySnapshot = processor.asSnapshot()

        assertEquals(emptyList<ExerciseUiModel>(), emptySnapshot)
        verify(exactly = 1) { repository.getExercises(initialQuery) }

        stateFlow.update { it.copy(query = expectedQuery) }

        val dataSnapshot = processor.asSnapshot()

        assertEquals(expectedData, dataSnapshot)
        verify(exactly = 1) { repository.getExercises(expectedQuery) }
    }

    private fun getNotLoadingData(data: List<ExerciseDataModel>): PagingData<ExerciseDataModel> =
        PagingData.from(
            data,
            LoadStates(
                refresh = LoadState.NotLoading(true),
                prepend = LoadState.NotLoading(true),
                append = LoadState.NotLoading(true),
            )
        )

    private fun getTestData(): List<ExerciseDataModel> = listOf(
        ExerciseDataModel(
            uuid = "ex1",
            name = "Push ups",
            sets = persistentListOf(),
            timestamp = 1000L,
            trainingUuid = null,
            labels = persistentListOf()
        ),
        ExerciseDataModel(
            uuid = "ex2",
            name = "Squats",
            sets = persistentListOf(),
            timestamp = 2000L,
            trainingUuid = null,
            labels = persistentListOf()
        )
    )

    private fun getExpectedData(): List<ExerciseUiModel> = listOf(
        ExerciseUiModel(
            uuid = "ex1",
            name = "Push ups",
            dateProperty = PropertyHolder.DateProperty(initialValue = 1000L)
        ),
        ExerciseUiModel(
            uuid = "ex2",
            name = "Squats",
            dateProperty = PropertyHolder.DateProperty(initialValue = 2000L)
        )
    )
}
