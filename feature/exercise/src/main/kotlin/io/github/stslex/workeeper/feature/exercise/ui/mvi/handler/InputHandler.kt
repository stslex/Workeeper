package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import kotlinx.collections.immutable.toImmutableList
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

            is Action.Input.Sets -> processSets(action)
        }
    }

    private fun processProperty(action: Action.Input.PropertyName) {
        updateState {
            it.copy(name = it.name.update(action.value))
        }
    }

    private fun processSets(action: Action.Input.Sets) {
        updateState {
            var isFound = false
            val newSets = it.sets.map { set ->
                if (set == action.set) {
                    isFound = true
                    action.set
                } else {
                    set
                }
            }
            val resultSet = if (isFound) newSets else (newSets + action.set)
            it.copy(sets = resultSet.toImmutableList())
        }
    }
}