package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen

abstract class ExerciseComponent(
    data: Screen.Exercise,
) : Component<Screen.Exercise>(data) {

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
