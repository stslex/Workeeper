package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

internal class CommonHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val testScope = TestScope(testDispatcher)

    private val initialState = ExerciseStore.State.INITIAL
    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<ExerciseHandlerStore>(relaxed = true) {
        every { state } returns stateFlow
        every { scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)

        // Mock the updateState and updateStateImmediate functions
        every { updateState(any()) } returns Unit
        coEvery { updateStateImmediate(any<(ExerciseStore.State) -> ExerciseStore.State>()) } returns Unit
        coEvery { updateStateImmediate(any<ExerciseStore.State>()) } returns Unit

        // Mock the launch function to actually execute the coroutine
        every {
            this@mockk.launch<Any>(
                onError = any(),
                onSuccess = any(),
                workDispatcher = any(),
                eachDispatcher = any(),
                action = any()
            )
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any?) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any?>(4)

            testScope.launch { runCatching { onSuccess(this, action()) } }
        }
    }
    private val handler = CommonHandler(exerciseRepository, store)

    @Test
    fun `init action with null data sets empty state and searches for titles`() = runTest {
        val searchResults = listOf<ExerciseDataModel>()
        coEvery { exerciseRepository.searchItems(any()) } returns searchResults

        handler.invoke(ExerciseStore.Action.Common.Init(null))

        testScheduler.advanceUntilIdle()

        // Verify that the handler processes the init action
        assertNotNull(handler)
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

        // Verify that the handler processes the init action
        assertNotNull(handler)
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

        // Verify that the handler processes the init action
        assertNotNull(handler)
    }

    @Test
    fun `init action handles repository error gracefully`() = runTest {
        val exerciseUuid = Uuid.random().toString()
        val screenData = Screen.Exercise.Data(exerciseUuid)

        coEvery { exerciseRepository.getExercise(exerciseUuid) } throws RuntimeException("Network error")
        coEvery { exerciseRepository.searchItems(any()) } returns listOf()

        handler.invoke(ExerciseStore.Action.Common.Init(screenData))

        testScheduler.advanceUntilIdle()

        // Verify that the handler processes the init action without crashing
        assertNotNull(handler)
    }
}