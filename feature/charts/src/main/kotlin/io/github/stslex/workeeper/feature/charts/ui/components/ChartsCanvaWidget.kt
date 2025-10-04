package io.github.stslex.workeeper.feature.charts.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.toPx
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ExerciseChartPreviewParameterProvider
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.SingleChartUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

@Composable
internal fun ChartsCanvaWidget(
    charts: ImmutableList<SingleChartUiModel>,
    modifier: Modifier = Modifier,
) {

    val chartsMapped = remember(charts) {
        charts.mapIndexed { index, item ->
            SingleChartCanvasModel(
                name = item.name,
                color = getRandomColor(index),
                properties = item.properties.map { property ->
                    SingleChartCanvasProperty(
                        xValue = property.timeX,
                        yValue = property.valueY,
                    )
                },
            )
        }
    }

    val pagerState = rememberPagerState(initialPage = 0) { chartsMapped.size }
    val coroutineScope = rememberCoroutineScope()

    HorizontalPager(
        modifier = modifier,
        state = pagerState,
        key = { chartsMapped[it].name },
    ) { index ->
        val chart = chartsMapped[index]
        SingleChart(chart)
    }

    FlowRow {
        chartsMapped.forEachIndexed { index, item ->
            val containerColor by animateColorAsState(
                targetValue = if (pagerState.currentPage == index) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                label = "container color animation",
                animationSpec = tween(AppUi.uiFeatures.defaultAnimationDuration),
            )
            val contentColor by animateColorAsState(
                targetValue = if (pagerState.currentPage == index) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "Content color animation",
                animationSpec = tween(AppUi.uiFeatures.defaultAnimationDuration),
            )
            Card(
                modifier = Modifier
                    .padding(AppDimension.Padding.medium),
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
            ) {
                Text(
                    modifier = Modifier
                        .padding(AppDimension.Padding.medium),
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun SingleChart(
    chart: SingleChartCanvasModel,
    modifier: Modifier = Modifier,
) {
    val outlineChartThin = AppDimension.Border.small.toPx

    val chartColor = MaterialTheme.colorScheme.primaryContainer
    val axisColor = MaterialTheme.colorScheme.onPrimaryContainer

    val testMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraLarge)
            .border(
                width = AppDimension.Border.medium,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.extraLarge,
            )
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val points = calculateChartPoints(chart)
        val path = createSmoothPathSimple(points)

        drawPath(
            path = path,
            color = axisColor,
            style = Stroke(
                width = outlineChartThin,
            ),
        )

        val filledPath = Path().apply {
            addPath(path)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        drawPath(
            path = filledPath,
            color = chartColor,
            style = Fill,
        )

        drawAxis(
            points = points,
            textMeasurer = testMeasurer,
            color = axisColor,
        )
    }
}

private fun DrawScope.drawAxis(
    points: List<Offset>,
    textMeasurer: TextMeasurer,
    color: Color,
) {
    val xValueCount = 5
    val xyCalculatedDiff = size.width / xValueCount

    val textSize = 12.sp
    val textStyle = TextStyle(
        color = color,
        fontSize = textSize,
    )
    val textMeasured = textMeasurer.measure(
        text = "0",
        style = textStyle,
    )
    val leftOffset = AppDimension.Padding.medium.toPx()
    val yOffset = textMeasured.size.height.toFloat()

    repeat(5) { index ->
        val xValue = xyCalculatedDiff * index
        drawLine(
            color = color,
            start = Offset(x = xValue, y = 0f),
            end = Offset(x = xValue, y = size.height),
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(10f, 10f),
                phase = 0f,
            ),
        )
        val text = "${index * 20}%"

        val textWidth = textMeasurer.measure(
            text = text,
            style = textStyle,
        ).size.width.toFloat()
        drawText(
            textMeasurer = textMeasurer,
            text = text,
            topLeft = Offset(
                (xValue + leftOffset).coerceAtMost(size.width - textWidth),
                size.height - yOffset,
            ),
            style = textStyle,
            maxLines = 1,
        )
    }

    val verticalLineCount = 6
    val yCalculatedDiff = size.height / verticalLineCount
    val yValueDiff = points.maxOfOrNull { it.y }
        ?.takeIf { it.isFinite() }
        ?.let { maxY -> maxY / verticalLineCount }
        ?: 1f

    repeat(verticalLineCount) { index ->
        val text = "${(yValueDiff * (verticalLineCount - index)).fastRoundToInt()}"

        val yValue = yCalculatedDiff * index
        drawText(
            textMeasurer = textMeasurer,
            text = text,
            topLeft = Offset(leftOffset, yValue - yOffset),
            style = textStyle,
//            overflow = TODO(),
//            softWrap = TODO(),
            maxLines = 1,
        )

        drawLine(
            color = color,
            start = Offset(x = 0f, y = yValue),
            end = Offset(x = size.width, y = yValue),
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(10f, 10f),
                phase = 0f,
            ),
        )
    }
}

private fun DrawScope.calculateChartPoints(
    chart: SingleChartCanvasModel,
): List<Offset> {
    val maxProperty = chart.properties.maxOfOrNull { it.yValue ?: 0f } ?: 1f
    val propertyK = size.height / maxProperty

    val itemsSize = chart.properties.size
    return chart.properties.mapIndexed { propertyIndex, property ->
        val yValue = if (
            property.yValue == null &&
            (propertyIndex == 0 || propertyIndex == itemsSize.dec())
        ) {
            0f
        } else {
            property.yValue?.let { yValue ->
                size.height - yValue * propertyK
            } ?: Float.NaN
        }
        Offset(
            x = size.width * property.xValue,
            y = yValue,
        )
    }
}

@Stable
private data class SingleChartCanvasModel(
    val name: String,
    val color: Color,
    val properties: List<SingleChartCanvasProperty>,
)

private data class SingleChartCanvasProperty(
    val xValue: Float,
    val yValue: Float?,
)

private fun DrawScope.createBackgroundPath(
    strokeThin: Float,
    strokeColor: Color,
    radius: Float,
): Path = Path().apply {
    addRoundRect(
        roundRect = RoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = size.height,
            cornerRadius = CornerRadius(radius, radius),
        ),
    )
}.also { path ->
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = strokeThin,
        ),
    )
}

@Suppress("MagicNumber")
private fun createSmoothPathSimple(points: List<Offset>): Path {
    val path = Path()

    if (points.isEmpty()) return path

    path.moveTo(points[0].x, points[0].y)

    if (points.size < 3) {
        points.forEach {
            path.lineTo(it.x, it.y)
        }
        return path
    }

    val filteredPoints = points.filter { it.isValid() }

    for (i in 0 until filteredPoints.size - 1) {
        val currentPoint = filteredPoints[i]

        if (currentPoint.y.isNaN()) {
            continue
        }

        val nextPoint = filteredPoints[i + 1]

        val controlX = (currentPoint.x + nextPoint.x) / 2f
        val controlY = (currentPoint.y + nextPoint.y) / 2f

        path.quadraticTo(
            x1 = currentPoint.x,
            y1 = currentPoint.y,
            x2 = controlX,
            y2 = controlY,
        )
    }

    path.lineTo(points.last().x, points.last().y)

    return path
}

private fun getRandomColor(index: Int): Color = Color(
    red = getRandomColorInt(index.inc(), 1),
    green = getRandomColorInt(index.inc(), 2),
    blue = getRandomColorInt(index.inc(), 3),
)

private fun getRandomColorInt(index: Int, colorIndex: Int): Int =
    ((0..255).random() * index * colorIndex) % 255

@Composable
@Preview
private fun ChartsCanvaWidgetPreview() {
    AppTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        ) {
            ChartsCanvaWidget(
                charts = ExerciseChartPreviewParameterProvider().values.first(),
            )
        }
    }
}
