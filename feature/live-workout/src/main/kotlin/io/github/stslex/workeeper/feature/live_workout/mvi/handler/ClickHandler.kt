// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toFinishStats
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.toImmutableSet
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongMethod", "LargeClass")
@ViewModelScoped
// TODO(tech-debt): v2.7 decomposition pass — this handler legitimately gained
// training-name + empty-finish + add-exercise dispatch in v2.3 (per spec); further
// splits (TrainingNameHandler, EmptyFinishHandler) belong with the rest of the
// live-workout feature decomposition.
internal class ClickHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
    private val pickerHandler: ExercisePickerHandler,
    private val setMutator: LiveSetMutator,
    store: LiveWorkoutHandlerStore,
) : Handler<Action.Click>, LiveWorkoutHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.OnSetMarkDone -> processSetMarkDone(action)
            is Action.Click.OnSetUncheck -> processSetUncheck(action)
            is Action.Click.OnSetTypeSelect -> processSetTypeSelect(action)
            is Action.Click.OnSetRemove -> processSetRemove(action)
            is Action.Click.OnAddSet -> processAddSet(action)
            is Action.Click.OnEditPlan -> processEditPlan(action)
            is Action.Click.OnResetSets -> processResetSetsAsk(action)
            is Action.Click.OnResetSetsConfirm -> processResetSetsConfirm(action)
            Action.Click.OnResetSetsDismiss -> processResetSetsDismiss()
            is Action.Click.OnSkipExercise -> processSkipExerciseAsk(action)
            is Action.Click.OnSkipExerciseConfirm -> processSkipExerciseConfirm(action)
            Action.Click.OnSkipExerciseDismiss -> processSkipExerciseDismiss()
            Action.Click.OnFinishClick -> processFinishClick()
            Action.Click.OnFinishConfirm -> processFinishConfirm()
            is Action.Click.OnFinishNameChange -> processFinishNameChange(action)
            Action.Click.OnFinishDismiss -> processFinishDismiss()
            Action.Click.OnCancelSessionClick -> processCancelClick()
            Action.Click.OnCancelSessionConfirm -> processCancelConfirm()
            Action.Click.OnCancelSessionDismiss -> processCancelDismiss()
            Action.Click.OnDeleteSessionMenuClick -> processDeleteSessionMenuClick()
            Action.Click.OnDeleteSessionConfirm -> processDeleteSessionConfirm()
            Action.Click.OnDeleteSessionDismiss -> processDeleteSessionDismiss()
            is Action.Click.OnExerciseHeaderClick -> processExerciseHeaderClick(action)
            Action.Click.OnBackClick -> processBackClick()
            Action.Click.OnTrainingNameTap -> processTrainingNameTap()
            is Action.Click.OnTrainingNameChange -> processTrainingNameChange(action)
            is Action.Click.OnTrainingNameSubmit -> processTrainingNameSubmit(action)
            Action.Click.OnTrainingNameDismiss -> processTrainingNameDismiss()
            Action.Click.OnAddExerciseClick -> processAddExerciseClick()
            is Action.Click.PickerAction -> pickerHandler.invoke(action.action)
            Action.Click.OnEmptyFinishDiscard -> processEmptyFinishDiscard()
            Action.Click.OnEmptyFinishContinue -> processEmptyFinishContinue()
        }
    }

    private fun processAddExerciseClick() {
        val current = state.value
        if (!current.canAddExercise) return
        if (current.sessionUuid.isNullOrBlank() || current.trainingUuid.isNullOrBlank()) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        pickerHandler.open()
    }

    private fun processBackClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        // Spec dismissal order: picker → empty-finish dialog → name edit → plan-editor
        // dirty → default back.
        val current = state.value
        if (current.isPickerVisible) {
            pickerHandler.invoke(ExercisePickerAction.OnDismiss)
            return
        }
        if (current.isEmptyFinishDialogVisible) {
            processEmptyFinishContinue()
            return
        }
        if (current.isTrainingNameEditing) {
            // Submit on back so the keyboard dismiss flow persists changes (per A1
            // "save on blur via tap-out, IME Done, or back-dismissed keyboard").
            processTrainingNameSubmit(Action.Click.OnTrainingNameSubmit(current.trainingNameDraft))
            return
        }
        consume(Action.Navigation.Back)
    }

    private fun processTrainingNameTap() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                isTrainingNameEditing = true,
                trainingNameDraft = current.trainingName,
            )
        }
    }

    private fun processTrainingNameChange(action: Action.Click.OnTrainingNameChange) {
        updateState { it.copy(trainingNameDraft = action.text) }
    }

    private fun processTrainingNameSubmit(action: Action.Click.OnTrainingNameSubmit) {
        val trimmed = action.text.trim()
        val current = state.value
        val trainingUuid = current.trainingUuid
        // Blank submit closes the editor without touching the persisted name. State is
        // left unchanged so the header keeps showing whatever was loaded — placeholder
        // when trainingName is blank, the saved value otherwise. Writing "" to the DB
        // would clobber a previously-saved name on next reload.
        if (trimmed.isBlank()) {
            updateState { latest -> latest.copy(isTrainingNameEditing = false) }
            return
        }
        // Snapshot pre-edit values so a write failure can revert the optimistic update;
        // otherwise State carries the new name while the DB still holds the old one and
        // the next reload silently undoes the user's input.
        val previousName = current.trainingName
        val previousLabel = current.trainingNameLabel
        updateState { latest ->
            latest.copy(
                trainingName = trimmed,
                trainingNameDraft = trimmed,
                trainingNameLabel = trimmed,
                isTrainingNameEditing = false,
            )
        }
        if (trainingUuid.isNullOrBlank() || trimmed == previousName) return
        launch(
            onError = { _ ->
                updateState { latest ->
                    latest.copy(
                        trainingName = previousName,
                        trainingNameLabel = previousLabel,
                    )
                }
                sendError(ErrorType.TrainingNameSaveFailed)
            },
        ) {
            interactor.updateTrainingName(trainingUuid, trimmed)
        }
    }

    private fun processTrainingNameDismiss() {
        // Revert path — used when the keyboard is dismissed without commit (we currently
        // route every blur through Submit, so this fires only from explicit Cancel triggers
        // a future iteration may wire up).
        updateState { current ->
            current.copy(
                isTrainingNameEditing = false,
                trainingNameDraft = current.trainingName,
            )
        }
    }

    private fun processSetMarkDone(action: Action.Click.OnSetMarkDone) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.Confirm))
        val current = state.value
        setMutator.findExercise(current, action.performedExerciseUuid) ?: return
        val seedDraft = setMutator.draftFor(current, action.performedExerciseUuid, action.position)
        if (seedDraft.reps <= 0) {
            sendError(ErrorType.InvalidReps)
            return
        }
        val planSet = PlanSetDomain(
            weight = seedDraft.weight,
            reps = seedDraft.reps,
            type = seedDraft.type.toDomain(),
        )
        // Optimistic UI: flip the row to done immediately so the checkbox tap feels snappy.
        updateState { latest ->
            setMutator.applySetMarked(
                latest,
                action.performedExerciseUuid,
                action.position,
                seedDraft,
            )
        }
        launch(
            onError = { _ ->
                sendError(ErrorType.SetSaveFailed)
                // Revert to a clean reload-shaped state by rebuilding statuses.
                updateState { latest -> setMutator.recomputeStatuses(latest) }
            },
        ) {
            interactor.upsertSet(
                performedExerciseUuid = action.performedExerciseUuid,
                position = action.position,
                set = planSet,
            )
        }
    }

    private fun processSetUncheck(action: Action.Click.OnSetUncheck) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { latest ->
            setMutator.applySetUnchecked(
                latest,
                action.performedExerciseUuid,
                action.position,
            )
        }
        launch(
            onError = { _ -> sendError(ErrorType.SetDeleteFailed) },
        ) {
            interactor.deleteSet(action.performedExerciseUuid, action.position)
        }
    }

    private fun processSetTypeSelect(action: Action.Click.OnSetTypeSelect) {
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        val current = state.value
        val exercise = setMutator.findExercise(current, action.performedExerciseUuid) ?: return
        val performed = exercise.performedSets.firstOrNull { it.position == action.position }
        val nextType = action.type.next()
        if (performed != null && performed.isDone) {
            // For a checked set, type changes are persisted immediately so the saved set
            // matches what the user sees. The optimistic UI update below keeps it instant.
            updateState { latest ->
                setMutator.applySetTypeChange(
                    latest,
                    action.performedExerciseUuid,
                    action.position,
                    nextType,
                )
            }
            launch(
                onError = { _ -> sendError(ErrorType.SetSaveFailed) },
            ) {
                interactor.upsertSet(
                    performedExerciseUuid = action.performedExerciseUuid,
                    position = action.position,
                    set = PlanSetDomain(
                        weight = performed.weight,
                        reps = performed.reps,
                        type = nextType.toDomain(),
                    ),
                )
            }
        } else {
            updateState { latest ->
                latest.updateSetDraft(
                    performedExerciseUuid = action.performedExerciseUuid,
                    position = action.position,
                    transform = { it.copy(type = nextType) },
                )
            }
        }
    }

    private fun processSetRemove(action: Action.Click.OnSetRemove) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { latest ->
            setMutator.applySetUnchecked(
                latest,
                action.performedExerciseUuid,
                action.position,
            )
        }
        launch(
            onError = { _ -> sendError(ErrorType.SetDeleteFailed) },
        ) {
            interactor.deleteSet(action.performedExerciseUuid, action.position)
        }
    }

    private fun processAddSet(action: Action.Click.OnAddSet) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { latest -> setMutator.applyAddSet(latest, action.performedExerciseUuid) }
    }

    private fun processEditPlan(action: Action.Click.OnEditPlan) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val exercise = setMutator.findExercise(current, action.performedExerciseUuid) ?: return
        // Plan editor moved to a dedicated full-screen route in v2.4 (D1). The
        // LiveWorkoutGraph observes a savedStateHandle flag on the previous backstack
        // entry; on flip, it dispatches Action.Common.Reload to refresh planSets.
        consume(
            Action.Navigation.OpenPlanEditor(
                performedExerciseUuid = exercise.performedExerciseUuid,
                exerciseUuid = exercise.exerciseUuid,
                trainingUuid = current.trainingUuid?.takeIf { !current.isAdhoc },
            ),
        )
    }

    private fun processResetSetsAsk(action: Action.Click.OnResetSets) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(pendingResetExerciseUuid = action.performedExerciseUuid) }
        sendEvent(
            Event.ShowResetSetsConfirmDialog(
                dialog = LiveWorkoutStore.ConfirmDialog(
                    title = resourceWrapper.getString(R.string.feature_live_workout_reset_title),
                    body = resourceWrapper.getString(R.string.feature_live_workout_reset_body),
                    confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_reset_confirm),
                    dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_reset_dismiss),
                ),
            ),
        )
    }

    private fun processResetSetsConfirm(action: Action.Click.OnResetSetsConfirm) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { latest -> setMutator.applyResetSets(latest, action.performedExerciseUuid) }
        launch(
            onError = { _ -> sendError(ErrorType.ResetFailed) },
        ) {
            interactor.resetExerciseSets(action.performedExerciseUuid)
        }
    }

    private fun processResetSetsDismiss() {
        updateState { it.copy(pendingResetExerciseUuid = null) }
    }

    private fun processSkipExerciseAsk(action: Action.Click.OnSkipExercise) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(pendingSkipExerciseUuid = action.performedExerciseUuid) }
        sendEvent(
            Event.ShowSkipExerciseConfirmDialog(
                dialog = LiveWorkoutStore.ConfirmDialog(
                    title = resourceWrapper.getString(R.string.feature_live_workout_skip_title),
                    body = resourceWrapper.getString(R.string.feature_live_workout_skip_body),
                    confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_skip_confirm),
                    dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_skip_dismiss),
                ),
            ),
        )
    }

    private fun processSkipExerciseConfirm(action: Action.Click.OnSkipExerciseConfirm) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { latest -> setMutator.applySkip(latest, action.performedExerciseUuid) }
        launch(
            onError = { _ -> sendError(ErrorType.SkipFailed) },
        ) {
            interactor.setSkipped(action.performedExerciseUuid, skipped = true)
        }
    }

    private fun processSkipExerciseDismiss() {
        updateState { it.copy(pendingSkipExerciseUuid = null) }
    }

    private fun processFinishClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        if (current.isSessionEmpty) {
            // E1 lock — empty-finish branches into a confirm dialog. Discard CTA is enabled
            // only when the parent training is ad-hoc, so library training sessions get the
            // Continue-editing-only variant.
            updateState {
                it.copy(
                    emptyFinishDialog = State.EmptyFinishDialogState.Visible(
                        canDiscard = it.isAdhoc,
                        confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_empty_finish_discard),
                        dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_empty_finish_continue),
                    ),
                )
            }
            return
        }
        val stats = current.toFinishStats(resourceWrapper)
        updateState { it.copy(pendingFinishConfirm = stats) }
        sendEvent(Event.ShowFinishConfirmDialog)
    }

    private fun processEmptyFinishContinue() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(emptyFinishDialog = State.EmptyFinishDialogState.Hidden) }
    }

    private fun processEmptyFinishDiscard() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        val current = state.value
        val sessionUuid = current.sessionUuid
        val trainingUuid = current.trainingUuid
        // Defence: discard cascade only fires for ad-hoc trainings. Library sessions can't
        // reach this path (canDiscard = false in the dialog), but we double-check before
        // calling the cascade DAO write.
        if (!current.isAdhoc || sessionUuid.isNullOrBlank() || trainingUuid.isNullOrBlank()) {
            updateState { it.copy(emptyFinishDialog = State.EmptyFinishDialogState.Hidden) }
            return
        }
        updateState {
            it.copy(
                emptyFinishDialog = State.EmptyFinishDialogState.Hidden,
                isFinishInFlight = true,
            )
        }
        launch(
            onSuccess = { consumeOnMain(Action.Navigation.Back) },
            onError = { _ ->
                updateState { it.copy(isFinishInFlight = false) }
                sendError(ErrorType.DiscardSessionFailed)
            },
        ) {
            interactor.discardAdhocSession(
                sessionUuid = sessionUuid,
                trainingUuid = trainingUuid,
            )
        }
    }

    private fun processFinishConfirm() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.Confirm))
        val current = state.value
        val sessionUuid = current.sessionUuid ?: return
        val stats = current.pendingFinishConfirm
        val requiredName = stats?.nameDraft
            ?.trim()
            ?.takeIf { stats.requiresName }
        if (stats?.requiresName == true && requiredName.isNullOrBlank()) {
            val requiredError =
                resourceWrapper.getString(R.string.feature_live_workout_finish_name_required)
            updateState { latest ->
                latest.copy(
                    pendingFinishConfirm = latest.pendingFinishConfirm?.copy(
                        nameError = requiredError,
                        confirmEnabled = false,
                    ),
                )
            }
            return
        }
        val trainingUuid = current.trainingUuid
        if (stats?.requiresName == true && trainingUuid.isNullOrBlank()) {
            sendError(ErrorType.FinishFailed)
            return
        }
        launch(
            onSuccess = { result ->
                if (result == null) {
                    sendError(ErrorType.FinishMissingSession)
                    updateState { it.copy(isFinishInFlight = false) }
                    return@launch
                }
                sendEvent(
                    Event.ShowSessionSavedSnackbar(
                        message = resourceWrapper.getString(R.string.feature_live_workout_finish_success),
                    ),
                )
                consumeOnMain(Action.Navigation.OpenPastSession(sessionUuid = sessionUuid))
            },
            onError = { _ ->
                updateState { it.copy(isFinishInFlight = false) }
                sendError(ErrorType.FinishFailed)
            },
        ) {
            // Rename + finish are paired inside finishSessionAtomic so a crash between
            // the two writes can no longer leave a named-but-IN_PROGRESS session.
            interactor.finishSession(
                sessionUuid = sessionUuid,
                newTrainingName = requiredName,
            )
        }
    }

    private fun processFinishNameChange(action: Action.Click.OnFinishNameChange) {
        val trimmed = action.text.trim()
        val requiredError = resourceWrapper
            .getString(R.string.feature_live_workout_finish_name_required)
            .takeIf { trimmed.isBlank() }
        updateState { latest ->
            val current = latest.pendingFinishConfirm ?: return@updateState latest
            latest.copy(
                pendingFinishConfirm = current.copy(
                    nameDraft = action.text,
                    nameError = requiredError,
                    confirmEnabled = trimmed.isNotBlank(),
                ),
            )
        }
    }

    private fun processFinishDismiss() {
        updateState { it.copy(pendingFinishConfirm = null) }
    }

    private fun processCancelClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.Confirm))
        updateState { it.copy(pendingCancelConfirm = true) }
        sendEvent(
            Event.ShowCancelSessionConfirmDialog(
                dialog = LiveWorkoutStore.ConfirmDialog(
                    title = resourceWrapper.getString(R.string.feature_live_workout_cancel_title),
                    body = resourceWrapper.getString(R.string.feature_live_workout_cancel_body),
                    confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_cancel_confirm),
                    dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_cancel_dismiss),
                ),
            ),
        )
    }

    private fun processCancelConfirm() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        val sessionUuid = state.value.sessionUuid ?: run {
            consume(Action.Navigation.Back)
            return
        }
        launch(
            onSuccess = { consumeOnMain(Action.Navigation.Back) },
            onError = { _ -> sendError(ErrorType.CancelFailed) },
        ) {
            interactor.cancelSession(sessionUuid)
        }
    }

    private fun processCancelDismiss() {
        updateState { it.copy(pendingCancelConfirm = false) }
    }

    private fun processDeleteSessionMenuClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(deleteDialogVisible = true) }
    }

    private fun processDeleteSessionConfirm() {
        val sessionUuid = state.value.sessionUuid ?: run {
            updateState { it.copy(deleteDialogVisible = false) }
            return
        }
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { it.copy(deleteDialogVisible = false) }
        launch(
            onSuccess = { consumeOnMain(Action.Navigation.Back) },
            onError = { _ -> sendError(ErrorType.CancelFailed) },
        ) {
            interactor.cancelSession(sessionUuid)
        }
    }

    private fun processDeleteSessionDismiss() {
        updateState { it.copy(deleteDialogVisible = false) }
    }

    private fun processExerciseHeaderClick(action: Action.Click.OnExerciseHeaderClick) {
        updateState { current ->
            val exercise = setMutator.findExercise(current, action.performedExerciseUuid)
                ?: return@updateState current
            when (exercise.status) {
                ExerciseStatusUiModel.SKIPPED -> current
                ExerciseStatusUiModel.PENDING -> {
                    // Promote to active. Status flips to CURRENT, card expands.
                    val activeNext = current.activeExerciseUuids.toMutableSet().apply {
                        add(action.performedExerciseUuid)
                    }
                    val expandedNext = current.expandedExerciseUuids.toMutableSet().apply {
                        add(action.performedExerciseUuid)
                    }
                    setMutator.recomputeStatuses(
                        current.copy(
                            activeExerciseUuids = activeNext.toImmutableSet(),
                            expandedExerciseUuids = expandedNext.toImmutableSet(),
                        ),
                    )
                }

                ExerciseStatusUiModel.CURRENT -> {
                    // Toggle expanded. If it's the auto-default (not yet in activeUuids),
                    // also promote to explicit-active so the user can later collapse it.
                    val expandedNext = current.expandedExerciseUuids.toMutableSet()
                    if (!expandedNext.add(action.performedExerciseUuid)) {
                        expandedNext.remove(action.performedExerciseUuid)
                    }
                    val activeNext = current.activeExerciseUuids.toMutableSet().apply {
                        add(action.performedExerciseUuid)
                    }
                    current.copy(
                        activeExerciseUuids = activeNext.toImmutableSet(),
                        expandedExerciseUuids = expandedNext.toImmutableSet(),
                    )
                }

                ExerciseStatusUiModel.DONE -> {
                    val expandedNext = current.expandedExerciseUuids.toMutableSet()
                    if (!expandedNext.add(action.performedExerciseUuid)) {
                        expandedNext.remove(action.performedExerciseUuid)
                    }
                    current.copy(expandedExerciseUuids = expandedNext.toImmutableSet())
                }
            }
        }
    }

    private fun sendError(type: ErrorType) {
        sendEvent(
            Event.ShowError(
                message = resourceWrapper.getString(type.msgRes),
            ),
        )
    }
}
