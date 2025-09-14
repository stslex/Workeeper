package io.github.stslex.workeeper.feature.charts.ui

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator

interface ChartsComponent : Component {

    companion object {

        fun create(
            navigator: Navigator
        ): ChartsComponent = ChartsComponentImpl(
            navigator = navigator
        )

    }
}

