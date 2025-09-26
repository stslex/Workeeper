package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingsFeature
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.AllTrainingsComponent
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.allTrainingsGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navScreen<Screen.BottomBar.AllTrainings> {
        val component = remember(navigator) { AllTrainingsComponent.create(navigator) }

        NavComponentScreen(TrainingsFeature, component) { processor ->

            val haptic = LocalHapticFeedback.current

            processor.Handle { event ->
                when (event) {
                    is TrainingStore.Event.Haptic -> haptic.performHapticFeedback(event.type)
                }
            }

            BackHandler(
                enabled = processor.state.value.selectedItems.isNotEmpty(),
            ) {
                processor.consume(Action.Click.BackHandler)
            }

            AllTrainingsScreen(
                modifier = modifier,
                state = processor.state.value,
                consume = processor::consume,
            )
        }
    }
}
