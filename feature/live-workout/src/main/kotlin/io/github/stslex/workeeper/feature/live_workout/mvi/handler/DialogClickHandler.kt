package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import javax.inject.Inject

@ViewModelScoped
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
            is Action.DialogClick.OnSkipExerciseConfirm -> processSkipExerciseConfirm(action)
            is Action.DialogClick.PickerAction -> pickerHandler.invoke(action.action)
            Action.DialogClick.OnEmptyFinishDiscard -> processEmptyFinishDiscard()
            Action.DialogClick.OnEmptyFinishContinue -> processEmptyFinishContinue()
            Action.DialogClick.OnDeleteSessionDismiss,
            Action.DialogClick.OnCancelSessionDismiss,
            Action.DialogClick.OnResetSetsDismiss,
            Action.DialogClick.OnFinishDismiss,
            Action.DialogClick.OnSkipExerciseDismiss,
            -> processCloseDialog()
        }
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

    private fun processSkipExerciseConfirm(action: Action.DialogClick.OnSkipExerciseConfirm) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { latest -> setMutator.applySkip(latest, action.performedExerciseUuid) }
        launch(
            onError = { _ -> sendError(ErrorType.SkipFailed) },
        ) {
            interactor.setSkipped(action.performedExerciseUuid, skipped = true)
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
