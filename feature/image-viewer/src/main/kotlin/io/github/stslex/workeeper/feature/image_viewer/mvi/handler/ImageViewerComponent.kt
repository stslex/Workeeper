// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen

abstract class ImageViewerComponent(
    data: Screen.ExerciseImage,
) : Component<Screen.ExerciseImage>(data) {

    companion object {

        fun create(
            navigator: Navigator,
            screen: Screen.ExerciseImage,
        ): ImageViewerComponent = NavigationHandler(navigator = navigator, data = screen)
    }
}
