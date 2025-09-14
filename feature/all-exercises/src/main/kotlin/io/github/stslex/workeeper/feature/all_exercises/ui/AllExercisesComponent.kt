package io.github.stslex.workeeper.feature.all_exercises.ui

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator

interface AllExercisesComponent : Component {

    companion object {

        fun create(
            navigator: Navigator
        ): AllExercisesComponent = AllExercisesComponentImpl(
            navigator = navigator
        )

    }
}

