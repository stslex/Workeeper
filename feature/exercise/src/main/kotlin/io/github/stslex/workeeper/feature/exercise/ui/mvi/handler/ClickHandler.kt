package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.data.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.data.model.ChangeExerciseDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyValid
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
        when (action) {
            Action.Click.Cancel -> consume(Action.Navigation.Back)
            Action.Click.Save -> processSave()
            Action.Click.Delete -> processDelete()
            Action.Click.ConfirmedDelete -> processConfirmedDelete()
        }
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
        sendEvent(ExerciseStore.Event.SnackbarDelete)
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
            timestamp = state.value.timestamp,
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