package io.github.stslex.workeeper.feature.charts.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action

@Suppress("unused")
internal class NavigationHandler(
    private val navigator: Navigator,
) : ChartsComponent(), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        // todo add navigation
    }
}
