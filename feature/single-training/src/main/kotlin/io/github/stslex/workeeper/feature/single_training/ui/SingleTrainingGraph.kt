package io.github.stslex.workeeper.feature.single_training.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
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
fun NavGraphBuilder.singleTrainingsEditGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier
) {
    navScreen<Screen.Training.Data> { data ->
        val component = remember(navigator) {
            SingleTrainingComponent.create(
                navigator = navigator, uuid = data.uuid
            )
        }
        SingleTrainingsNavScreen(
            component = component,
            sharedTransitionScope = sharedTransitionScope,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.singleTrainingsCreateGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier
) {
    navScreen<Screen.Training.New> {
        val component = remember(navigator) {
            SingleTrainingComponent.create(
                navigator = navigator,
                uuid = null
            )
        }
        SingleTrainingsNavScreen(
            component = component,
            sharedTransitionScope = sharedTransitionScope,
            modifier = modifier
        )
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun SingleTrainingsNavScreen(
    component: SingleTrainingComponent,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier
) {
    NavComponentScreen(TrainingFeature, component) { processor ->
        SingleTrainingsScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume
        )
    }
}

