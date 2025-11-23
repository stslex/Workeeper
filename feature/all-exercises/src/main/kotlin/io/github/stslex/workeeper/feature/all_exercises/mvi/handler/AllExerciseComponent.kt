package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllExercises

abstract class AllExerciseComponent : Component<AllExercises>(AllExercises) {

    companion object {

        fun create(
            navigator: Navigator,
        ): AllExerciseComponent = NavigationHandler(
            navigator = navigator,
        )
    }
}
