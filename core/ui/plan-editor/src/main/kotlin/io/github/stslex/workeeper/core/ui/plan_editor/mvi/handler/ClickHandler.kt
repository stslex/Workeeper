// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.ErrorType
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.State.Mode
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

private const val DEFAULT_NEW_REPS = 5

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
            val previous = current.draft.lastOrNull()
            val nextSet = previous?.copy(type = SetTypeUiModel.WORK) ?: PlanSetUiModel(
                weight = null,
                reps = DEFAULT_NEW_REPS,
                type = SetTypeUiModel.WORK,
            )
            current.copy(draft = (current.draft + nextSet).toImmutableList())
        }
    }

    private fun processRemove(index: Int) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            if (index !in current.draft.indices) return@updateState current
            current.copy(
                draft = current.draft.toMutableList()
                    .also { it.removeAt(index) }
                    .toImmutableList(),
            )
        }
    }

    private fun processTypeChange(index: Int, value: SetTypeUiModel) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            if (index !in current.draft.indices) return@updateState current
            current.copy(
                draft = current.draft.toMutableList()
                    .apply { this[index] = this[index].copy(type = value) }
                    .toImmutableList(),
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
            consumeOnMain(Action.Navigation.Back)
        }
    }
}
