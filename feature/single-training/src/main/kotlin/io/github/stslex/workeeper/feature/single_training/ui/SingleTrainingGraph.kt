package io.github.stslex.workeeper.feature.single_training.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.feature.single_training.di.TrainingFeature
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.SingleTrainingComponent

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.singleTrainingsGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier
) {
    navScreen<Screen.Training> { data ->
        val component = remember(navigator) {
            SingleTrainingComponent.create(
                navigator = navigator,
                uuid = data.uuid
            )
        }
        NavComponentScreen(TrainingFeature, component) { processor ->
            SingleTrainingsScreen(
                modifier = modifier,
                state = processor.state.value,
                consume = processor::consume
            )
        }
    }
}

