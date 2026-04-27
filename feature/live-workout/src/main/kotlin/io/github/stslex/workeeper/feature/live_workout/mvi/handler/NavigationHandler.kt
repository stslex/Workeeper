// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action

@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
    data: Screen.LiveWorkout,
) : LiveWorkoutComponent(data), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
            Action.Navigation.BackToHome -> {
                // Pop until the user lands back on the bottom-tab Home — the same
                // popBack works because Live workout sits one detail deep from Home.
                navigator.popBack()
            }
        }
    }
}
