package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.RootComponent
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.mvi.handler.AllExerciseComponent
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.AllTrainingsComponent
import io.github.stslex.workeeper.feature.charts.mvi.handler.ChartsComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.SingleTrainingComponent

class RootComponentImpl(
    private val navigator: Navigator,
) : RootComponent {

    override fun createComponent(
        screen: Screen,
    ): Component<*> = when (screen) {
        Screen.BottomBar.AllExercises -> AllExerciseComponent.create(navigator)
        Screen.BottomBar.AllTrainings -> AllTrainingsComponent.create(navigator)
        Screen.BottomBar.Charts -> ChartsComponent.create(navigator)
        is Screen.Exercise -> ExerciseComponent.create(navigator, screen)
        is Screen.Training -> SingleTrainingComponent.create(navigator, screen)
    }
}
