package io.github.stslex.workeeper.feature.charts.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.Charts

abstract class ChartsComponent : Component<Charts>(Charts) {

    companion object {

        fun create(
            navigator: Navigator,
        ): ChartsComponent = NavigationHandler(
            navigator = navigator,
        )
    }
}
