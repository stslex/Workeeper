package io.github.stslex.workeeper.feature.charts.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.charts.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsState
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsType
import io.github.stslex.workeeper.feature.charts.mvi.model.ExerciseChartPreviewParameterProvider
import io.github.stslex.workeeper.feature.charts.mvi.model.SingleChartUiModel
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.State
import io.github.stslex.workeeper.feature.charts.ui.components.ChartsScreenBodyWidget
import io.github.stslex.workeeper.feature.charts.ui.components.DatePickerDialog
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Suppress("unused")
internal fun ChartsScreenWidget(
    state: State,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    pagerState: PagerState,
    chartsListState: LazyListState,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        ChartsScreenBodyWidget(
            modifier = Modifier.fillMaxSize(),
            state = state,
            pagerState = pagerState,
            consume = consume,
            chartsListState = chartsListState,
        )

        when (state.calendarState) {
            CalendarState.Opened.StartDate -> DatePickerDialog(
                timestamp = state.startDate.value,
                onDismissRequest = { consume(Action.Click.Calendar.Close) },
                dateChange = { consume(Action.Input.ChangeStartDate(it)) },
            )

            CalendarState.Opened.EndDate -> DatePickerDialog(
                timestamp = state.endDate.value,
                onDismissRequest = { consume(Action.Click.Calendar.Close) },
                dateChange = { consume(Action.Input.ChangeEndDate(it)) },
            )

            else -> Unit
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
private fun ChartsScreenWidgetPreview(
    @PreviewParameter(ExerciseChartPreviewParameterProvider::class)
    charts: ImmutableList<SingleChartUiModel>,
) {
    AppTheme {
        val startDate = System.currentTimeMillis()
        val singleDay = 24 * 60 * 60 * 1000
        val endDate = System.currentTimeMillis() - (7L * singleDay) // 7 days default

        val name = "Test Exercise"
        val chartsState = State(
            name = name,
            startDate = PropertyHolder.DateProperty.new(startDate),
            endDate = PropertyHolder.DateProperty.new(endDate),
            chartState = ChartsState.Content(
                charts = charts,
                selectedChartIndex = 0,
                chartsTitles = charts.map { it.name }.toImmutableList(),
            ),
            type = ChartsType.TRAINING,
            calendarState = CalendarState.Closed,
        )
        AnimatedContent("") {
            SharedTransitionScope {
                ChartsScreenWidget(
                    state = chartsState,
                    sharedTransitionScope = this,
                    animatedContentScope = this@AnimatedContent,
                    consume = {},
                    modifier = it,
                    pagerState = rememberPagerState { 1 },
                    chartsListState = rememberLazyListState(),
                )
            }
        }
    }
}
