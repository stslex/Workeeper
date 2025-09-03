package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Factory
@Scope(ExerciseScope::class)
@Scoped
class InputHandler : Handler<Action.Input, ExerciseHandlerStore> {

    override fun ExerciseHandlerStore.invoke(action: Action.Input) {
        when (action) {
            is Action.Input.Property -> processProperty(action)
            is Action.Input.Time -> updateState { it.copy(timestamp = action.timestamp) }
        }
    }

    private fun ExerciseHandlerStore.processProperty(action: Action.Input.Property) {
        when (action.type) {
            PropertyType.NAME -> updateState {
                it.copy(name = it.name.update(action.value))
            }
            PropertyType.SETS -> updateState {
                it.copy(sets = it.sets.update(value = action.value))
            }
            PropertyType.REPS -> updateState {
                it.copy(reps = it.reps.update(value = action.value))
            }
            PropertyType.WEIGHT -> updateState {
                it.copy(weight = it.weight.update(value = action.value.replace(",", ".")))
            }
        }
    }
}