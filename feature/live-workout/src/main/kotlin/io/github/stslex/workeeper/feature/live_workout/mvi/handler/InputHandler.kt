// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action

@SingleIn(LiveWorkoutScope::class)
internal class InputHandler @Inject constructor(
    store: LiveWorkoutHandlerStore,
) : Handler<Action.Input>, LiveWorkoutHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.OnSetWeightChange -> updateState { current ->
                current.updateSetDraft(
                    performedExerciseUuid = action.performedExerciseUuid,
                    position = action.position,
                    transform = { it.copy(weight = action.value) },
                )
            }

            is Action.Input.OnSetRepsChange -> updateState { current ->
                current.updateSetDraft(
                    performedExerciseUuid = action.performedExerciseUuid,
                    position = action.position,
                    transform = { it.copy(reps = action.value?.coerceAtLeast(0) ?: 0) },
                )
            }
        }
    }
}
