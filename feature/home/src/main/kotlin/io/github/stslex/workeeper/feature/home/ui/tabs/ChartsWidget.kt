package io.github.stslex.workeeper.feature.home.ui.tabs

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.ui.components.DatePickersWidget
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.calculateSizes
import io.github.stslex.workeeper.feature.home.ui.mvi.store.CalendarState
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeChartsState
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.AnimationMode
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Line
import kotlin.uuid.Uuid

@Composable
internal fun ChartsWidget(
    state: HomeChartsState,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        DatePickersWidget(
            startDate = state.startDate,
            endDate = state.endDate,
            onStartDateClick = { consume(Action.Click.Calendar.StartDate) },
            onEndDateClick = { consume(Action.Click.Calendar.EndDate) },
        )
        val textColor = MaterialTheme.colorScheme.onBackground
        val textStyle = MaterialTheme.typography.titleLarge.copy(color = textColor)
        if (state.charts.isNotEmpty()) {
            LineChart(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimension.Padding.medium)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(AppDimension.Padding.medium),
                data = remember(state.charts) {
                    state.charts.mapIndexed { index, item ->
                        val color = getRandomColor(index)
                        Line(
                            label = item.name,
                            values = item.properties,
                            color = SolidColor(color),
                            firstGradientFillColor = color.copy(alpha = .5f),
                            secondGradientFillColor = Color.Transparent,
                            strokeAnimationSpec = tween(2000, easing = EaseInOutCubic),
                            gradientAnimationDelay = 1000,
                            drawStyle = DrawStyle.Stroke(width = 2.dp),
                        )
                    }
                },
                animationMode = AnimationMode.Together(delayBuilder = {
                    it * 500L
                }),
                labelProperties = LabelProperties(
                    enabled = true,
                    textStyle = textStyle
                ),
                labelHelperProperties = LabelHelperProperties(
                    enabled = true,
                    textStyle = textStyle
                ),
                indicatorProperties = HorizontalIndicatorProperties(
                    enabled = true,
                    textStyle = textStyle
                ),
            )
        }
    }
}

private fun getRandomColor(index: Int): Color = Color(
    red = getRandomColorInt(index.inc(), 1),
    green = getRandomColorInt(index.inc(), 2),
    blue = getRandomColorInt(index.inc(), 3),
)

private fun getRandomColorInt(index: Int, colorIndex: Int): Int = ((0..255).random() * index * colorIndex) % 255

@Composable
@Preview
private fun ChartsWidgetPreview() {
    AppTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background)
        ) {
            val singleDay = 24 * 60 * 60 * 1000
            val startDate = System.currentTimeMillis() - (7L * singleDay)
            val endDate = System.currentTimeMillis() // 7 days default

            val name = "Test Exercise"
            val listOfData = List(7) {
                ExerciseDataModel(
                    uuid = Uuid.random().toString(),
                    name = name,
                    sets = it,
                    reps = it,
                    weight = it.toDouble(),
                    timestamp = endDate - (it * singleDay)
                )
            }
            val chartsState = HomeChartsState(
                name = name,
                startDate = DateProperty.new(startDate),
                endDate = DateProperty.new(endDate),
                charts = listOfData.calculateSizes(),
                calendarState = CalendarState.Closed
            )
            ChartsWidget(
                state = chartsState,
                consume = {}
            )
        }
    }
}