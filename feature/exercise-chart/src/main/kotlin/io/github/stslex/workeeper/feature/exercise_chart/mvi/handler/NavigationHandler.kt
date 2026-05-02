// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action

@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
    data: Screen.ExerciseChart,
) : ChartComponent(data), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.OpenPastSession ->
                navigator.navTo(Screen.PastSession(sessionUuid = action.sessionUuid))
            Action.Navigation.OpenHome ->
                navigator.navTo(Screen.BottomBar.Home)
            Action.Navigation.PopBack ->
                navigator.popBack()
        }
    }
}
