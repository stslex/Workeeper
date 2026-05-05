// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
    data: Screen.Exercise,
) : ExerciseComponent(data), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
            is Action.Navigation.OpenSession -> navigator.navTo(
                Screen.PastSession(
                    sessionUuid = action.sessionUuid,
                ),
            )

            is Action.Navigation.OpenLiveWorkout -> navigator.navTo(
                Screen.LiveWorkout(
                    sessionUuid = action.sessionUuid,
                    trainingUuid = null,
                ),
            )

            is Action.Navigation.OpenImageViewer -> navigator.navTo(
                Screen.ExerciseImage(action.model),
            )

            is Action.Navigation.OpenChart -> navigator.navTo(
                Screen.ExerciseChart(exerciseUuid = action.exerciseUuid),
            )

            is Action.Navigation.OpenPlanEditor -> navigator.navTo(
                Screen.PlanEditor(
                    performedExerciseUuid = null,
                    exerciseUuid = action.exerciseUuid,
                    trainingUuid = null,
                ),
            )
        }
    }
}
