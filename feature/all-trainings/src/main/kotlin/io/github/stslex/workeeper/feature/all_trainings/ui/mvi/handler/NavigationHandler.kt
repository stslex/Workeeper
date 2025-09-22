package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action

internal class NavigationHandler(
    private val navigator: Navigator
) : AllTrainingsComponent, Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.CreateTraining -> navigator.navTo(Screen.Training(null))
            is Action.Navigation.OpenTraining -> navigator.navTo(Screen.Training(action.uuid))
        }
    }
}