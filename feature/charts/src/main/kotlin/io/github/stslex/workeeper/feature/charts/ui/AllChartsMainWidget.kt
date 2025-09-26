package io.github.stslex.workeeper.feature.charts.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.charts.ui.components.ChartsWidget
import io.github.stslex.workeeper.feature.charts.ui.components.DatePickerDialog
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartsType
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ExerciseChartPreviewParameterProvider
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.SingleChartUiModel
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.State
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AllChartsMainWidget(
    state: State,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        ChartsWidget(
            modifier = Modifier.fillMaxSize(),
            state = state,
            consume = consume,
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
private fun HomeWidgetPreview(
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
            charts = charts,
            type = ChartsType.TRAINING,
            calendarState = CalendarState.Closed,
        )
        AnimatedContent("") {
            SharedTransitionScope {
                AllChartsMainWidget(
                    state = chartsState,
                    sharedTransitionScope = this,
                    animatedContentScope = this@AnimatedContent,
                    consume = {},
                    modifier = it,
                )
            }
        }
    }
}
