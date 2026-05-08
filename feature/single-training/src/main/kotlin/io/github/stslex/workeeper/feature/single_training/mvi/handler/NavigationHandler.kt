// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import javax.inject.Inject

@ViewModelScoped
internal class NavigationHandler @Inject constructor(
    private val navigator: Navigator,
) : Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
            is Action.Navigation.OpenExerciseDetail ->
                navigator.navTo(Screen.Exercise(uuid = action.uuid))

            is Action.Navigation.OpenSession ->
                navigator.navTo(Screen.PastSession(sessionUuid = action.sessionUuid))

            is Action.Navigation.OpenLiveWorkout -> navigator.navTo(
                Screen.LiveWorkout(
                    sessionUuid = action.sessionUuid.takeIf { it.isNotBlank() },
                    trainingUuid = action.trainingUuid,
                ),
            )

            is Action.Navigation.OpenPlanEditor -> navigator.navTo(
                Screen.PlanEditor.Existing(
                    performedExerciseUuid = null,
                    exerciseUuid = action.exerciseUuid,
                    trainingUuid = action.trainingUuid,
                ),
            )
        }
    }
}
