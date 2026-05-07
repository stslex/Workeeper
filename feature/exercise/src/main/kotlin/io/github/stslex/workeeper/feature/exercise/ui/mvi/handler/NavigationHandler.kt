// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.NavigationHandlerFactory
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
    store: ExerciseHandlerStore,
) : Handler<Action.Navigation>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Init -> processInit()
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

    private fun processInit() {
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
        store: ExerciseHandlerStore,
    ) : NavigationHandlerFactory<NavigationHandler>(
        creator = { navigator -> NavigationHandler(navigator, store) },
    )
}
