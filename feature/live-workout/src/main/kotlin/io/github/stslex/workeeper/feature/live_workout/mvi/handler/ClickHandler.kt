// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.PendingUndoOps.flushPendingUndo
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.PendingUndoOps.pushUndo
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.PendingUndoOps.undoPending
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toFinishStats
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toPlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.PendingUndo
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@Suppress("TooManyFunctions", "LongMethod", "LargeClass")
@SingleIn(LiveWorkoutScope::class)
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
            is Action.Click.OnRemoveLastSet -> processRemoveLastSet(action)
            is Action.Click.OnEditPlan -> processEditPlan(action)
            is Action.Click.OnResetSets -> processResetSetsAsk(action)
            is Action.Click.OnSkipExercise -> processSkipExerciseToggle(action)
            Action.Click.OnFinishClick -> processFinishClick()
            Action.Click.OnCancelSessionClick -> processCancelClick()
            Action.Click.OnDeleteSessionMenuClick -> processDeleteSessionMenuClick()
            is Action.Click.OnExerciseHeaderClick -> processExerciseHeaderClick(action)
            Action.Click.OnBackClick -> processBackClick()
            Action.Click.OnTrainingNameTap -> processTrainingNameTap()
            is Action.Click.OnTrainingNameChange -> processTrainingNameChange(action)
            is Action.Click.OnTrainingNameSubmit -> processTrainingNameSubmit(action)
            Action.Click.OnTrainingNameDismiss -> processTrainingNameDismiss()
            Action.Click.OnAddExerciseClick -> processAddExerciseClick()
            Action.Click.OnSessionMenuClick -> processSessionMenuClick()
            is Action.Click.OnExerciseMenuClick -> processExerciseMenuClick(action)
            is Action.Click.OnShowDescription -> processShowDescription(action)
            is Action.Click.OnToggleOneOff -> processToggleOneOff(action)
            is Action.Click.OnDeleteExerciseClick -> processDeleteExerciseClick(action)
            Action.Click.OnSheetDismiss -> processSheetDismiss()
            Action.Click.OnUndoClick -> processUndoClick()
            is Action.Click.OnUndoTimeout -> processUndoTimeout(action)
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
        if (current.bottomSheetState is BottomSheetState.ExercisePicker) {
            pickerHandler.invoke(ExercisePickerAction.OnDismiss)
            return
        }
        if (current.dialogState is DialogState.EmptyFinish) {
            consume(Action.DialogClick.OnEmptyFinishContinue)
            return
        }
        if (current.isTrainingNameEditing) {
            // Submit on back so the keyboard dismiss flow persists changes (per A1
            // "save on blur via tap-out, IME Done, or back-dismissed keyboard").
            processTrainingNameSubmit(Action.Click.OnTrainingNameSubmit(current.trainingNameDraft))
            return
        }
        // Leaving the screen closes the undo window — the deferred §6.1 delete commits now
        // rather than dying with the Store.
        flushPendingUndo(interactor)
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
        val prior = state.value
        setMutator.findExercise(prior, action.performedExerciseUuid) ?: return
        updateState { latest -> setMutator.applyAddSet(latest, action.performedExerciseUuid) }
        pushUndo(
            interactor,
            PendingUndo(
                id = PendingUndoOps.nextUndoId(),
                message = resourceWrapper.getString(R.string.feature_live_workout_toast_set_added),
                restoreExercises = prior.exercises,
                restoreDrafts = prior.setDrafts,
                restoreOverrides = prior.rowCountOverrides,
            ),
        )
    }

    /**
     * `− подход` (§6.4). Always the last visible row; the mutator refuses below one row.
     * When the removed row was persisted (a done set), the DB row goes with it — same
     * optimistic shape as [processSetUncheck].
     */
    private fun processRemoveLastSet(action: Action.Click.OnRemoveLastSet) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val prior = state.value
        val exercise = setMutator.findExercise(prior, action.performedExerciseUuid) ?: return
        if (exercise.visibleSets.size <= 1) return
        val removedRow = exercise.visibleSets.last()
        var removedPerformedPosition: Int? = null
        updateState { latest ->
            val result = setMutator.applyRemoveLastSet(latest, action.performedExerciseUuid)
            removedPerformedPosition = result.removedPerformedPosition
            result.state
        }
        val position = removedPerformedPosition
        pushUndo(
            interactor,
            PendingUndo(
                id = PendingUndoOps.nextUndoId(),
                message = resourceWrapper.getString(R.string.feature_live_workout_toast_set_removed),
                restoreExercises = prior.exercises,
                restoreDrafts = prior.setDrafts,
                restoreOverrides = prior.rowCountOverrides,
                undoCompensation = position?.let {
                    PendingUndo.UndoCompensation.ReupsertSet(
                        performedExerciseUuid = action.performedExerciseUuid,
                        position = it,
                        weight = removedRow.weight,
                        reps = removedRow.reps,
                        type = removedRow.type,
                    )
                },
            ),
        )
        if (position == null) return
        launch(
            onError = { _ -> sendError(ErrorType.SetDeleteFailed) },
        ) {
            interactor.deleteSet(action.performedExerciseUuid, position)
        }
    }

    private fun processEditPlan(action: Action.Click.OnEditPlan) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        flushPendingUndo(interactor)
        updateState { it.copy(bottomSheetState = BottomSheetState.Hidden) }
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
        updateState {
            it.copy(
                dialogState = DialogState.ConfirmDialog.ResetSets(
                    title = resourceWrapper.getString(R.string.feature_live_workout_reset_title),
                    body = resourceWrapper.getString(R.string.feature_live_workout_reset_body),
                    confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_reset_confirm),
                    dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_reset_dismiss),
                    exerciseUuid = action.performedExerciseUuid,
                ),
            )
        }
    }

    /**
     * §6.1 / extraction C9: skip is a reversible in-place TOGGLE — no confirmation, no
     * snackbar, nothing destroyed. The dialog this used to open guarded a set wipe that no
     * longer happens; `Пропустить упражнение` ⇄ `Вернуть в сессию` is the whole flow.
     */
    private fun processSkipExerciseToggle(action: Action.Click.OnSkipExercise) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        val current = state.value
        val exercise = setMutator.findExercise(current, action.performedExerciseUuid) ?: return
        val skipped = exercise.status != ExerciseStatusUiModel.SKIPPED
        updateState { latest ->
            setMutator.applySkipToggle(latest, action.performedExerciseUuid, skipped)
        }
        launch(
            onError = { _ -> sendError(ErrorType.SkipFailed) },
        ) {
            interactor.setSkipped(action.performedExerciseUuid, skipped = skipped)
        }
    }

    private fun processSessionMenuClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheetState = BottomSheetState.SessionMenu) }
    }

    private fun processExerciseMenuClick(action: Action.Click.OnExerciseMenuClick) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(bottomSheetState = BottomSheetState.ExerciseMenu(action.performedExerciseUuid))
        }
    }

    private fun processShowDescription(action: Action.Click.OnShowDescription) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(
                bottomSheetState = BottomSheetState.ExerciseDescription(action.performedExerciseUuid),
            )
        }
    }

    /**
     * `Только на сегодня` (extraction §1.9): flips plan attachment live behind the open
     * sheet — no snapshot, no toast, exactly the mockup's `toggleOnce`. Non-ad-hoc sessions
     * only; the sheet never offers the row otherwise.
     */
    private fun processToggleOneOff(action: Action.Click.OnToggleOneOff) {
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        val current = state.value
        val trainingUuid = current.trainingUuid ?: return
        if (current.isAdhoc) return
        val exercise = setMutator.findExercise(current, action.performedExerciseUuid) ?: return
        val nextAttached = !exercise.isPlanAttached
        updateState { latest ->
            latest.copy(
                exercises = latest.exercises.map { row ->
                    if (row.performedExerciseUuid == action.performedExerciseUuid) {
                        row.copy(isPlanAttached = nextAttached)
                    } else {
                        row
                    }
                }.toImmutableList(),
            )
        }
        launch(
            onError = { _ ->
                sendError(ErrorType.PlanSaveFailed)
                updateState { latest ->
                    latest.copy(
                        exercises = latest.exercises.map { row ->
                            if (row.performedExerciseUuid == action.performedExerciseUuid) {
                                row.copy(isPlanAttached = !nextAttached)
                            } else {
                                row
                            }
                        }.toImmutableList(),
                    )
                }
            },
        ) {
            interactor.setPlanAttachment(
                trainingUuid = trainingUuid,
                exerciseUuid = exercise.exerciseUuid,
                attached = nextAttached,
                planSets = exercise.planSets.map { it.toPlanSetDomain() },
            )
        }
    }

    private fun processDeleteExerciseClick(action: Action.Click.OnDeleteExerciseClick) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(
                bottomSheetState = BottomSheetState.DeleteExerciseConfirm(action.performedExerciseUuid),
            )
        }
    }

    private fun processSheetDismiss() {
        updateState { it.copy(bottomSheetState = BottomSheetState.Hidden) }
    }

    private fun processUndoClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        undoPending(
            interactor = interactor,
            setMutator = setMutator,
            onError = { _ -> sendError(ErrorType.SetSaveFailed) },
        )
    }

    private fun processUndoTimeout(action: Action.Click.OnUndoTimeout) {
        if (state.value.pendingUndo?.id != action.id) return
        flushPendingUndo(interactor)
    }

    private fun processFinishClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        flushPendingUndo(interactor)
        val current = state.value
        if (current.isSessionEmpty) {
            // E1 lock — empty-finish branches into a confirm dialog. Discard CTA is enabled
            // only when the parent training is ad-hoc, so library training sessions get the
            // Continue-editing-only variant.
            updateState {
                it.copy(
                    dialogState = DialogState.EmptyFinish(
                        canDiscard = it.isAdhoc,
                        confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_empty_finish_discard),
                        dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_empty_finish_continue),
                    ),
                )
            }
            return
        }
        val stats = current.toFinishStats(resourceWrapper)
        updateState {
            it.copy(
                dialogState = stats,
            )
        }
    }

    private fun processCancelClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.Confirm))
        updateState {
            it.copy(
                dialogState = DialogState.ConfirmDialog.CancelSession(
                    title = resourceWrapper.getString(R.string.feature_live_workout_cancel_title),
                    body = resourceWrapper.getString(R.string.feature_live_workout_cancel_body),
                    confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_cancel_confirm),
                    dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_cancel_dismiss),
                ),
            )
        }
    }

    private fun processDeleteSessionMenuClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))

        updateState { state ->
            val sessionName = state.trainingNameLabel.takeIf { it.isNotBlank() }
                ?: resourceWrapper.getString(R.string.feature_live_workout_delete_session_unnamed)
            val progressLabel = resourceWrapper.getString(
                R.string.feature_live_workout_delete_session_progress_format,
                state.doneCount,
                state.totalCount,
                state.setsLogged,
            )
            state.copy(
                dialogState = DialogState.DeleteDialog(
                    sessionName = sessionName,
                    progressLabel = progressLabel,
                ),
            )
        }
    }

    /**
     * The only producer of manual disclosure intent (§7). It records what the user asked for
     * and lets `DisclosureAutomaton` decide what is expanded — this handler no longer writes
     * `expandedExerciseUuids` itself, so the transition table stays the single description of
     * the behaviour.
     *
     * A tap is a toggle against the *currently rendered* state, so tapping an auto-expanded
     * card collapses it (rule 2) and tapping a completed card opens it (rule 3), which is the
     * only way its add/remove-set buttons become reachable.
     */
    private fun processExerciseHeaderClick(action: Action.Click.OnExerciseHeaderClick) {
        updateState { current ->
            val exercise = setMutator.findExercise(current, action.performedExerciseUuid)
                ?: return@updateState current
            // A skipped card has nothing to disclose, and reversing the skip is a separate
            // action — so this must not count as a manual action and mute auto-collapse.
            if (exercise.status == ExerciseStatusUiModel.SKIPPED) return@updateState current

            val uuid = action.performedExerciseUuid
            val isExpandedNow = uuid in current.expandedExerciseUuids
            val manualExpanded = current.manualExpandedExerciseUuids.toMutableSet()
            val manualCollapsed = current.manualCollapsedExerciseUuids.toMutableSet()
            if (isExpandedNow) {
                manualExpanded.remove(uuid)
                manualCollapsed.add(uuid)
            } else {
                manualCollapsed.remove(uuid)
                manualExpanded.add(uuid)
            }
            // Status is a separate axis and keeps its existing rule: the automaton owns
            // expansion, `activeExerciseUuids` owns CURRENT-vs-PENDING. Collapsing a card that
            // has logged sets must not demote it out of CURRENT, which is why this is not a
            // plain toggle.
            val activeNext = current.activeExerciseUuids.toMutableSet()
            if (isExpandedNow && exercise.performedSets.isEmpty() && activeNext.isNotEmpty()) {
                activeNext.remove(uuid)
            } else {
                activeNext.add(uuid)
            }
            setMutator.recomputeStatuses(
                current.copy(
                    activeExerciseUuids = activeNext.toImmutableSet(),
                    manualExpandedExerciseUuids = manualExpanded.toImmutableSet(),
                    manualCollapsedExerciseUuids = manualCollapsed.toImmutableSet(),
                    hasManualDisclosureAction = true,
                ),
            )
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
