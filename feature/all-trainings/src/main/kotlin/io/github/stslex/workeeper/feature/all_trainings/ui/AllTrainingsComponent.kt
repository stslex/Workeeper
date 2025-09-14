package io.github.stslex.workeeper.feature.all_trainings.ui

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator

interface AllTrainingsComponent : Component {

    companion object {

        fun create(
            navigator: Navigator
        ): AllTrainingsComponent = AllTrainingsComponentImpl(
            navigator = navigator
        )

    }
}

