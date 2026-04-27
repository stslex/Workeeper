// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.toImmutableMap
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    store: LiveWorkoutHandlerStore,
) : Handler<Action.Input>, LiveWorkoutHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.OnSetWeightChange -> updateDraft(
                performedExerciseUuid = action.performedExerciseUuid,
                position = action.position,
                transform = { it.copy(weight = action.value) },
            )

            is Action.Input.OnSetRepsChange -> updateDraft(
                performedExerciseUuid = action.performedExerciseUuid,
                position = action.position,
                transform = { it.copy(reps = action.value?.coerceAtLeast(0) ?: 0) },
            )
        }
    }

    private fun updateDraft(
        performedExerciseUuid: String,
        position: Int,
        transform: (LiveSetUiModel) -> LiveSetUiModel,
    ) {
        updateState { current ->
            val key = State.DraftKey(performedExerciseUuid, position)
            val template = current.setDrafts[key] ?: current.lookupDraftSeed(
                performedExerciseUuid = performedExerciseUuid,
                position = position,
            )
            val updated = transform(template)
            current.copy(
                setDrafts = (current.setDrafts + (key to updated)).toImmutableMap(),
            )
        }
    }

    private fun State.lookupDraftSeed(
        performedExerciseUuid: String,
        position: Int,
    ): LiveSetUiModel {
        val exercise = exercises.firstOrNull { it.performedExerciseUuid == performedExerciseUuid }
        val performed = exercise?.performedSets?.firstOrNull { it.position == position }
        if (performed != null) return performed
        val plan = exercise?.planSets?.getOrNull(position)
        return LiveSetUiModel(
            position = position,
            weight = plan?.weight,
            reps = plan?.reps ?: 0,
            type = plan?.type ?: SetTypeUiModel.WORK,
            isDone = false,
        )
    }
}
