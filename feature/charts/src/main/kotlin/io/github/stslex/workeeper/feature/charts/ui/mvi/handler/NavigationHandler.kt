package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action

internal class NavigationHandler(
    private val navigator: Navigator,
) : ChartsComponent, Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        // todo add navigation
    }
}
