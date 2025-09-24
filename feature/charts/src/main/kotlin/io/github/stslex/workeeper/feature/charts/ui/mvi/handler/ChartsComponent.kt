package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator

interface ChartsComponent : Component {

    companion object {

        fun create(
            navigator: Navigator,
        ): ChartsComponent = NavigationHandler(
            navigator = navigator,
        )
    }
}
