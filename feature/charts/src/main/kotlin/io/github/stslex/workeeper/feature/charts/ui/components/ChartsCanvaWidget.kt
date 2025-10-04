package io.github.stslex.workeeper.feature.charts.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.toPx
import io.github.stslex.workeeper.feature.charts.mvi.model.ExerciseChartPreviewParameterProvider
import io.github.stslex.workeeper.feature.charts.mvi.model.SingleChartUiModel
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun ChartsCanvaWidget(
    charts: ImmutableList<SingleChartUiModel>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        modifier = modifier,
        state = pagerState,
        key = { charts[it].name },
        pageSpacing = AppDimension.Padding.big,
    ) { index ->
        SingleChart(charts[index])
    }
}

@Composable
private fun SingleChart(
    chart: SingleChartUiModel,
    modifier: Modifier = Modifier,
) {
    val outlineChartThin = AppDimension.Border.medium.toPx

    val chartColor = MaterialTheme.colorScheme.primary
        .copy(
            alpha = 0.1f,
        )
    val axisColor = MaterialTheme.colorScheme.onSecondaryContainer

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
            .background(MaterialTheme.colorScheme.surfaceContainer),
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
    chart: SingleChartUiModel,
): List<Offset> {
    val maxProperty = chart.properties.maxOfOrNull { it.valueY ?: 0f } ?: 1f
    val propertyK = size.height / maxProperty

    val itemsSize = chart.properties.size
    return chart.properties.mapIndexed { propertyIndex, property ->
        val yValue = if (
            property.valueY == null &&
            (propertyIndex == 0 || propertyIndex == itemsSize.dec())
        ) {
            0f
        } else {
            property.valueY?.let { yValue ->
                size.height - yValue * propertyK
            } ?: Float.NaN
        }
        Offset(
            x = size.width * property.timeX,
            y = yValue,
        )
    }
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

    val nullableFirstX = mutableListOf<Float>()
    val nullableLastX = mutableListOf<Float>()

    points.forEach { point ->
        if (point.y.isNaN() || point.y.isInfinite()) {
            nullableFirstX.add(point.x)
            nullableLastX.add(point.x)
        } else {
            nullableLastX.clear()
        }
    }

    val filteredPoints = points.map { point ->
        val nullableFirst = nullableFirstX.contains(point.x)
        val nullableLast = nullableLastX.contains(point.x)
        if (nullableLast || nullableFirst) {
            Offset(
                x = point.x,
                y = 0f,
            )
        } else {
            point
        }
    }.filter { it.isValid() }

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

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL)
private fun ChartsCanvaWidgetPreview(
    @PreviewParameter(ExerciseChartPreviewParameterProvider::class)
    params: ImmutableList<SingleChartUiModel>,
) {
    AppTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        ) {
            ChartsCanvaWidget(
                charts = params,
                pagerState = rememberPagerState { 1 },
            )
        }
    }
}
