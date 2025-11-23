package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Action

@Stable
internal class NavigationHandler(
    private val navigator: Navigator,
) : AllExerciseComponent(), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.CreateExerciseDialog -> navigator.navTo(Exercise(null, null))
            is Action.Navigation.OpenExercise -> navigator.navTo(
                Exercise(
                    uuid = action.uuid,
                    trainingUuid = null,
                ),
            )
        }
    }
}
