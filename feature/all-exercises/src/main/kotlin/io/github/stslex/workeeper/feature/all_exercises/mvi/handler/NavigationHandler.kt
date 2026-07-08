// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesScope
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action

@SingleIn(AllExercisesScope::class)
internal class NavigationHandler @Inject constructor(
    private val navigator: Navigator,
) : Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.OpenDetail -> navigator.navTo(Screen.Exercise(uuid = action.uuid))
            Action.Navigation.OpenCreate -> navigator.navTo(Screen.Exercise(uuid = null))
        }
    }
}
