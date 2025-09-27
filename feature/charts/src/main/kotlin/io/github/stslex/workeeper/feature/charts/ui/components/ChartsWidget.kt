package io.github.stslex.workeeper.feature.charts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
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
        ChartsTypePickerWidget(
            selectedType = state.type,
            onClick = { consume(Action.Click.ChangeType(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppDimension.Padding.large),
        )
        DatePickersWidget(
            startDate = state.startDate,
            endDate = state.endDate,
            onStartDateClick = { consume(Action.Click.Calendar.StartDate) },
            onEndDateClick = { consume(Action.Click.Calendar.EndDate) },
        )
        ChartsCanvaWidget(
            charts = state.charts,
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimension.Padding.medium)
                .background(MaterialTheme.colorScheme.background)
                .padding(AppDimension.Padding.medium),
        )
        if (state.charts.isEmpty()) {
            EmptyWidget(query = state.name)
        }
    }
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
                charts = charts,
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
