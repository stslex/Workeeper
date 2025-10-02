package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder.Companion.update
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    store: ExerciseHandlerStore,
) : Handler<Action.Input>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.PropertyName -> processProperty(action)
            is Action.Input.Time -> updateState {
                it.copy(dateProperty = it.dateProperty.update(action.timestamp))
            }

            is Action.Input.DialogSets -> processSets(action)
        }
    }

    private fun processProperty(action: Action.Input.PropertyName) {
        updateState {
            it.copy(name = it.name.update(action.value))
        }
    }

    private fun processSets(action: Action.Input.DialogSets) {

        fun DialogState.Sets.getReps() = if (action is Action.Input.DialogSets.Reps) {
            action.value.toIntOrNull()
                ?.let { set.reps.update(it) }
                ?: set.reps
        } else {
            set.reps
        }

        fun DialogState.Sets.getWeight() = if (action is Action.Input.DialogSets.Weight) {
            action.value.toDoubleOrNull()
                ?.let { set.weight.update(it) }
                ?: set.weight
        } else {
            set.weight
        }

        updateState { state ->
            val dialogState = state.dialogState.let { dialogState ->
                if (dialogState is DialogState.Sets) {
                    dialogState.copy(
                        set = dialogState.set.copy(
                            reps = dialogState.getReps(),
                            weight = dialogState.getWeight(),
                        ),
                    )
                } else {
                    dialogState
                }
            }
            state.copy(
                dialogState = dialogState,
            )
        }
    }
}
