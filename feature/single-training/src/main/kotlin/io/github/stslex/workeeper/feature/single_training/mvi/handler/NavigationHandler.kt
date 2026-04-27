// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action

@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
    data: Screen.Training,
) : SingleTrainingComponent(data), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
            is Action.Navigation.OpenExerciseDetail ->
                navigator.navTo(Screen.Exercise(uuid = action.uuid))
            // OpenSession + OpenLiveWorkout will land on the Past session detail and Live
            // workout screens delivered in Stage 5.4. For now the navigator targets the
            // existing entries; real screens will follow in the next stage.
            is Action.Navigation.OpenSession -> Unit
            is Action.Navigation.OpenLiveWorkout -> Unit
        }
    }
}
