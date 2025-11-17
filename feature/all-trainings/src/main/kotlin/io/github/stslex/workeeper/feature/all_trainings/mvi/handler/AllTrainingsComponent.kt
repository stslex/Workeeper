package io.github.stslex.workeeper.feature.all_trainings.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllTrainings

abstract class AllTrainingsComponent : Component<AllTrainings>(AllTrainings) {

    companion object {

        fun create(navigator: Navigator): AllTrainingsComponent = NavigationHandler(navigator)
    }
}
