package io.github.stslex.workeeper.feature.charts.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.feature.charts.di.ChartsFeature
import io.github.stslex.workeeper.feature.charts.mvi.handler.ChartsComponent
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore

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

            val pagerState = rememberPagerState(initialPage = 0) {
                processor.state.value.chartState.content?.charts?.size ?: 0
            }
            val chartsListState = rememberLazyListState()

            LaunchedEffect(pagerState.currentPage) {
                processor.consume(ChartsStore.Action.Input.ScrollToChart(pagerState.currentPage))
            }

            processor.Handle { event ->
                when (event) {
                    is ChartsStore.Event.HapticFeedback -> {
                        haptic.performHapticFeedback(event.type)
                    }

                    is ChartsStore.Event.OnChartTitleChange -> {
                        pagerState.animateScrollToPage(event.chartIndex)
                    }

                    is ChartsStore.Event.OnChartTitleScrolled -> {
                        chartsListState.animateScrollToItem(event.chartIndex)
                    }
                }
            }

            AllChartsMainWidget(
                modifier = modifier,
                state = processor.state.value,
                consume = processor::consume,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = this,
                pagerState = pagerState,
                chartsListState = chartsListState,
            )
        }
    }
}
