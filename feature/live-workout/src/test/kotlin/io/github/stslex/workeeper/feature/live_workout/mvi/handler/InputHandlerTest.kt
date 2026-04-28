// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
        val store = mockk<LiveWorkoutHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }
        val handler = InputHandler(store)

        handler.invoke(Action.Input.OnSetRepsChange("pe-1", 0, 8))

        val draft = stateFlow.value.setDrafts[State.DraftKey("pe-1", 0)]
        assertEquals(8, draft?.reps)
        assertEquals(100.0, draft?.weight)
        assertEquals(SetTypeUiModel.WORK, draft?.type)
    }
}
