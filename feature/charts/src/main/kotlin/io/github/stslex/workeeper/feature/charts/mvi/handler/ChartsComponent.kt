package io.github.stslex.workeeper.feature.charts.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.Charts
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action

internal interface ChartsComponent : Component<Charts>, Handler<Action.Navigation> {

    override val data: Charts
        get() = Charts

    companion object {

        fun create(
            navigator: Navigator,
        ): ChartsComponent = NavigationHandler(
            navigator = navigator,
        )
    }
}
