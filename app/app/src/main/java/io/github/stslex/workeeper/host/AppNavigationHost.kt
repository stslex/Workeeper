package io.github.stslex.workeeper.host

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
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
        modifier = modifier
    ) {
        NavHost(
            modifier = Modifier.fillMaxSize(),
            navController = navigator.navController,
            startDestination = Screen.BottomBar.Charts,
        ) {
            chartsGraph(
                navigator = navigator,
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            allTrainingsGraph(
                navigator = navigator,
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            allExercisesGraph(
                navigator = navigator,
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            singleTrainingsGraph(
                navigator = navigator,
                sharedTransitionScope = this@SharedTransitionLayout,
            )
            exerciseGraph(
                navigator = navigator,
                sharedTransitionScope = this@SharedTransitionLayout,
            )
        }
    }
}
