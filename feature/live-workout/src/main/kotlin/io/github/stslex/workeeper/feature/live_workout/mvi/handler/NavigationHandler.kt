// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.NavigationHandlerFactory
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
    store: LiveWorkoutHandlerStore,
) : Handler<Action.Navigation>, LiveWorkoutHandlerStore by store {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Init -> processInitAction()
            Action.Navigation.Back -> navigator.popBack()
            is Action.Navigation.OpenPastSession -> navigator.replaceTo(
                Screen.PastSession(sessionUuid = action.sessionUuid),
            )

            is Action.Navigation.OpenPlanEditor ->
                navigator.navTo(
                    Screen.PlanEditor(
                        performedExerciseUuid = action.performedExerciseUuid,
                        exerciseUuid = action.exerciseUuid,
                        trainingUuid = action.trainingUuid,
                    ),
                )
        }
    }

    private fun processInitAction() {
        navigator
            .subscribeToStackAttr(Screen.PlanEditor.planEditorSavedAttr)
            ?.filterNotNull()
            ?.distinctUntilChanged()
            ?.launch { saved ->
                if (saved) {
                    consume(Action.Common.Reload)
                    navigator.setCurrentStack(Screen.PlanEditor.planEditorSavedAttr)
                }
            }
    }

    @ViewModelScoped
    class Factory @Inject constructor(
        store: LiveWorkoutHandlerStore,
    ) : NavigationHandlerFactory<NavigationHandler>(
        creator = { navigator -> NavigationHandler(navigator, store) },
    )
}
