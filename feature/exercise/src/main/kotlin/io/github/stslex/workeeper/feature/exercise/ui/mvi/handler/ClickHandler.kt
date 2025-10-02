package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder.Companion.update
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mappers.ExerciseUiMap
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.uuid.Uuid

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: ExerciseInteractor,
    private val exerciseUiMap: ExerciseUiMap,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
    store: ExerciseHandlerStore,
) : Handler<Action.Click>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Click) {
        sendEvent(ExerciseStore.Event.HapticClick)
        when (action) {
            Action.Click.Cancel -> consume(Action.NavigationMiddleware.BackWithConfirmation)
            Action.Click.Save -> processSave()
            Action.Click.Delete -> processDelete()
            Action.Click.ConfirmedDelete -> processConfirmedDelete()
            Action.Click.PickDate -> processPickDate()
            Action.Click.CloseDialog -> processCloseDialog()
            Action.Click.CloseMenuVariants -> processCloseMenuVariants()
            is Action.Click.OnMenuItemClick -> processOnMenuItemClick(action)
            Action.Click.OpenMenuVariants -> processOpenMenuVariants()
            is Action.Click.DialogSets -> processDialogSetsActions(action)
        }
    }

    private fun processDialogSetsActions(action: Action.Click.DialogSets) {
        when (action) {
            Action.Click.DialogSets.CancelButton -> processCloseDialog()
            is Action.Click.DialogSets.DeleteButton -> processDialogSetsDelete(action)
            is Action.Click.DialogSets.DismissSetsDialog -> processDialogSetsDismiss(action)
            is Action.Click.DialogSets.SaveButton -> processDialogSetsSave(action)
            Action.Click.DialogSets.OpenCreate -> processDialogSetsCreate()
            is Action.Click.DialogSets.OpenEdit -> processDialogSetsOpen(action)
        }
    }

    private fun processDialogSetsCreate() {
        updateState {
            it.copy(
                dialogState = DialogState.Sets(
                    SetsUiModel.EMPTY.copy(
                        uuid = Uuid.random().toString(),
                    ),
                ),
            )
        }
    }

    private fun processDialogSetsOpen(action: Action.Click.DialogSets.OpenEdit) {
        updateState {
            it.copy(dialogState = DialogState.Sets(action.set))
        }
    }

    private fun processDialogSetsDismiss(action: Action.Click.DialogSets.DismissSetsDialog) {
        val isSaveSetValid: Boolean = action.set.weight.isValid && action.set.reps.isValid
        if (isSaveSetValid.not()) {
            return
        }
        consume(Action.Click.DialogSets.SaveButton(action.set))
    }

    private fun processDialogSetsDelete(action: Action.Click.DialogSets.DeleteButton) {
        updateState {
            it.copy(
                sets = it.sets.filter { set -> set.uuid != action.uuid }.toImmutableList(),
                dialogState = DialogState.Closed,
            )
        }
    }

    private fun processDialogSetsSave(action: Action.Click.DialogSets.SaveButton) {
        updateState {
            var isFound = false
            val newSets = it.sets.map { set ->
                if (set.uuid == action.set.uuid) {
                    isFound = true
                    action.set
                } else {
                    set
                }
            }
            val resultSet = if (isFound) newSets else (newSets + action.set)
            it.copy(
                sets = resultSet.toImmutableList(),
                dialogState = DialogState.Closed,
            )
        }
    }

    private fun processCloseMenuVariants() {
        updateState { it.copy(isMenuOpen = false) }
    }

    private fun processOpenMenuVariants() {
        updateState { it.copy(isMenuOpen = true) }
    }

    private fun processOnMenuItemClick(action: Action.Click.OnMenuItemClick) {
        val item = action.item
        updateState {
            it.copy(
                name = it.name.update(item.itemModel.name),
                sets = item.itemModel.sets,
                dateProperty = it.dateProperty.update(item.itemModel.timestamp),
                isMenuOpen = false,
            )
        }
    }

    private fun processCloseDialog() {
        updateState { it.copy(dialogState = DialogState.Closed) }
    }

    private fun processPickDate() {
        updateState { it.copy(dialogState = DialogState.Calendar) }
    }

    private fun processConfirmedDelete() {
        val currentUuid = state.value.uuid.takeIf {
            it.isNullOrBlank().not()
        } ?: return
        launch(
            onSuccess = {
                withContext(mainDispatcher) {
                    consume(Action.NavigationMiddleware.Back)
                }
            },
        ) {
            interactor.deleteItem(currentUuid)
        }
    }

    private fun processDelete() {
        sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DELETE))
    }

    private fun processSave() {
        val setsValid = state.value.sets.all { it.reps.isValid && it.weight.isValid }

        if (state.value.name.isValid.not() || setsValid.not()) {
            sendEvent(ExerciseStore.Event.InvalidParams)
            return
        }

        launch(
            onSuccess = {
                withContext(mainDispatcher) {
                    consume(Action.NavigationMiddleware.Back)
                }
            },
            onError = {
                logger.e(it)
            },
        ) {
            interactor.saveItem(exerciseUiMap(state.value))
        }
    }
}
