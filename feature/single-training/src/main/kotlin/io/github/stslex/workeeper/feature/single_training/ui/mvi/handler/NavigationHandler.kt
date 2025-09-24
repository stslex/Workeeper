package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action

internal class NavigationHandler(
    private val navigator: Navigator,
    override val uuid: String?,
) : SingleTrainingComponent, Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.PopBack -> navigator.popBack()
            is Action.Navigation.CreateExercise -> navigator.navTo(
                Screen.Exercise(
                    uuid = null,
                    trainingUuid = action.trainingUuid,
                ),
            )

            is Action.Navigation.OpenExercise -> navigator.navTo(
                Screen.Exercise(
                    uuid = action.exerciseUuid,
                    trainingUuid = action.trainingUuid,
                ),
            )
        }
    }
}
