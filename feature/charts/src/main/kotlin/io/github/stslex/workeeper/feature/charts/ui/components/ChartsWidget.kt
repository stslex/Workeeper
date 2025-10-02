package io.github.stslex.workeeper.feature.charts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.search.SearchPagingWidget
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartsState
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartsType
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ExerciseChartPreviewParameterProvider
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.SingleChartUiModel
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.State
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun ChartsWidget(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        SearchPagingWidget(
            modifier = Modifier
                .padding(AppDimension.Padding.big),
            query = state.name,
            onQueryChange = { consume(Action.Input.Query(it)) },
        )
        Spacer(Modifier.height(AppDimension.Padding.large))
        ChartsTypePickerWidget(
            selectedType = state.type,
            onClick = { consume(Action.Click.ChangeType(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimension.Padding.big),
        )
        Spacer(Modifier.height(AppDimension.Padding.large))
        DatePickersWidget(
            modifier = Modifier
                .padding(horizontal = AppDimension.Padding.big),
            startDate = state.startDate,
            endDate = state.endDate,
            onStartDateClick = { consume(Action.Click.Calendar.StartDate) },
            onEndDateClick = { consume(Action.Click.Calendar.EndDate) },
        )
        Spacer(Modifier.height(AppDimension.Padding.large))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(
                        topStart = MaterialTheme.shapes.extraLarge.topStart,
                        topEnd = MaterialTheme.shapes.extraLarge.topEnd,
                        bottomEnd = CornerSize(0.dp),
                        bottomStart = CornerSize(0.dp),
                    ),
                ),
        ) {
            when (val chartState = state.chartState) {
                is ChartsState.Content -> ChartsCanvaWidget(
                    charts = chartState.charts,
                    modifier = Modifier
                        .padding(AppDimension.Padding.big),
                )

                is ChartsState.Loading -> LoadingWidget(
                    modifier = Modifier
                        .align(Alignment.Center),
                )

                is ChartsState.Empty -> EmptyWidget(query = state.name)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingWidget(modifier: Modifier) {
    LoadingIndicator(
        modifier = modifier,
    )
}

@Composable
@Preview
private fun ChartsWidgetPreview(
    @PreviewParameter(ExerciseChartPreviewParameterProvider::class)
    charts: ImmutableList<SingleChartUiModel>,
) {
    AppTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        ) {
            val name = "Test Exercise"
            val chartsState = State(
                name = name,
                startDate = PropertyHolder.DateProperty.now(),
                endDate = PropertyHolder.DateProperty.now(),
                chartState = ChartsState.Loading,
                type = ChartsType.TRAINING,
                calendarState = CalendarState.Closed,
            )
            ChartsWidget(
                state = chartsState,
                consume = {},
            )
        }
    }
}
