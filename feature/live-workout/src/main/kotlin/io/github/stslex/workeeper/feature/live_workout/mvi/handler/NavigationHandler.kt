// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action

@SingleIn(LiveWorkoutScope::class)
internal class NavigationHandler @Inject constructor(
    private val navigator: Navigator,
) : Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
            is Action.Navigation.OpenPastSession -> navigator.replaceTo(
                Screen.PastSession(sessionUuid = action.sessionUuid),
            )

            is Action.Navigation.OpenPlanEditor ->
                navigator.navTo(
                    Screen.PlanEditor.Existing(
                        performedExerciseUuid = action.performedExerciseUuid,
                        exerciseUuid = action.exerciseUuid,
                        trainingUuid = action.trainingUuid,
                    ),
                )
        }
    }
}
