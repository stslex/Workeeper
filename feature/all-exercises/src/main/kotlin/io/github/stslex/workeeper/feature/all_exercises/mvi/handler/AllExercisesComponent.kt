package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllExercises

@Stable
interface AllExercisesComponent : Component<AllExercises> {

    override val data: AllExercises get() = AllExercises

    companion object {

        fun create(
            navigator: Navigator,
        ): AllExercisesComponent = NavigationHandler(
            navigator = navigator,
        )
    }
}
