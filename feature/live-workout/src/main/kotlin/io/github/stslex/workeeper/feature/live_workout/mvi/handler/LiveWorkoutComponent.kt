// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen

abstract class LiveWorkoutComponent(
    data: Screen.LiveWorkout,
) : Component<Screen.LiveWorkout>(data) {

    companion object {

        fun create(
            navigator: Navigator,
            screen: Screen.LiveWorkout,
        ): LiveWorkoutComponent = NavigationHandler(navigator = navigator, data = screen)
    }
}
