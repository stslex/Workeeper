package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import com.arkivanov.decompose.ComponentContext
import io.github.stslex.workeeper.core.ui.navigation.Config.Exercise.Data
import io.github.stslex.workeeper.core.ui.navigation.Router
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseHandlerStore

internal class ExerciseComponentImpl(
    private val router: Router,
    override val data: Data
) : ExerciseComponent, ComponentContext by router {

    override fun ExerciseHandlerStore.invoke(
        action: Action.Navigation
    ) {
        when (action) {
            Action.Navigation.Back -> router.popBack()
        }
    }
}