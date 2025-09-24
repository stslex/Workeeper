package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator

internal interface AllTrainingsComponent : Component {

    companion object {

        fun create(
            navigator: Navigator,
        ): AllTrainingsComponent = NavigationHandler(
            navigator = navigator,
        )
    }
}
