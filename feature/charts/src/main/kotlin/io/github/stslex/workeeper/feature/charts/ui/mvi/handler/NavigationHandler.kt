package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action

internal class NavigationHandler(
    private val navigator: Navigator,
) : ChartsComponent {

    override fun invoke(action: Action.Navigation) {
        // todo add navigation
    }
}
