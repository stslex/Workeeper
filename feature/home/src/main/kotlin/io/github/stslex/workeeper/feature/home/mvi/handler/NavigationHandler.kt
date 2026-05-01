// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action

@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
) : HomeComponent(), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.OpenLiveWorkoutResume -> navigator.navTo(
                Screen.LiveWorkout(
                    sessionUuid = action.sessionUuid,
                    trainingUuid = null,
                ),
            )

            is Action.Navigation.OpenLiveWorkoutFresh -> navigator.navTo(
                Screen.LiveWorkout(
                    sessionUuid = null,
                    trainingUuid = action.trainingUuid,
                ),
            )

            is Action.Navigation.OpenPastSession -> navigator.navTo(
                Screen.PastSession(sessionUuid = action.sessionUuid),
            )

            Action.Navigation.OpenSettings -> navigator.navTo(Screen.Settings)
            Action.Navigation.OpenCharts -> navigator.navTo(Screen.ExerciseChart(exerciseUuid = null))
            Action.Navigation.OpenAllTrainings -> navigator.navTo(Screen.BottomBar.AllTrainings)
        }
    }
}
