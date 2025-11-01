package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingChangeMapper
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.uuid.Uuid

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: SingleTrainingInteractor,
    private val changeMap: TrainingChangeMapper,
    @MainImmediateDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    store: TrainingHandlerStore,
) : Handler<Action.Click>, TrainingHandlerStore by store {

    override fun invoke(action: Action.Click) {
        sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick))
        when (action) {
            Action.Click.Close -> processClickClose()
            Action.Click.CloseCalendarPicker -> processCloseCalendar()
            Action.Click.OpenCalendarPicker -> processOpenCalendar()
            Action.Click.Save -> processSave()
            Action.Click.DeleteDialogOpen -> processDeleteDialogOpen()
            Action.Click.CreateExercise -> processCreateExercise()
            is Action.Click.ExerciseClick -> processClickExercise(action)
            is Action.Click.Menu -> processMenuAction(action)
            is Action.Click.DialogDeleteTraining.Dismiss -> processDialogDeleteTrainingDismiss()
            is Action.Click.DialogDeleteTraining.Confirm -> processDialogDeleteTrainingConfirm()
        }
    }

    private fun processMenuAction(action: Action.Click.Menu) {
        when (action) {
            Action.Click.Menu.Close -> updateState { it.copyTraining(isMenuOpen = false) }
            Action.Click.Menu.Open -> updateState { it.copyTraining(isMenuOpen = true) }
            is Action.Click.Menu.Item -> updateState {
                it.copy(
                    training = action.item.itemModel.copy(isMenuOpen = false),
                )
            }
        }
    }

    private fun processCreateExercise() {
        val currentTrainingUuid = state.value.training.uuid
            .ifBlank { state.value.pendingForCreateUuid }
            .ifBlank {
                val uuid = Uuid.random().toString()
                updateState { it.copy(pendingForCreateUuid = uuid) }
                uuid
            }
        consume(
            Action.Navigation.CreateExercise(currentTrainingUuid),
        )
    }

    private fun processClickExercise(action: Action.Click.ExerciseClick) {
        val currentTrainingUuid = state.value.training.uuid
            .ifBlank { state.value.pendingForCreateUuid }
            .ifBlank {
                val uuid = Uuid.random().toString()
                updateState { it.copy(pendingForCreateUuid = uuid) }
                uuid
            }
        consume(
            Action.Navigation.OpenExercise(
                exerciseUuid = action.exerciseUuid,
                trainingUuid = currentTrainingUuid,
            ),
        )
    }

    private fun processDialogDeleteTrainingDismiss() {
        updateState {
            it.copy(dialogState = DialogState.Closed)
        }
    }

    private fun processDialogDeleteTrainingConfirm() {
        val uuid = state.value.training.uuid
            .ifBlank { state.value.pendingForCreateUuid }
            .ifBlank {
                // todo show error snackbar
                return
            }

        launch(
            onSuccess = {
                withContext(mainDispatcher) {
                    consume(Action.Navigation.PopBack)
                }
            },
        ) {
            interactor.removeTraining(uuid)
        }
    }

    private fun processDeleteDialogOpen() {
        updateState {
            it.copy(dialogState = DialogState.DeleteTraining)
        }
    }

    private fun processClickClose() {
        consume(Action.Navigation.PopBack)
    }

    private fun processCloseCalendar() {
        updateState { it.copy(dialogState = DialogState.Closed) }
    }

    private fun processOpenCalendar() {
        updateState { it.copy(dialogState = DialogState.Calendar) }
    }

    private fun processSave() {
        if (state.value.training.name.isValid.not()) {
            // todo show error snackbar
            return
        }
        val uuid = state.value.training.uuid
            .ifBlank { state.value.pendingForCreateUuid }
        launch(
            onSuccess = {
                withContext(mainDispatcher) {
                    consume(Action.Navigation.PopBack)
                }
            },
            onError = { logger.e(it) },
        ) {
            interactor.updateTraining(changeMap(state.value.training.copy(uuid = uuid)))
        }
    }
}
