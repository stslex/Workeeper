package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [InputHandler::class])
@Scope(name = EXERCISE_SCOPE_NAME)
internal class InputHandler(
    @Named(EXERCISE_SCOPE_NAME) store: ExerciseHandlerStore
) : Handler<Action.Input>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.PropertyName -> processProperty(action)
            is Action.Input.Time -> updateState {
                it.copy(dateProperty = DateProperty.new(action.timestamp))
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
            this.set.reps.update(action.value)
        } else {
            this.set.reps
        }

        fun DialogState.Sets.getWeight() = if (action is Action.Input.DialogSets.Weight) {
            this.set.weight.update(action.value)
        } else {
            this.set.weight
        }

        updateState { state ->
            val dialogState = state.dialogState.let { dialogState ->
                if (dialogState is DialogState.Sets) {
                    dialogState.copy(
                        set = dialogState.set.copy(
                            reps = dialogState.getReps(),
                            weight = dialogState.getWeight()
                        )
                    )
                } else {
                    dialogState
                }
            }
            state.copy(
                dialogState = dialogState
            )
        }
    }
}