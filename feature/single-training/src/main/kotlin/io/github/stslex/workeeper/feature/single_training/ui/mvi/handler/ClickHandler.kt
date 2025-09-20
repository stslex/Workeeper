package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.MainImmediateDispatcher
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingChangeMapper
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [ClickHandler::class])
@Scope(name = TRAINING_SCOPE_NAME)
internal class ClickHandler(
    private val interactor: SingleTrainingInteractor,
    private val changeMap: TrainingChangeMapper,
    @param:MainImmediateDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    @Named(TRAINING_SCOPE_NAME) store: TrainingHandlerStore
) : Handler<Action.Click>, TrainingHandlerStore by store {

    override fun invoke(action: Action.Click) {
        sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.ContextClick))
        when (action) {
            Action.Click.Close -> processClickClose()
            Action.Click.CloseCalendarPicker -> processCloseCalendar()
            Action.Click.OpenCalendarPicker -> processOpenCalendar()
            Action.Click.Save -> processSave()
            Action.Click.Delete -> processDelete()
            Action.Click.CreateExercise -> processCreateExercise()
            is Action.Click.ExerciseClick -> processClickExercise(action)
        }
    }

    private fun processCreateExercise() {
        consume(Action.Navigation.CreateExercise)
    }

    private fun processClickExercise(action: Action.Click.ExerciseClick) {
        consume(Action.Navigation.OpenExercise(action.exerciseUuid))
    }

    private fun processDelete() {
        val uuid = state.value.training.uuid.ifBlank {
            // todo show error snackbar
            return
        }
        launch(
            onSuccess = {
                withContext(mainDispatcher) {
                    consume(Action.Navigation.PopBack)
                }
            }
        ) {
            interactor.removeTraining(uuid)
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
        if (state.value.training.name.isBlank()) {
            // todo show error snackbar
            return
        }
        launch(
            onSuccess = {
                withContext(mainDispatcher) {
                    consume(Action.Navigation.PopBack)
                }
            },
            onError = { logger.e(it) }
        ) {
            interactor.updateTraining(changeMap(state.value.training))
        }
    }
}