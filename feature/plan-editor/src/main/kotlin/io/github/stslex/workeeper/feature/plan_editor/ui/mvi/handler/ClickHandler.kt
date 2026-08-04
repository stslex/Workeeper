// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanDraftReducer
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorScope
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.feature.plan_editor.ui.mapper.PlanEditorMapper.toDomain
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.ErrorType
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State.Mode
import kotlinx.collections.immutable.toImmutableList
import io.github.stslex.workeeper.core.ui.plan_editor.R as CoreEditorR

@Suppress("TooManyFunctions")
@SingleIn(PlanEditorScope::class)
internal class ClickHandler @Inject constructor(
    private val interactor: PlanEditorInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: PlanEditorHandlerStore,
) : Handler<Action.Click>, PlanEditorHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnAddSet -> processAddSet()
            is Action.Click.OnSetRemove -> processRemove(action.index)
            is Action.Click.OnSetTypeChange -> processSetTypeChange(action.index, action.value)
            is Action.Click.OnTypeToggle -> processTypeToggle(action.target)
            Action.Click.OnTypeChangeConfirm -> processTypeChangeConfirm()
            Action.Click.OnTypeChangeDismiss -> processTypeChangeDismiss()
            Action.Click.OnSave -> processSave()
            Action.Click.OnBackClick -> processBack()
            Action.Click.OnConfirmDiscard -> processDiscard()
            Action.Click.OnDismissDiscard -> processDismissDiscard()
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

    private fun processSetTypeChange(index: Int, value: SetTypeUiModel) {
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

    private fun processTypeToggle(target: ExerciseTypeUiModel) {
        val current = state.value
        if (current.type == target) return
        // Switching from WEIGHTED to WEIGHTLESS while weighted draft rows exist would
        // silently strand weight data. Surface a confirm so the user opts in to the wipe.
        val needsWeightWipe = target == ExerciseTypeUiModel.WEIGHTLESS &&
            current.type == ExerciseTypeUiModel.WEIGHTED &&
            current.draft.any { it.weight != null }
        if (needsWeightWipe) {
            sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
            // Pre-resolve display strings outside the updateState lambda — Rule 1 of
            // compose-state-discipline.
            val title = resourceWrapper.getString(
                CoreEditorR.string.core_ui_plan_editor_type_change_weightless_title,
            )
            val body = resourceWrapper.getString(
                CoreEditorR.string.core_ui_plan_editor_type_change_weightless_body,
            )
            val impactSummary = resourceWrapper.getString(
                CoreEditorR.string.core_ui_plan_editor_type_change_weightless_impact,
            )
            val confirmLabel = resourceWrapper.getString(
                CoreEditorR.string.core_ui_plan_editor_type_change_weightless_confirm,
            )
            updateState {
                it.copy(
                    pendingTypeChange = target,
                    dialogState = DialogState.TypeChangeConfirm(
                        title = title,
                        body = body,
                        impactSummary = impactSummary,
                        confirmLabel = confirmLabel,
                    ),
                )
            }
            return
        }
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        updateState { it.copy(type = target) }
    }

    private fun processTypeChangeConfirm() {
        val pending = state.value.pendingTypeChange ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { latest ->
            val nextDraft = latest.draft.map { it.copy(weight = null) }.toImmutableList()
            latest.copy(
                type = pending,
                pendingTypeChange = null,
                dialogState = DialogState.Hidden,
                draft = nextDraft,
            )
        }
    }

    private fun processTypeChangeDismiss() {
        updateState {
            it.copy(
                pendingTypeChange = null,
                dialogState = DialogState.Hidden,
            )
        }
    }

    private fun processBack() {
        // ONE RULE FOR EVERY MODAL: back dismisses the topmost one, and no variant is exempt.
        // In practice this arm is a fallback rather than the live path — each modal here is an
        // `AppConfirmSheet`, which owns back inside its own window and routes it to
        // `onDismissRequest` before the route sees anything. It must stay non-destructive for
        // exactly that reason: a variant that navigated away instead would turn a stray back
        // press into a silent discard.
        val dialog = state.value.dialogState
        if (dialog !is DialogState.Hidden) {
            updateState { it.copy(dialogState = DialogState.Hidden, pendingTypeChange = null) }
            return
        }
        if (state.value.isDirty) {
            updateState { it.copy(dialogState = DialogState.DiscardConfirm) }
        } else {
            consume(Action.Navigation.Back)
        }
    }

    private fun processDismissDiscard() {
        updateState { it.copy(dialogState = DialogState.Hidden) }
    }

    private fun processDiscard() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { it.copy(dialogState = DialogState.Hidden) }
        consume(Action.Navigation.Back)
    }

    // NO SAVE ACTION ON THE DISCARD SHEET: it appears only when there is something to lose and
    // saving already lives on the form, so a third action would be a second door to a room the
    // user is standing in (§26, "Every modal on the three editors is a SHEET").

    private fun processSave() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        if (current.isSaving) return
        persistAndPop(current.mode)
    }

    private fun persistAndPop(mode: Mode) {
        val current = state.value
        updateState { it.copy(isSaving = true) }
        val (exerciseUuid, trainingUuid) = when (mode) {
            is Mode.Exercise -> mode.exerciseUuid to null
            is Mode.PerformedExercise -> mode.exerciseUuid to mode.trainingUuid
        }
        val plan = current.draft.takeIf { it.isNotEmpty() }?.map { it.toDomain() }
        val type = current.type.toDomain()
        launch(
            onError = {
                updateState { it.copy(isSaving = false) }
                sendEvent(Event.ShowError(ErrorType.SaveFailed))
            },
        ) {
            interactor.savePlan(
                exerciseUuid = exerciseUuid,
                trainingUuid = trainingUuid,
                type = type,
                plan = plan,
            )
            updateState { current ->
                current.copy(
                    isSaving = false,
                    initialDraft = current.draft,
                    initialType = current.type,
                )
            }
            // BackAfterSave pops AND writes the saved-flag to the caller's backstack
            // entry savedStateHandle so the consumer reloads its plan + type-driven
            // state on resume.
            consumeOnMain(Action.Navigation.BackAfterSave)
        }
    }
}
