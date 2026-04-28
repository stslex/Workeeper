// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStore
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.mvi.mapper.toSetsDataType
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    private val interactor: PastSessionInteractor,
    store: PastSessionHandlerStore,
) : Handler<Action.Input>, PastSessionHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.OnSetWeightChange -> processWeightChange(action)
            is Action.Input.OnSetRepsChange -> processRepsChange(action)
        }
    }

    private fun processWeightChange(action: Action.Input.OnSetWeightChange) {
        val parsed = action.raw.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val isValid = action.raw.isBlank() || (parsed != null && parsed >= 0.0)
        val updatedSet = updateSetInState(action.setUuid) { set ->
            set.copy(weightInput = action.raw, weightError = !isValid)
        } ?: return
        if (isValid) {
            persistIfReady(updatedSet)
        }
    }

    private fun processRepsChange(action: Action.Input.OnSetRepsChange) {
        val parsed = action.raw.takeIf { it.isNotBlank() }?.toIntOrNull()
        val isValid = parsed != null && parsed > 0
        val updatedSet = updateSetInState(action.setUuid) { set ->
            set.copy(repsInput = action.raw, repsError = !isValid)
        } ?: return
        if (isValid) {
            persistIfReady(updatedSet)
        }
    }

    private fun updateSetInState(
        setUuid: String,
        transform: (PastSetUiModel) -> PastSetUiModel,
    ): PastSetUiModel? {
        val current = state.value.phase as? State.Phase.Loaded ?: return null
        var resultSet: PastSetUiModel? = null
        val updatedDetail = current.detail.copy(
            exercises = current.detail.exercises.map { exercise ->
                if (exercise.sets.none { it.setUuid == setUuid }) {
                    exercise
                } else {
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            if (set.setUuid == setUuid) {
                                transform(set).also { resultSet = it }
                            } else {
                                set
                            }
                        }.toImmutableList(),
                    )
                }
            }.toImmutableList(),
        )
        updateState {
            it.copy(phase = State.Phase.Loaded(detail = updatedDetail))
        }
        return resultSet
    }

    private fun persistIfReady(set: PastSetUiModel) {
        val reps = set.repsInput.toIntOrNull() ?: return
        if (reps <= 0 || set.repsError) return
        val weight = set.weightInput.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        if (set.weightInput.isNotBlank() && weight == null) return
        if (set.weightError) return
        launch(
            onError = { _ -> sendEvent(Event.SaveFailedSnackbar) },
        ) {
            interactor.updateSet(
                performedExerciseUuid = set.performedExerciseUuid,
                position = set.position,
                set = SetsDataModel(
                    uuid = set.setUuid,
                    reps = reps,
                    weight = weight,
                    type = set.type.toSetsDataType(),
                ),
            )
        }
    }
}
