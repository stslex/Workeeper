package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.MainImmediateDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mappers.ExerciseUiMap
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyValid
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import kotlin.uuid.Uuid

@Scoped(binds = [ClickHandler::class])
@Scope(name = EXERCISE_SCOPE_NAME)
internal class ClickHandler(
    private val repository: ExerciseRepository,
    private val interactor: ExerciseInteractor,
    private val exerciseUiMap: ExerciseUiMap,
    @param:MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
    @Named(EXERCISE_SCOPE_NAME) store: ExerciseHandlerStore
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
                        uuid = Uuid.random().toString()
                    )
                )
            )
        }
    }

    private fun processDialogSetsOpen(action: Action.Click.DialogSets.OpenEdit) {
        updateState {
            it.copy(dialogState = DialogState.Sets(action.set))
        }
    }

    private fun processDialogSetsDismiss(action: Action.Click.DialogSets.DismissSetsDialog) {
        val isSaveSetValid: Boolean = action.set.weight.validation() == PropertyValid.VALID &&
                action.set.reps.validation() == PropertyValid.VALID
        if (isSaveSetValid.not()) {
            return
        }
        consume(Action.Click.DialogSets.SaveButton(action.set))
    }

    private fun processDialogSetsDelete(action: Action.Click.DialogSets.DeleteButton) {
        updateState {
            it.copy(
                sets = it.sets.filter { set -> set.uuid != action.uuid }.toImmutableList(),
                dialogState = DialogState.Closed
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
                dialogState = DialogState.Closed
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
                name = it.name.update(value = item.name),
                sets = item.sets,
                dateProperty = DateProperty.new(item.timestamp),
                isMenuOpen = false
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
            }
        ) {
            repository.deleteItem(currentUuid)
        }
    }

    private fun processDelete() {
        sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DELETE))
    }

    private fun processSave() {
        val nameValid = state.value.name.validation()

        var setsValid = true

        updateState {
            it.copy(
                name = it.name.copy(valid = nameValid),
                sets = it.sets.map { set ->
                    val repsValidation = set.reps.validation()
                    val weightValidation = set.weight.validation()

                    if (
                        repsValidation != PropertyValid.VALID ||
                        weightValidation != PropertyValid.VALID
                    ) {
                        setsValid = false
                    }

                    val reps = set.reps.copy(valid = repsValidation)
                    val weight = set.weight.copy(valid = weightValidation)
                    set.copy(
                        reps = reps,
                        weight = weight
                    )
                }.toImmutableList()
            )
        }

        if (nameValid != PropertyValid.VALID || setsValid.not()) {
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
            }
        ) {
            interactor.saveItem(exerciseUiMap(state.value))
        }
    }
}