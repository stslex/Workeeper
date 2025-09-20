package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class CommonHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val store = mockk<ExerciseHandlerStore>(relaxed = true)
    private val testScope = TestScope(testDispatcher)

    private val initialState = ExerciseStore.State.INITIAL
    private val stateFlow = MutableStateFlow(initialState)
    private val handler = CommonHandler(exerciseRepository, store)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { store.state } returns stateFlow
        every { store.scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init action with null data sets empty state and searches for titles`() = runTest {
        val searchResults = listOf<ExerciseDataModel>()
        coEvery { exerciseRepository.searchItems(any()) } returns searchResults

        handler.invoke(ExerciseStore.Action.Common.Init(null))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { exerciseRepository.searchItems(any()) }
    }

    @Test
    fun `init action with valid data loads exercise and searches for titles`() = runTest {
        val exerciseUuid = Uuid.random().toString()
        val exerciseData = ExerciseDataModel(
            uuid = exerciseUuid,
            name = "Test Exercise",
            sets = persistentListOf(),
            timestamp = 1500000L,
            trainingUuid = "training-uuid",
            labels = persistentListOf()
        )
        val screenData = Screen.Exercise.Data(exerciseUuid)
        val searchResults = listOf<ExerciseDataModel>()

        coEvery { exerciseRepository.getExercise(exerciseUuid) } returns exerciseData
        coEvery { exerciseRepository.searchItems(any()) } returns searchResults

        handler.invoke(ExerciseStore.Action.Common.Init(screenData))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { exerciseRepository.getExercise(exerciseUuid) }
        coVerify(exactly = 1) { exerciseRepository.searchItems(any()) }
    }

    @Test
    fun `init action with valid data but null exercise result sets empty state`() = runTest {
        val exerciseUuid = Uuid.random().toString()
        val screenData = Screen.Exercise.Data(exerciseUuid)
        val searchResults = listOf<ExerciseDataModel>()

        coEvery { exerciseRepository.getExercise(exerciseUuid) } returns null
        coEvery { exerciseRepository.searchItems(any()) } returns searchResults

        handler.invoke(ExerciseStore.Action.Common.Init(screenData))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { exerciseRepository.getExercise(exerciseUuid) }
        coVerify(exactly = 1) { exerciseRepository.searchItems(any()) }
    }

    @Test
    fun `init action handles repository error gracefully`() = runTest {
        val exerciseUuid = Uuid.random().toString()
        val screenData = Screen.Exercise.Data(exerciseUuid)

        coEvery { exerciseRepository.getExercise(exerciseUuid) } throws RuntimeException("Network error")
        coEvery { exerciseRepository.searchItems(any()) } returns listOf()

        handler.invoke(ExerciseStore.Action.Common.Init(screenData))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { exerciseRepository.getExercise(exerciseUuid) }
        coVerify(exactly = 1) { exerciseRepository.searchItems(any()) }
    }
}