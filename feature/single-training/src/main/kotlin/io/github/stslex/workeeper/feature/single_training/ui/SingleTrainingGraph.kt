package io.github.stslex.workeeper.feature.single_training.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.single_training.di.TrainingFeature

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.singleTrainingsGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(TrainingFeature, navigator) { processor ->
        with(sharedTransitionScope) {
            SingleTrainingsScreen(
                modifier = modifier
                    .sharedBounds(
                        sharedContentState = sharedTransitionScope.rememberSharedContentState(
                            processor.state.value.training.uuid.ifBlank { "createTraining" },
                        ),
                        animatedVisibilityScope = this@navComponentScreen,
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                            ContentScale.FillBounds,
                            Alignment.Center,
                        ),
                    ),
                state = processor.state.value,
                consume = processor::consume,
            )
        }
    }
}
