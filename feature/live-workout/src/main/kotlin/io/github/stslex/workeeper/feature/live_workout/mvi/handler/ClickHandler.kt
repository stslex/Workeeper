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
        // Dismissal order: picker → empty-finish dialog → name edit → default back.
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
            // Submit on back so a back-dismissed keyboard still persists the edit.
            processTrainingNameSubmit(Action.Click.OnTrainingNameSubmit(current.trainingNameDraft))
            return
        }
        // GUARD: leaving closes the undo window; the deferred delete must commit here.
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
        // Blank submit must not write "" — that would clobber a previously-saved name.
        if (trimmed.isBlank()) {
            updateState { latest -> latest.copy(isTrainingNameEditing = false) }
            return
        }
        // Snapshot pre-edit values so a write failure can revert the optimistic update.
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
        // Revert path; every blur routes through Submit, so only an explicit Cancel hits it.
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
            // A checked set persists type changes at once so the saved set matches the UI.
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

    /** `− подход`: always the last visible row; a persisted row's DB row goes with it. */
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
        // The plan editor is a full-screen route; its result arrives as PlanResultReceived.
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

    /** Skip is a reversible in-place toggle — no confirmation, nothing destroyed. */
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

    /** `Только на сегодня`: flips plan attachment behind the open sheet; non-ad-hoc only. */
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
            // The Discard CTA is enabled only when the parent training is ad-hoc.
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

    /** A header tap flips this card's membership in the open set; nothing else happens. */
    private fun processExerciseHeaderClick(action: Action.Click.OnExerciseHeaderClick) {
        updateState { current ->
            val uuid = action.performedExerciseUuid
            setMutator.findExercise(current, uuid) ?: return@updateState current
            current.copy(
                expandedExerciseUuids = if (uuid in current.expandedExerciseUuids) {
                    current.expandedExerciseUuids - uuid
                } else {
                    current.expandedExerciseUuids + uuid
                }.toImmutableSet(),
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
