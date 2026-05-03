// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStore
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.mvi.mapper.toSetsDataType
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: PastSessionInteractor,
    store: PastSessionHandlerStore,
) : Handler<Action.Click>, PastSessionHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnBackClick -> processBack()
            Action.Click.OnDeleteClick -> processDeleteClick()
            Action.Click.OnDeleteConfirm -> processDeleteConfirm()
            Action.Click.OnDeleteDismiss -> processDeleteDismiss()
            Action.Click.OnRetryLoad -> consume(Action.Common.Init)
            is Action.Click.OnSetTypeChange -> processSetTypeChange(action)
        }
    }

    private fun processBack() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.Back)
    }

    private fun processDeleteClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(deleteDialogVisible = true) }
    }

    private fun processDeleteDismiss() {
        updateState { it.copy(deleteDialogVisible = false) }
    }

    private fun processDeleteConfirm() {
        sendEvent(Event.HapticClick(HapticFeedbackType.Confirm))
        val sessionUuid = state.value.sessionUuid
        updateState { it.copy(deleteDialogVisible = false) }
        launch(
            onSuccess = {
                sendEvent(Event.DeletedSnackbar)
                consumeOnMain(Action.Navigation.Back)
            },
            onError = { _ -> sendEvent(Event.ShowError(ErrorType.LoadFailed)) },
        ) {
            interactor.deleteSession(sessionUuid)
        }
    }

    private fun processSetTypeChange(action: Action.Click.OnSetTypeChange) {
        val current = state.value.phase as? State.Phase.Loaded ?: return
        val updatedDetail = current.detail.copy(
            exercises = current.detail.exercises.map { exercise ->
                if (exercise.sets.none { it.setUuid == action.setUuid }) {
                    exercise
                } else {
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            if (set.setUuid == action.setUuid) {
                                set.copy(type = action.type)
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
        val targetSet = updatedDetail
            .exercises
            .asSequence()
            .flatMap { it.sets.asSequence() }
            .firstOrNull { it.setUuid == action.setUuid } ?: return
        val weight = targetSet.weightInput.toDoubleOrNull()
        val reps = targetSet.repsInput.toIntOrNull() ?: return
        persistSet(
            performedExerciseUuid = targetSet.performedExerciseUuid,
            position = targetSet.position,
            setUuid = targetSet.setUuid,
            weight = weight,
            reps = reps,
            type = action.type.toSetsDataType(),
        )
    }

    private fun persistSet(
        performedExerciseUuid: String,
        position: Int,
        setUuid: String,
        weight: Double?,
        reps: Int,
        type: io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType,
    ) {
        launch(
            onError = { _ -> sendEvent(Event.SaveFailedSnackbar) },
        ) {
            interactor.updateSet(
                performedExerciseUuid = performedExerciseUuid,
                position = position,
                set = SetsDataModel(
                    uuid = setUuid,
                    reps = reps,
                    weight = weight,
                    type = type,
                ),
            )
        }
    }
}
