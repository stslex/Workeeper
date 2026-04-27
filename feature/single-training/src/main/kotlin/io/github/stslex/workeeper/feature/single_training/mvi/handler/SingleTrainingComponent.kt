// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen

abstract class SingleTrainingComponent(
    data: Screen.Training,
) : Component<Screen.Training>(data) {

    companion object {

        fun create(
            navigator: Navigator,
            screen: Screen.Training,
        ): SingleTrainingComponent = NavigationHandler(navigator = navigator, data = screen)
    }
}
