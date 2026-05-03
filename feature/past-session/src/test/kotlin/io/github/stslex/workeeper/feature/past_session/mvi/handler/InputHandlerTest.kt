// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStore
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class InputHandlerTest {

    private val interactor = mockk<PastSessionInteractor>(relaxed = true)

    @Test
    fun `OnSetWeightChange marks invalid weight and skips persist`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(loadedState(), this, dispatcher)
        val handler = InputHandler(interactor = interactor, store = store)

        handler.invoke(Action.Input.OnSetWeightChange(setUuid = SET_UUID, raw = "oops"))
        advanceUntilIdle()

        assertEquals("oops", currentSet(store).weightInput)
        assertTrue(currentSet(store).weightError)
        coVerify(exactly = 0) { interactor.updateSet(any(), any(), any()) }
    }

    @Test
    fun `OnSetRepsChange clears reps error when value becomes valid`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(
            loadedState(set = baseSet(repsInput = "0", repsError = true)),
            this,
            dispatcher,
        )
        val handler = InputHandler(interactor = interactor, store = store)

        handler.invoke(Action.Input.OnSetRepsChange(setUuid = SET_UUID, raw = "9"))

        assertEquals("9", currentSet(store).repsInput)
        assertFalse(currentSet(store).repsError)
    }

    @Test
    fun `rapid valid inputs debounce to a single persist with the latest value`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(loadedState(), this, dispatcher)
        val handler = InputHandler(interactor = interactor, store = store)

        handler.invoke(Action.Input.OnSetWeightChange(setUuid = SET_UUID, raw = "1"))
        handler.invoke(Action.Input.OnSetWeightChange(setUuid = SET_UUID, raw = "15"))
        handler.invoke(Action.Input.OnSetWeightChange(setUuid = SET_UUID, raw = "150"))

        advanceTimeBy(299)
        coVerify(exactly = 0) { interactor.updateSet(any(), any(), any()) }

        advanceTimeBy(1)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            interactor.updateSet(
                performedExerciseUuid = PERFORMED_EXERCISE_UUID,
                position = SET_POSITION,
                set = match { set ->
                    set.uuid == SET_UUID &&
                        set.reps == 8 &&
                        set.weight == 150.0 &&
                        set.type == SetsDataType.WORK
                },
            )
        }
    }

    @Test
    fun `invalid follow-up input cancels in-flight persist`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(loadedState(), this, dispatcher)
        val handler = InputHandler(interactor = interactor, store = store)

        handler.invoke(Action.Input.OnSetWeightChange(setUuid = SET_UUID, raw = "120"))
        handler.invoke(Action.Input.OnSetWeightChange(setUuid = SET_UUID, raw = "12x"))
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals("12x", currentSet(store).weightInput)
        assertTrue(currentSet(store).weightError)
        coVerify(exactly = 0) { interactor.updateSet(any(), any(), any()) }
    }

    @Test
    fun `persist failure reverts to the last saved set and emits SaveFailedSnackbar`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(loadedState(), this, dispatcher)
        val handler = InputHandler(interactor = interactor, store = store)
        coEvery { interactor.updateSet(any(), any(), any()) } throws IllegalStateException("boom")

        handler.invoke(Action.Input.OnSetWeightChange(setUuid = SET_UUID, raw = "110"))
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(baseSet(), currentSet(store))
        assertEquals(listOf(Event.SaveFailedSnackbar), store.events)
    }

    private fun currentSet(store: TestStore): PastSetUiModel =
        ((store.state.value.phase as State.Phase.Loaded).detail.exercises.single().sets.single())

    private fun loadedState(set: PastSetUiModel = baseSet()): State = State.create(sessionUuid = SESSION_UUID)
        .copy(
            phase = State.Phase.Loaded(
                detail = PastSessionUiModel(
                    trainingName = "Push Day",
                    isAdhoc = false,
                    finishedAtAbsoluteLabel = "Apr 28",
                    durationLabel = "01:00",
                    totalsLabel = "1 exercise · 1 set",
                    volumeLabel = "800 kg total",
                    exercises = persistentListOf(
                        PastExerciseUiModel(
                            performedExerciseUuid = PERFORMED_EXERCISE_UUID,
                            exerciseName = "Bench",
                            position = 0,
                            skipped = false,
                            isWeighted = true,
                            sets = persistentListOf(set),
                        ),
                    ),
                ),
            ),
        )

    private fun baseSet(
        weightInput: String = "100",
        repsInput: String = "8",
        weightError: Boolean = false,
        repsError: Boolean = false,
        type: SetTypeUiModel = SetTypeUiModel.WORK,
    ): PastSetUiModel = PastSetUiModel(
        setUuid = SET_UUID,
        performedExerciseUuid = PERFORMED_EXERCISE_UUID,
        position = SET_POSITION,
        type = type,
        weightInput = weightInput,
        repsInput = repsInput,
        weightError = weightError,
        repsError = repsError,
        isPersonalRecord = false,
    )

    private class TestStore(
        initialState: State,
        private val testScope: TestScope,
        private val dispatcher: TestDispatcher,
    ) : PastSessionHandlerStore {

        private var action: Action? = null

        override val state = MutableStateFlow(initialState)
        override val lastAction: Action? get() = action
        override val logger: Logger = mockk(relaxed = true)

        val events = mutableListOf<Event>()

        override fun sendEvent(event: Event) {
            events += event
        }

        override fun consume(action: Action) {
            this.action = action
        }

        override suspend fun consumeOnMain(action: Action) {
            this.action = action
        }

        override fun updateState(update: (State) -> State) {
            state.value = update(state.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            state.value = update(state.value)
        }

        override suspend fun updateStateImmediate(state: State) {
            this.state.value = state
        }

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job = testScope.launch(workDispatcher ?: dispatcher) {
            runCatching { action() }
                .onSuccess {
                    withContext(eachDispatcher ?: dispatcher) { onSuccess(it) }
                }
                .onFailure {
                    withContext(eachDispatcher ?: dispatcher) { onError(it) }
                }
        }

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = this
            .catch { onError(it) }
            .onEach { withContext(eachDispatcher ?: dispatcher) { each(it) } }
            .flowOn(workDispatcher ?: dispatcher)
            .launchIn(testScope)
    }

    private companion object {

        const val SESSION_UUID = "session-1"
        const val SET_UUID = "set-1"
        const val PERFORMED_EXERCISE_UUID = "performed-1"
        const val SET_POSITION = 2
    }
}
