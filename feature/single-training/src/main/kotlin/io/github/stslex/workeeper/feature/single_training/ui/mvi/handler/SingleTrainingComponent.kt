package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.Training

abstract class SingleTrainingComponent(data: Training) : Component<Training>(data) {

    companion object {

        fun create(
            navigator: Navigator,
            screen: Training,
        ): SingleTrainingComponent = NavigationHandler(
            navigator = navigator,
            data = screen,
        )
    }
}
