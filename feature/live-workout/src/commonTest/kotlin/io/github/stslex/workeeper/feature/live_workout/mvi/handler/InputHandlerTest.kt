// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

internal class InputHandlerTest {

    @Test
    fun `OnSetRepsChange updates draft map for the targeted set`() {
        val stateFlow = MutableStateFlow(
            State.create(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
            ).copy(
                isLoading = false,
                exercises = persistentListOf(
                    LiveExerciseUiModel(
                        performedExerciseUuid = "pe-1",
                        exerciseUuid = "ex-1",
                        exerciseName = "Bench",
                        exerciseType = ExerciseTypeUiModel.WEIGHTED,
                        position = 0,
                        status = ExerciseStatusUiModel.CURRENT,
                        statusLabel = "",
                        planSets = persistentListOf(
                            PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                        ),
                        performedSets = persistentListOf(),
                    ),
                ),
            ),
        )
        val store = FakeInputHandlerStore(stateFlow)
        val handler = InputHandler(store)

        handler.invoke(Action.Input.OnSetRepsChange("pe-1", 0, 8))

        val draft = stateFlow.value.setDrafts[State.DraftKey("pe-1", 0)]
        assertEquals(8, draft?.reps)
        assertEquals(100.0, draft?.weight)
        assertEquals(SetTypeUiModel.WORK, draft?.type)
    }

    /** Strict: an input writes only through [updateState]; every other member fails fast. */
    private class FakeInputHandlerStore(
        override val state: MutableStateFlow<State>,
    ) : LiveWorkoutHandlerStore {

        override val lastAction: Nothing get() = unreached("lastAction")

        override val logger: Nothing get() = unreached("logger")

        override fun sendEvent(event: Event): Nothing = unreached("sendEvent")

        override fun consume(action: Action): Nothing = unreached("consume")

        override suspend fun consumeOnMain(action: Action): Nothing = unreached("consumeOnMain")

        override fun updateState(update: (State) -> State) {
            state.value = update(state.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
            unreached("updateStateImmediate")

        override suspend fun updateStateImmediate(state: State): Nothing = unreached("updateStateImmediate")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = unreached("launch")

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = unreached("launchDefault")

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Nothing = unreached("Flow.launch")

        private fun unreached(member: String): Nothing = error("InputHandler does not reach $member")
    }
}
