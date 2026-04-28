// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStore
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<PastSessionInteractor>(relaxed = true)

    @Test
    fun `OnDeleteClick shows dialog and emits ContextClick haptic`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(loadedState(), this, dispatcher)
        val handler = ClickHandler(interactor = interactor, store = store)

        handler.invoke(Action.Click.OnDeleteClick)

        assertTrue(store.state.value.deleteDialogVisible)
        assertEquals(
            listOf(Event.HapticClick(HapticFeedbackType.ContextClick)),
            store.events,
        )
    }

    @Test
    fun `OnDeleteConfirm deletes the session closes dialog and navigates back with snackbar`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(loadedState(deleteDialogVisible = true), this, dispatcher)
        val handler = ClickHandler(interactor = interactor, store = store)

        handler.invoke(Action.Click.OnDeleteConfirm)
        advanceUntilIdle()

        coVerify(exactly = 1) { interactor.deleteSession(SESSION_UUID) }
        assertFalse(store.state.value.deleteDialogVisible)
        assertEquals(
            listOf(
                Event.HapticClick(HapticFeedbackType.Confirm),
                Event.DeletedSnackbar,
            ),
            store.events,
        )
        assertEquals(listOf(Action.Navigation.Back), store.consumedActions)
    }

    @Test
    fun `OnRetryLoad consumes Init`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(loadedState(), this, dispatcher)
        val handler = ClickHandler(interactor = interactor, store = store)

        handler.invoke(Action.Click.OnRetryLoad)

        assertEquals(listOf(Action.Common.Init), store.consumedActions)
    }

    @Test
    fun `OnSetTypeChange updates the row and persists the changed type`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(loadedState(), this, dispatcher)
        val handler = ClickHandler(interactor = interactor, store = store)

        handler.invoke(
            Action.Click.OnSetTypeChange(
                setUuid = SET_UUID,
                type = SetTypeUiModel.FAILURE,
            ),
        )
        advanceUntilIdle()

        val updatedSet = currentSet(store)
        assertEquals(SetTypeUiModel.FAILURE, updatedSet.type)
        coVerify(exactly = 1) {
            interactor.updateSet(
                performedExerciseUuid = PERFORMED_EXERCISE_UUID,
                position = SET_POSITION,
                set = match { set ->
                    set.uuid == SET_UUID &&
                        set.reps == 8 &&
                        set.weight == 100.0 &&
                        set.type == SetsDataType.FAIL
                },
            )
        }
    }

    private fun currentSet(store: TestStore): PastSetUiModel =
        ((store.state.value.phase as State.Phase.Loaded).detail.exercises.single().sets.single())

    private fun loadedState(deleteDialogVisible: Boolean = false): State = State.create(sessionUuid = SESSION_UUID)
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
                            sets = persistentListOf(
                                PastSetUiModel(
                                    setUuid = SET_UUID,
                                    performedExerciseUuid = PERFORMED_EXERCISE_UUID,
                                    position = SET_POSITION,
                                    type = SetTypeUiModel.WORK,
                                    weightInput = "100",
                                    repsInput = "8",
                                    weightError = false,
                                    repsError = false,
                                    isPersonalRecord = false,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            deleteDialogVisible = deleteDialogVisible,
        )

    private class TestStore(
        initialState: State,
        testScope: TestScope,
        dispatcher: TestDispatcher,
    ) : PastSessionHandlerStore {

        private var action: Action? = null

        override val state = MutableStateFlow(initialState)
        override val lastAction: Action? get() = action
        override val logger: Logger = mockk(relaxed = true)
        override val scope = AppCoroutineScope(
            scope = testScope,
            defaultDispatcher = dispatcher,
            immediateDispatcher = dispatcher,
        )

        val events = mutableListOf<Event>()
        val consumedActions = mutableListOf<Action>()

        override fun sendEvent(event: Event) {
            events += event
        }

        override fun consume(action: Action) {
            this.action = action
            consumedActions += action
        }

        override suspend fun consumeOnMain(action: Action) {
            this.action = action
            consumedActions += action
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
    }

    private companion object {

        const val SESSION_UUID = "session-1"
        const val SET_UUID = "set-1"
        const val PERFORMED_EXERCISE_UUID = "performed-1"
        const val SET_POSITION = 2
    }
}
