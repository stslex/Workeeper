package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.RootComponent
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.mvi.handler.AllExercisesComponent
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.AllTrainingsComponent
import io.github.stslex.workeeper.feature.exercise.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.ChartComponent
import io.github.stslex.workeeper.feature.home.mvi.handler.HomeComponent
import io.github.stslex.workeeper.feature.image_viewer.mvi.handler.ImageViewerComponent
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.LiveWorkoutComponent
import io.github.stslex.workeeper.feature.past_session.mvi.handler.PastSessionComponent
import io.github.stslex.workeeper.feature.settings.mvi.handler.ArchiveComponent
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsComponent
import io.github.stslex.workeeper.feature.single_training.mvi.handler.SingleTrainingComponent

class RootComponentImpl(
    private val navigator: Navigator,
) : RootComponent {

    override fun createComponent(
        screen: Screen,
    ): Component<*> = when (screen) {
        Screen.BottomBar.Home -> HomeComponent.create(navigator)
        Screen.BottomBar.AllExercises -> AllExercisesComponent.create(navigator)
        Screen.BottomBar.AllTrainings -> AllTrainingsComponent.create(navigator)
        is Screen.Exercise -> ExerciseComponent.create(navigator, screen)
        is Screen.Training -> SingleTrainingComponent.create(navigator, screen)
        is Screen.LiveWorkout -> LiveWorkoutComponent.create(navigator, screen)
        Screen.Settings -> SettingsComponent.create(navigator)
        Screen.Archive -> ArchiveComponent.create(navigator)
        is Screen.PastSession -> PastSessionComponent.create(navigator, screen)
        is Screen.ExerciseImage -> ImageViewerComponent.create(navigator, screen)
        is Screen.ExerciseChart -> ChartComponent.create(navigator, screen)
    }
}
