// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsScope
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action

@SingleIn(AllTrainingsScope::class)
internal class NavigationHandler @Inject constructor(
    private val navigator: Navigator,
) : Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.OpenDetail -> navigator.navTo(Screen.Training(uuid = action.uuid))
            Action.Navigation.OpenCreate -> navigator.navTo(Screen.Training(uuid = null))
        }
    }
}
