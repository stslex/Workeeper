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
import io.github.stslex.workeeper.feature.charts.ui.chartsGraph
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
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
            startDestination = Screen.BottomBar.Charts,
        ) {
            chartsGraph(
                modifier = bottomBarModifier
                    .testTag("ChartsGraph"),
                sharedTransitionScope = this@SharedTransitionLayout,
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
        }
    }
}
