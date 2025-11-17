package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

interface ExerciseComponent : Component<Screen.Exercise>, Handler<Action.Navigation> {

    companion object {

        fun create(
            navigator: Navigator,
            screen: Screen.Exercise,
        ): ExerciseComponent = ExerciseComponentImpl(
            navigator = navigator,
            data = screen,
        )
    }
}
