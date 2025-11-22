package io.github.stslex.workeeper.feature.charts.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.charts.di.ChartsFeature
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.chartsGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ChartsFeature) { processor ->
        val haptic = LocalHapticFeedback.current

        val pagerState = rememberPagerState(initialPage = 0) {
            processor.state.value.chartState.content?.charts?.size ?: 0
        }
        val chartsListState = rememberLazyListState()

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .filter {
                    pagerState.isScrollInProgress && pagerState.targetPage == it
                }
                .distinctUntilChanged()
                .collectLatest { page ->
                    processor.consume(ChartsStore.Action.Input.CurrentChartPageChange(page))
                }
        }

        suspend fun scrollHeader(animated: Boolean, index: Int) {
            if (animated) {
                chartsListState.animateScrollToItem(index)
            } else {
                chartsListState.scrollToItem(index)
            }
        }

        processor.Handle { event ->
            when (event) {
                is ChartsStore.Event.HapticFeedback -> {
                    haptic.performHapticFeedback(event.type)
                }

                is ChartsStore.Event.ScrollChartPager -> {
                    pagerState.animateScrollToPage(event.chartIndex)
                }

                is ChartsStore.Event.ScrollChartHeader -> {
                    if (event.force) {
                        scrollHeader(event.animated, event.chartIndex)
                    } else {
                        val isFullyVisible = chartsListState.layoutInfo.visibleItemsInfo.any {
                            it.index == event.chartIndex &&
                                it.offset >= chartsListState.layoutInfo.viewportStartOffset &&
                                it.offset + it.size <= chartsListState.layoutInfo.viewportEndOffset
                        }
                        if (isFullyVisible.not()) {
                            scrollHeader(event.animated, event.chartIndex)
                        }
                    }
                }
            }
        }

        ChartsScreenWidget(
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
