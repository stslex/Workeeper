package io.github.stslex.workeeper.feature.charts.ui

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
import io.github.stslex.workeeper.feature.charts.di.ChartsFeature
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.ChartsComponent
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.chartsGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navScreen<Screen.BottomBar.Charts> {
        val component = remember(navigator) { ChartsComponent.create(navigator) }
        NavComponentScreen(ChartsFeature, component) { processor ->
            val haptic = LocalHapticFeedback.current
            processor.Handle { event ->
                when (event) {
                    is ChartsStore.Event.HapticFeedback -> haptic.performHapticFeedback(event.type)
                }
            }
            AllChartsMainWidget(
                modifier = modifier,
                state = processor.state.value,
                consume = processor::consume,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = this,
            )
        }
    }
}
