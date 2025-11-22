package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.utils.OnKeyboardVisible
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingsFeature
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore.Action

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.allTrainingsGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(TrainingsFeature) { processor ->

        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is TrainingStore.Event.Haptic -> haptic.performHapticFeedback(event.type)
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

        AllTrainingsScreen(
            modifier = modifier,
            state = processor.state.value,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = this,
            consume = processor::consume,
        )
    }
}
