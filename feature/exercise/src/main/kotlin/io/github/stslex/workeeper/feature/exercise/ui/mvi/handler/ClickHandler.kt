package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.data.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.data.model.ChangeExerciseDataModel
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyValid
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Factory
@Scope(ExerciseScope::class)
@Scoped
internal class ClickHandler(
    private val repository: ExerciseRepository
) : Handler<Action.Click, ExerciseHandlerStore> {

    override fun ExerciseHandlerStore.invoke(action: Action.Click) {
        sendEvent(ExerciseStore.Event.HapticClick)
        when (action) {
            Action.Click.Cancel -> consume(Action.Navigation.BackWithConfirmation)
            Action.Click.Save -> processSave()
            Action.Click.Delete -> processDelete()
            Action.Click.ConfirmedDelete -> processConfirmedDelete()
            Action.Click.PickDate -> processPickDate()
            Action.Click.CloseCalendar -> processCloseCalendar()
            Action.Click.CloseMenuVariants -> processCloseMenuVariants()
            is Action.Click.OnMenuItemClick -> processOnMenuItemClick(action)
            Action.Click.OpenMenuVariants -> processOpenMenuVariants()
        }
    }

    private fun ExerciseHandlerStore.processCloseMenuVariants() {
        updateState { it.copy(isMenuOpen = false) }
    }

    private fun ExerciseHandlerStore.processOpenMenuVariants() {
        updateState { it.copy(isMenuOpen = true) }
    }

    private fun ExerciseHandlerStore.processOnMenuItemClick(action: Action.Click.OnMenuItemClick) {
        val item = action.item
        updateState {
            it.copy(
                name = it.name.update(value = item.name),
                sets = it.sets.copy(value = item.sets.toString()),
                reps = it.reps.copy(value = item.reps.toString()),
                weight = it.weight.copy(value = item.weight.toString()),
                dateProperty = DateProperty.new(item.timestamp),
                isMenuOpen = false
            )
        }
    }

    private fun ExerciseHandlerStore.processCloseCalendar() {
        updateState { it.copy(isCalendarOpen = false) }
    }

    private fun ExerciseHandlerStore.processPickDate() {
        updateState { it.copy(isCalendarOpen = true) }
    }

    private fun ExerciseHandlerStore.processConfirmedDelete() {
        val currentUuid = state.value.uuid.takeIf {
            it.isNullOrBlank().not()
        } ?: return
        launch(
            onSuccess = {
                withContext(Dispatchers.Main.immediate) {
                    consume(Action.Navigation.Back)
                }
            }
        ) {
            repository.deleteItem(currentUuid)
        }
    }

    private fun ExerciseHandlerStore.processDelete() {
        sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DELETE))
    }

    private fun ExerciseHandlerStore.processSave() {

        val nameValid = state.value.name.validation()
        val setsValid = state.value.sets.validation()
        val repsValid = state.value.reps.validation()
        val weightValid = state.value.weight.validation()

        updateState {
            it.copy(
                name = it.name.copy(valid = nameValid),
                sets = it.sets.copy(valid = setsValid),
                reps = it.reps.copy(valid = repsValid),
                weight = it.weight.copy(valid = weightValid),
            )
        }

        if (
            nameValid != PropertyValid.VALID ||
            setsValid != PropertyValid.VALID ||
            repsValid != PropertyValid.VALID ||
            weightValid != PropertyValid.VALID
        ) {
            sendEvent(ExerciseStore.Event.InvalidParams)
            return
        }

        val item = ChangeExerciseDataModel(
            uuid = state.value.uuid,
            name = state.value.name.value,
            sets = state.value.sets.value.toInt(),
            reps = state.value.reps.value.toInt(),
            weight = state.value.weight.value.toDouble(),
            timestamp = state.value.dateProperty.timestamp,
        )

        launch(
            onSuccess = {
                withContext(Dispatchers.Main.immediate) {
                    consume(Action.Navigation.Back)
                }
            }
        ) {
            repository.saveItem(item)
        }
    }
}