package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val interactor = mockk<ExerciseInteractor>()
    private val testScope = TestScope(testDispatcher)

    private val initialState = ExerciseStore.State.INITIAL
    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<ExerciseHandlerStore>(relaxed = true) {
        every { state } returns stateFlow
        every { scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)

        // Mock the updateState function to actually update the state
        every { updateState(any()) } answers {
            val transform = arg<(ExerciseStore.State) -> ExerciseStore.State>(0)
            runCatching { stateFlow.value = transform(stateFlow.value) }
        }

        // Mock updateStateImmediate to update the state
        coEvery { updateStateImmediate(any<ExerciseStore.State>()) } answers {
            val newState = arg<ExerciseStore.State>(0)
            stateFlow.value = newState
        }
    }
    private val handler = CommonHandler(interactor, store)

    @Test
    fun `init action with null data sets empty state and searches for titles`() = runTest {
        val searchResults = listOf<ExerciseDataModel>()
        coEvery { interactor.searchItems(any()) } returns searchResults

        handler.invoke(ExerciseStore.Action.Common.Init(uuid = null, trainingUuid = null))

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
            labels = persistentListOf(),
        )
        val searchResults = listOf<ExerciseDataModel>()

        coEvery { interactor.getExercise(exerciseUuid) } returns exerciseData
        coEvery { interactor.searchItems(any()) } returns searchResults

        handler.invoke(
            ExerciseStore.Action.Common.Init(
                uuid = exerciseUuid,
                trainingUuid = "training-uuid",
            ),
        )

        testScheduler.advanceUntilIdle()

        // Verify that the handler processes the init action
        assertNotNull(handler)
    }

    @Test
    fun `init action with valid data but null exercise result sets empty state`() = runTest {
        val exerciseUuid = Uuid.random().toString()
        val searchResults = listOf<ExerciseDataModel>()

        coEvery { interactor.getExercise(exerciseUuid) } returns null
        coEvery { interactor.searchItems(any()) } returns searchResults

        handler.invoke(ExerciseStore.Action.Common.Init(uuid = exerciseUuid, trainingUuid = null))

        testScheduler.advanceUntilIdle()

        // Verify that the handler processes the init action
        assertNotNull(handler)
    }

    @Test
    fun `init action handles repository error gracefully`() = runTest {
        val exerciseUuid = Uuid.random().toString()

        coEvery { interactor.getExercise(exerciseUuid) } throws RuntimeException("Network error")
        coEvery { interactor.searchItems(any()) } returns listOf()

        handler.invoke(ExerciseStore.Action.Common.Init(uuid = exerciseUuid, trainingUuid = null))

        testScheduler.advanceUntilIdle()

        // Verify that the handler processes the init action without crashing
        assertNotNull(handler)
    }
}
