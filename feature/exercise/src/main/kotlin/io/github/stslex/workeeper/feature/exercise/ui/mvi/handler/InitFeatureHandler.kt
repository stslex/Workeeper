package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise.Data
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Factory
@Scope(ExerciseScope::class)
@Scoped
internal class InitFeatureHandler : Handler<Action.InitDialog, ExerciseHandlerStore> {

    override fun ExerciseHandlerStore.invoke(action: Action.InitDialog) {

        when (val data = action.data) {
            null -> updateState {
                it.copy(
                    initialHash = it.calculateEqualsHash()
                )
            }
            else -> updateState { state ->
                state.setState(data)
            }
        }
    }

    private fun State.setState(data: Data): State = copy(
        uuid = data.uuid,
        name = name.update(data.name),
        sets = sets.update(data.sets.toString()),
        reps = reps.update(data.reps.toString()),
        weight = weight.update(data.weight.toString()),
        timestamp = data.timestamp,
    ).apply {
        copy(initialHash = calculateEqualsHash())
    }

}