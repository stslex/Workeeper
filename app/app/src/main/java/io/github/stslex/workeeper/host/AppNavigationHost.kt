// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.ui.allExercisesGraph
import io.github.stslex.workeeper.feature.all_trainings.ui.allTrainingsGraph
import io.github.stslex.workeeper.feature.archive.ui.archiveGraph
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
import io.github.stslex.workeeper.feature.exercise_chart.ui.exerciseChartGraph
import io.github.stslex.workeeper.feature.home.ui.homeGraph
import io.github.stslex.workeeper.feature.image_viewer.ui.imageViewerGraph
import io.github.stslex.workeeper.feature.live_workout.ui.liveWorkoutGraph
import io.github.stslex.workeeper.feature.past_session.ui.pastSessionGraph
import io.github.stslex.workeeper.feature.plan_editor.ui.planEditorGraph
import io.github.stslex.workeeper.feature.settings.ui.settingsGraph
import io.github.stslex.workeeper.feature.single_training.ui.singleTrainingsGraph

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AppNavigationHost(
    navigatorHolder: NavigatorHolder,
    modifier: Modifier = Modifier,
) {
    SharedTransitionLayout(
        modifier = modifier,
    ) {
        val bottomBarModifier = Modifier
            .fillMaxSize()
            .padding(bottom = AppDimension.BottomNavBar.height)
            .systemBarsPadding()
            .background(MaterialTheme.colorScheme.background)

        val standardModifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(MaterialTheme.colorScheme.background)

        val motionDuration = AppUi.motion.base

        ClearFocusOnDestinationChanged(navigatorHolder)

        NavHost(
            modifier = Modifier.fillMaxSize(),
            navController = navigatorHolder.navController,
            startDestination = Screen.BottomBar.Home,
            enterTransition = {
                fadeIn(
                    animationSpec = tween(motionDuration),
                    initialAlpha = 0.3f,
                )
            },
            exitTransition = {
                fadeOut(
                    animationSpec = tween(motionDuration),
                    targetAlpha = 0f,
                )
            },
        ) {
            // The one place the navigation library's builder is wrapped. Every graph below
            // registers against NavGraphScope and never names the library's own builder, so
            // re-pointing this line is enough to change what backs them.
            with(NavGraphScope(this)) {
                homeGraph(
                    modifier = bottomBarModifier
                        .reportScreenPlace<Screen.BottomBar.Home>()
                        .testTag("HomeGraph"),
                )
                allTrainingsGraph(
                    modifier = bottomBarModifier
                        .reportScreenPlace<Screen.BottomBar.AllTrainings>()
                        .testTag("AllTrainingsGraph"),
                )
                allExercisesGraph(
                    modifier = bottomBarModifier
                        .reportScreenPlace<Screen.BottomBar.AllExercises>()
                        .testTag("AllExercisesGraph"),
                )
                singleTrainingsGraph(
                    modifier = standardModifier
                        .reportScreenPlace<Screen.Training>()
                        .testTag("SingleTrainingGraph"),
                )
                exerciseGraph(
                    modifier = standardModifier
                        .reportScreenPlace<Screen.Exercise>()
                        .testTag("ExerciseGraph"),
                )
                liveWorkoutGraph(
                    modifier = standardModifier
                        .reportScreenPlace<Screen.LiveWorkout>()
                        .testTag("LiveWorkoutGraph"),
                )
                pastSessionGraph(
                    modifier = standardModifier
                        .reportScreenPlace<Screen.PastSession>()
                        .testTag("PastSessionGraph"),
                )
                imageViewerGraph(
                    modifier = standardModifier
                        .reportScreenPlace<Screen.ExerciseImage>()
                        .testTag("ImageViewerGraph"),
                )
                settingsGraph(
                    modifier = standardModifier
                        .reportScreenPlace<Screen.Settings>()
                        .testTag("SettingsGraph"),
                )
                archiveGraph(
                    modifier = standardModifier
                        .reportScreenPlace<Screen.Archive>()
                        .testTag("ArchiveGraph"),
                )
                exerciseChartGraph(
                    modifier = standardModifier
                        .reportScreenPlace<Screen.ExerciseChart>()
                        .testTag("ExerciseChartGraph"),
                )
                planEditorGraph(
                    modifier = standardModifier
                        .reportScreenPlace<Screen.PlanEditor>()
                        .testTag("PlanEditorGraph"),
                )
            }
        }
    }
}

private inline fun <reified S : Screen> Modifier.reportScreenPlace(): Modifier {
    val action = RecordAction.OnScreenPlaced(S::class)
    return this.onPlaced {
        PerformanceMetricsRecorder.process(action)
    }
}
