// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanDraftReducer
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.feature.plan_editor.ui.mapper.PlanEditorMapper.toDomain
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.ErrorType
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State.Mode
import javax.inject.Inject

@Suppress("TooManyFunctions")
@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: PlanEditorInteractor,
    store: PlanEditorHandlerStore,
) : Handler<Action.Click>, PlanEditorHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnAddSet -> processAddSet()
            is Action.Click.OnSetRemove -> processRemove(action.index)
            is Action.Click.OnSetTypeChange -> processTypeChange(action.index, action.value)
            Action.Click.OnSave -> processSave()
            Action.Click.OnBackClick -> processBack()
            Action.Click.OnConfirmDiscard -> processDiscard()
            Action.Click.OnConfirmSave -> processConfirmSave()
            Action.Click.OnDismissDiscard -> processDismissDialog()
        }
    }

    private fun processAddSet() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                draft = PlanDraftReducer.reduce(
                    draft = current.draft,
                    action = PlanEditorBodyAction.OnAddSet,
                    isWeighted = current.isWeighted,
                ),
            )
        }
    }

    private fun processRemove(index: Int) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                draft = PlanDraftReducer.reduce(
                    draft = current.draft,
                    action = PlanEditorBodyAction.OnSetRemove(index),
                    isWeighted = current.isWeighted,
                ),
            )
        }
    }

    private fun processTypeChange(index: Int, value: SetTypeUiModel) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                draft = PlanDraftReducer.reduce(
                    draft = current.draft,
                    action = PlanEditorBodyAction.OnSetTypeChange(index, value),
                    isWeighted = current.isWeighted,
                ),
            )
        }
    }

    private fun processBack() {
        if (state.value.isDirty) {
            updateState { it.copy(confirmDiscardOpen = true) }
        } else {
            consume(Action.Navigation.Back)
        }
    }

    private fun processDismissDialog() {
        updateState { it.copy(confirmDiscardOpen = false) }
    }

    private fun processDiscard() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { it.copy(confirmDiscardOpen = false) }
        consume(Action.Navigation.Back)
    }

    private fun processConfirmSave() {
        updateState { it.copy(confirmDiscardOpen = false) }
        processSave()
    }

    private fun processSave() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        if (current.isSaving) return
        updateState { it.copy(isSaving = true) }
        val mode = current.mode
        val (exerciseUuid, trainingUuid) = when (mode) {
            is Mode.Exercise -> mode.exerciseUuid to null
            is Mode.PerformedExercise -> mode.exerciseUuid to mode.trainingUuid
        }
        val plan = current.draft.takeIf { it.isNotEmpty() }?.map { it.toDomain() }
        launch(
            onError = {
                updateState { it.copy(isSaving = false) }
                sendEvent(Event.ShowError(ErrorType.SaveFailed))
            },
        ) {
            interactor.savePlan(
                exerciseUuid = exerciseUuid,
                trainingUuid = trainingUuid,
                plan = plan,
            )
            updateState { it.copy(isSaving = false, initialDraft = state.value.draft) }
            // BackAfterSave pops AND writes the saved-flag to the caller's backstack
            // entry savedStateHandle so the live-workout / exercise-detail screens
            // reload their plan-driven state on resume. (v2.4 D1.)
            consumeOnMain(Action.Navigation.BackAfterSave)
        }
    }
}
