package io.github.stslex.workeeper.feature.all_exercises.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.utils.OnKeyboardVisible
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseFeature
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.AllExercisesComponent
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.allExercisesGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navScreen<Screen.BottomBar.AllExercises> {
        val component = remember(navigator) { AllExercisesComponent.create(navigator) }

        NavComponentScreen(ExerciseFeature, component) { processor ->

            val haptic = LocalHapticFeedback.current

            processor.Handle { event ->
                when (event) {
                    is ExercisesStore.Event.HapticFeedback -> haptic.performHapticFeedback(event.type)
                }
            }

            BackHandler(
                enabled = (
                    processor.state.value.selectedItems.isNotEmpty() ||
                        processor.state.value.query.isNotEmpty()
                    ) && processor.state.value.isKeyboardVisible.not(),
            ) {
                processor.consume(Action.Click.BackHandler)
            }

            OnKeyboardVisible { isKeyboardVisible ->
                processor.consume(Action.Input.KeyboardChange(isKeyboardVisible))
            }

            val lazyListState = rememberLazyListState()
            ExerciseWidget(
                modifier = modifier,
                state = processor.state.value,
                lazyState = lazyListState,
                consume = processor::consume,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = this,
            )
        }
    }
}
