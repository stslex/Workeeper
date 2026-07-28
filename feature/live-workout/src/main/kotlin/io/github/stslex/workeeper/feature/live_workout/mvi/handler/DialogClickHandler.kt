package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.PendingUndoOps.flushPendingUndo
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.PendingUndoOps.pushUndo
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.PendingUndo
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

@SingleIn(LiveWorkoutScope::class)
internal class DialogClickHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
    private val pickerHandler: ExercisePickerHandler,
    private val setMutator: LiveSetMutator,
    store: LiveWorkoutHandlerStore,
) : Handler<Action.DialogClick>, LiveWorkoutHandlerStore by store {

    override fun invoke(action: Action.DialogClick) {
        when (action) {
            Action.DialogClick.OnDeleteSessionConfirm -> processDeleteSessionConfirm()
            Action.DialogClick.OnCancelSessionConfirm -> processCancelConfirm()
            Action.DialogClick.OnFinishConfirm -> processFinishConfirm()
            is Action.DialogClick.OnFinishNameChange -> processFinishNameChange(action)
            is Action.DialogClick.OnResetSetsConfirm -> processResetSetsConfirm(action)
            is Action.DialogClick.PickerAction -> pickerHandler.invoke(action.action)
            is Action.DialogClick.OnDeleteExerciseConfirm -> processDeleteExerciseConfirm(action)
            Action.DialogClick.OnDeleteExerciseKeep -> processDeleteExerciseKeep()
            Action.DialogClick.OnEmptyFinishDiscard -> processEmptyFinishDiscard()
            Action.DialogClick.OnEmptyFinishContinue -> processEmptyFinishContinue()
            Action.DialogClick.OnDeleteSessionDismiss,
            Action.DialogClick.OnCancelSessionDismiss,
            Action.DialogClick.OnResetSetsDismiss,
            Action.DialogClick.OnFinishDismiss,
            -> processCloseDialog()
        }
    }

    /**
     * `sh-del`'s confirm (§6.1 "deleted: excluded, plan cleaned, 5-second undo toast").
     * The removal is SOFT here: the exercise leaves State, the toast opens the 5s window,
     * and the transactional hard delete rides `PendingUndo.deferredCommit` — committed on
     * timeout/replacement/navigation, restored wholesale on `Отменить`.
     */
    private fun processDeleteExerciseConfirm(action: Action.DialogClick.OnDeleteExerciseConfirm) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        val prior = state.value
        val exercise = setMutator.findExercise(prior, action.performedExerciseUuid) ?: return
        val removeFromPlan = exercise.isPlanAttached && !prior.isAdhoc
        updateState { latest ->
            setMutator.recomputeStatuses(
                latest.copy(
                    exercises = latest.exercises
                        .filterNot { it.performedExerciseUuid == action.performedExerciseUuid }
                        .toImmutableList(),
                    setDrafts = latest.setDrafts
                        .filterKeys { it.performedExerciseUuid != action.performedExerciseUuid }
                        .toImmutableMap(),
                    rowCountOverrides = (latest.rowCountOverrides - action.performedExerciseUuid)
                        .toImmutableMap(),
                    bottomSheetState = BottomSheetState.Hidden,
                ),
            )
        }
        pushUndo(
            interactor,
            PendingUndo(
                id = PendingUndoOps.nextUndoId(),
                message = resourceWrapper.getString(
                    R.string.feature_live_workout_toast_exercise_removed,
                    exercise.exerciseName.truncateForToast(),
                ),
                restoreExercises = prior.exercises,
                restoreDrafts = prior.setDrafts,
                restoreOverrides = prior.rowCountOverrides,
                deferredCommit = PendingUndo.DeferredCommit(
                    performedExerciseUuid = action.performedExerciseUuid,
                    exerciseUuid = exercise.exerciseUuid,
                    removeFromPlan = removeFromPlan,
                ),
            ),
        )
    }

    private fun processDeleteExerciseKeep() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheetState = BottomSheetState.Hidden) }
    }

    private fun processEmptyFinishContinue() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(dialogState = DialogState.Hidden) }
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
            updateState { it.copy(dialogState = DialogState.Hidden) }
            return
        }
        updateState {
            it.copy(
                dialogState = DialogState.Hidden,
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

    private fun processResetSetsConfirm(action: Action.DialogClick.OnResetSetsConfirm) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { latest -> setMutator.applyResetSets(latest, action.performedExerciseUuid) }
        launch(
            onError = { _ -> sendError(ErrorType.ResetFailed) },
        ) {
            interactor.resetExerciseSets(action.performedExerciseUuid)
        }
    }

    private fun processFinishConfirm() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.Confirm))
        val current = state.value
        val sessionUuid = current.sessionUuid ?: return
        val stats = current.dialogState as? DialogState.FinishSession
        val requiredName = stats?.nameDraft
            ?.trim()
            ?.takeIf { stats.requiresName }
        if (stats?.requiresName == true && requiredName.isNullOrBlank()) {
            val requiredError = resourceWrapper.getString(
                R.string.feature_live_workout_finish_name_required,
            )
            updateState { latest ->
                latest.copy(
                    dialogState = stats.copy(
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

    private fun processFinishNameChange(action: Action.DialogClick.OnFinishNameChange) {
        val trimmed = action.text.trim()
        val requiredError = resourceWrapper
            .getString(R.string.feature_live_workout_finish_name_required)
            .takeIf { trimmed.isBlank() }
        updateState { latest ->
            val current = latest.dialogState as? DialogState.FinishSession
                ?: return@updateState latest
            latest.copy(
                dialogState = current.copy(
                    nameDraft = action.text,
                    nameError = requiredError,
                    confirmEnabled = trimmed.isNotBlank(),
                ),
            )
        }
    }

    private fun processCancelConfirm() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        flushPendingUndo(interactor)
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

    private fun processDeleteSessionConfirm() {
        flushPendingUndo(interactor)
        val sessionUuid = state.value.sessionUuid ?: run {
            updateState { it.copy(dialogState = DialogState.Hidden) }
            return
        }
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { it.copy(dialogState = DialogState.Hidden) }
        launch(
            onSuccess = { consumeOnMain(Action.Navigation.Back) },
            onError = { _ -> sendError(ErrorType.CancelFailed) },
        ) {
            interactor.cancelSession(sessionUuid)
        }
    }

    private fun processCloseDialog() {
        updateState { it.copy(dialogState = DialogState.Hidden) }
    }

    private fun sendError(type: ErrorType) {
        sendEvent(
            Event.ShowError(
                message = resourceWrapper.getString(type.msgRes),
            ),
        )
    }
}
