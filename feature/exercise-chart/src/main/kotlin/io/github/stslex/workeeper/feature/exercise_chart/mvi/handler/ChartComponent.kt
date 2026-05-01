// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen

abstract class ChartComponent(
    data: Screen.ExerciseChart,
) : Component<Screen.ExerciseChart>(data) {

    companion object {

        fun create(navigator: Navigator, screen: Screen.ExerciseChart): ChartComponent =
            NavigationHandler(navigator = navigator, data = screen)
    }
}
