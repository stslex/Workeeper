package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyType
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
            is Action.Input.Property -> processProperty(action)
            is Action.Input.Time -> updateState {
                it.copy(dateProperty = DateProperty.new(action.timestamp))
            }
        }
    }

    private fun processProperty(action: Action.Input.Property) {
//        when (action.type) {
//            PropertyType.NAME -> {
//                updateState {
//                    it.copy(name = it.name.update(action.value))
//                }
//                consume(Action.Common.SearchTitle)
//            }
//
//            PropertyType.SETS -> updateState {
//                it.copy(sets = it.sets.update(value = action.value))
//            }
//
//            PropertyType.REPS -> updateState {
//                it.copy(reps = it.reps.update(value = action.value))
//            }
//
//            PropertyType.WEIGHT -> updateState {
//                it.copy(weight = it.weight.update(value = action.value.replace(",", ".")))
//            }
//        }
    }
}