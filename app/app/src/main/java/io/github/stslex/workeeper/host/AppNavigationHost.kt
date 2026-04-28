package io.github.stslex.workeeper.host

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.ui.allExercisesGraph
import io.github.stslex.workeeper.feature.all_trainings.ui.allTrainingsGraph
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
import io.github.stslex.workeeper.feature.home.ui.homeGraph
import io.github.stslex.workeeper.feature.image_viewer.ui.imageViewerGraph
import io.github.stslex.workeeper.feature.live_workout.ui.liveWorkoutGraph
import io.github.stslex.workeeper.feature.past_session.ui.pastSessionGraph
import io.github.stslex.workeeper.feature.settings.ui.archiveGraph
import io.github.stslex.workeeper.feature.settings.ui.settingsGraph
import io.github.stslex.workeeper.feature.single_training.ui.singleTrainingsGraph

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AppNavigationHost(
    navigator: Navigator,
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
        NavHost(
            modifier = Modifier.fillMaxSize(),
            navController = navigator.navController,
            startDestination = Screen.BottomBar.Home,
        ) {
            homeGraph(
                modifier = bottomBarModifier
                    .testTag("HomeGraph"),
            )
            allTrainingsGraph(
                modifier = bottomBarModifier
                    .testTag("AllTrainingsGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            allExercisesGraph(
                modifier = bottomBarModifier
                    .testTag("AllExercisesGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            singleTrainingsGraph(
                modifier = Modifier
                    .testTag("SingleTrainingGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            exerciseGraph(
                modifier = Modifier
                    .testTag("ExerciseGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            liveWorkoutGraph(
                modifier = Modifier.testTag("LiveWorkoutGraph"),
            )
            pastSessionGraph(
                modifier = Modifier.testTag("PastSessionGraph"),
            )
            imageViewerGraph(
                modifier = Modifier.testTag("ImageViewerGraph"),
            )
            settingsGraph(
                modifier = Modifier.testTag("SettingsGraph"),
            )
            archiveGraph(
                modifier = Modifier.testTag("ArchiveGraph"),
            )
        }
    }
}
