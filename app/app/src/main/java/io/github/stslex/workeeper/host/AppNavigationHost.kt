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
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.ui.allExercisesGraph
import io.github.stslex.workeeper.feature.all_trainings.ui.allTrainingsGraph
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
import io.github.stslex.workeeper.feature.exercise_chart.ui.exerciseChartGraph
import io.github.stslex.workeeper.feature.home.ui.homeGraph
import io.github.stslex.workeeper.feature.image_viewer.ui.imageViewerGraph
import io.github.stslex.workeeper.feature.live_workout.ui.liveWorkoutGraph
import io.github.stslex.workeeper.feature.past_session.ui.pastSessionGraph
import io.github.stslex.workeeper.feature.plan_editor.ui.planEditorGraph
import io.github.stslex.workeeper.feature.settings.ui.archiveGraph
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

        val motionDuration = AppUi.motion.normal

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
            homeGraph(
                modifier = bottomBarModifier
                    .reportScreenPlace<Screen.BottomBar.Home>()
                    .testTag("HomeGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            allTrainingsGraph(
                modifier = bottomBarModifier
                    .reportScreenPlace<Screen.BottomBar.AllTrainings>()
                    .testTag("AllTrainingsGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            allExercisesGraph(
                modifier = bottomBarModifier
                    .reportScreenPlace<Screen.BottomBar.AllExercises>()
                    .testTag("AllExercisesGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            singleTrainingsGraph(
                modifier = Modifier
                    .reportScreenPlace<Screen.Training>()
                    .testTag("SingleTrainingGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            exerciseGraph(
                modifier = Modifier
                    .reportScreenPlace<Screen.Exercise>()
                    .testTag("ExerciseGraph"),
            )
            liveWorkoutGraph(
                modifier = Modifier
                    .reportScreenPlace<Screen.LiveWorkout>()
                    .testTag("LiveWorkoutGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            pastSessionGraph(
                modifier = Modifier
                    .reportScreenPlace<Screen.PastSession>()
                    .testTag("PastSessionGraph"),
            )
            imageViewerGraph(
                modifier = Modifier
                    .reportScreenPlace<Screen.ExerciseImage>()
                    .testTag("ImageViewerGraph"),
            )
            settingsGraph(
                modifier = Modifier
                    .reportScreenPlace<Screen.Settings>()
                    .testTag("SettingsGraph"),
            )
            archiveGraph(
                modifier = Modifier
                    .reportScreenPlace<Screen.Archive>()
                    .testTag("ArchiveGraph"),
            )
            exerciseChartGraph(
                modifier = Modifier
                    .reportScreenPlace<Screen.ExerciseChart>()
                    .testTag("ExerciseChartGraph"),
            )
            planEditorGraph(
                modifier = Modifier
                    .reportScreenPlace<Screen.PlanEditor>()
                    .testTag("PlanEditorGraph"),
            )
        }
    }
}

private inline fun <reified S : Screen> Modifier.reportScreenPlace(): Modifier {
    val action = RecordAction.OnScreenPlaced(S::class)
    return this.onPlaced {
        PerformanceMetricsRecorder.process(action)
    }
}
