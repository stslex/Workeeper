package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action.Navigation

@Stable
internal class HomeComponentImpl(
    private val navigator: Navigator,
) : HomeComponent {

    override fun HomeHandlerStore.invoke(action: Navigation) {
        when (action) {
            is Navigation.CreateExerciseDialog -> navigator.navTo(Exercise.New)
            is Navigation.OpenExercise -> navigator.navTo(action.data)
        }
    }

}