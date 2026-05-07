// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen

class SingleTrainingComponent(
    internal val navigator: Navigator,
    data: Screen.Training,
) : Component<Screen.Training>(data) {

    companion object {

        fun create(
            navigator: Navigator,
            screen: Screen.Training,
        ): SingleTrainingComponent = SingleTrainingComponent(
            navigator = navigator,
            data = screen,
        )
    }
}
