package io.github.stslex.workeeper.feature.home.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.ui.components.DatePickerDialog
import io.github.stslex.workeeper.feature.home.ui.components.HomeActionButton
import io.github.stslex.workeeper.feature.home.ui.components.HomeToolbarWidget
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseChartPreviewParameterProvider
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.model.HomeTabs
import io.github.stslex.workeeper.feature.home.ui.mvi.store.CalendarState
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeAllState
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeChartsState
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.ui.mvi.store.SingleChartUiModel
import io.github.stslex.workeeper.feature.home.ui.tabs.AllTabsWidget
import io.github.stslex.workeeper.feature.home.ui.tabs.ChartsWidget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeWidget(
    homeAllState: HomeAllState,
    chartsState: HomeChartsState,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    consume: (Action) -> Unit,
    lazyState: LazyListState,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { HomeTabs.entries.size },
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        floatingActionButton = {
            AnimatedContent(pagerState.currentPage) { page ->
                when (page) {
                    HomeTabs.ALL.ordinal -> with(sharedTransitionScope) {
                        HomeActionButton(
                            modifier = Modifier
                                .sharedBounds(
                                    sharedContentState = sharedTransitionScope.rememberSharedContentState(
                                        "createExercise"
                                    ),
                                    animatedVisibilityScope = animatedContentScope,
                                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                ),
                            selectedMode = homeAllState.selectedItems.isNotEmpty()
                        ) {
                            consume(Action.Click.FloatButtonClick)
                        }
                    }
                }
            }

        },
        topBar = {
            HomeToolbarWidget(
                pagerState = pagerState,
                onTabClick = {
                    // Switch to the selected tab
                }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = pagerState
        ) { pageNumber ->
            when (pageNumber) {
                HomeTabs.ALL.ordinal -> {
                    AllTabsWidget(
                        state = homeAllState,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        consume = consume,
                        lazyState = lazyState
                    )
                }

                HomeTabs.CHARTS.ordinal -> {
                    ChartsWidget(
                        state = chartsState,
                        consume = consume
                    )

                    when (chartsState.calendarState) {
                        CalendarState.Opened.StartDate -> DatePickerDialog(
                            timestamp = chartsState.startDate.timestamp,
                            onDismissRequest = { consume(Action.Click.Calendar.Close) },
                            dateChange = { consume(Action.Input.ChangeStartDate(it)) }
                        )

                        CalendarState.Opened.EndDate -> DatePickerDialog(
                            timestamp = chartsState.endDate.timestamp,
                            onDismissRequest = { consume(Action.Click.Calendar.Close) },
                            dateChange = { consume(Action.Input.ChangeEndDate(it)) }
                        )

                        else -> Unit
                    }
                }

                else -> throw IllegalStateException("Pager index out of bound")
            }
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
private fun HomeWidgetPreview(
    @PreviewParameter(ExerciseChartPreviewParameterProvider::class)
    charts: ImmutableList<SingleChartUiModel>
) {
    AppTheme {
        val items = Array(10) { index ->
            ExerciseUiModel(
                uuid = Uuid.random().toString(),
                name = "nameOfExercise$index",
                sets = index,
                reps = index,
                weight = 60.0 + index,
                dateProperty = DateProperty.new(System.currentTimeMillis())
            )
        }.toList()
        val itemsPaging = {
            flowOf(PagingData.from(items))
        }
        val homeAllState = HomeAllState(
            items = itemsPaging,
            selectedItems = persistentSetOf(),
            query = ""
        )
        val startDate = System.currentTimeMillis()
        val singleDay = 24 * 60 * 60 * 1000
        val endDate = System.currentTimeMillis() - (7L * singleDay) // 7 days default

        val name = "Test Exercise"
        val chartsState = HomeChartsState(
            name = name,
            startDate = DateProperty.new(startDate),
            endDate = DateProperty.new(endDate),
            charts = charts,
            calendarState = CalendarState.Closed
        )
        val pagerState = rememberPagerState(
            initialPage = 1
        ) { HomeTabs.entries.size }
        AnimatedContent("") {
            SharedTransitionScope {
                HomeWidget(
                    homeAllState = homeAllState,
                    chartsState = chartsState,
                    sharedTransitionScope = this,
                    animatedContentScope = this@AnimatedContent,
                    pagerState = pagerState,
                    consume = {},
                    lazyState = LazyListState(),
                    modifier = it
                )
            }
        }
    }
}