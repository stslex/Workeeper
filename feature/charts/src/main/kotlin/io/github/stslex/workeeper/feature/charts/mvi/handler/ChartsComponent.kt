package io.github.stslex.workeeper.feature.charts.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action

internal interface ChartsComponent : Component, Handler<Action.Navigation> {

    companion object {

        fun create(
            navigator: Navigator,
        ): ChartsComponent = NavigationHandler(
            navigator = navigator,
        )
    }
}
