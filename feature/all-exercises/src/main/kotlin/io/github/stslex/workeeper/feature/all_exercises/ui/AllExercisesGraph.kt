package io.github.stslex.workeeper.feature.all_exercises.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseFeature
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.AllExercisesComponent
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.allExercisesGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier
) {
    navScreen<Screen.BottomBar.AllExercises> {
        ExerciseScreen(
            modifier = modifier,
            sharedTransitionScope = sharedTransitionScope,
            component = remember { AllExercisesComponent.create(navigator) },
            animatedContentScope = this
        )
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ExerciseScreen(
    component: AllExercisesComponent,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier
) {
    NavComponentScreen(ExerciseFeature, component) { processor ->

        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is ExercisesStore.Event.HapticFeedback -> haptic.performHapticFeedback(event.type)
            }
        }

        val lazyListState = rememberLazyListState()
        ExerciseWidget(
            modifier = modifier,
            state = processor.state.value,
            lazyState = lazyListState,
            consume = processor::consume,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
        )
    }
}