// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.max

private val ChartAxisPadding = AppDimension.Space.md
private val ChartLineWidth = AppDimension.Border.medium
private val ChartPointRadius = AppDimension.Padding.small
private val TapTargetRadius = AppDimension.iconMd

private const val CHART_ASPECT_RATIO = 16f / 9f
private const val SINGLE_POINT_PAD = 1.0

@Suppress("LongParameterList")
@Composable
internal fun ChartCanvas(
    points: ImmutableList<ChartPointUiModel>,
    activeTooltip: ChartTooltipUiModel?,
    windowStartDay: LocalDate,
    windowEndDay: LocalDate,
    onPointTap: (ChartPointUiModel) -> Unit,
    onCanvasTap: () -> Unit,
    onTooltipTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = AppUi.colors.borderSubtle
    val lineColor = AppUi.colors.accent
    val pointFillColor = AppUi.colors.accent
    val pointStrokeColor = AppUi.colors.surfaceTier0
    val density = LocalDensity.current
    val axisPaddingPx = with(density) { ChartAxisPadding.toPx() }
    val lineWidthPx = with(density) { ChartLineWidth.toPx() }
    val pointRadiusPx = with(density) { ChartPointRadius.toPx() }
    val tapTargetRadiusPx = with(density) { TapTargetRadius.toPx() }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val totalDays = ChronoUnit.DAYS.between(windowStartDay, windowEndDay)
        .coerceAtLeast(1)
        .toFloat()
    val rawMin = points.minOfOrNull { it.value } ?: 0.0
    val rawMax = points.maxOfOrNull { it.value } ?: 0.0
    val (minY, maxY) = if (rawMin == rawMax) {
        // Single-point case (or all-equal) — pad so we get a centred horizontal line
        // instead of a divide-by-zero.
        rawMin - SINGLE_POINT_PAD to rawMax + SINGLE_POINT_PAD
    } else {
        rawMin to rawMax
    }
    val yRange = (maxY - minY).coerceAtLeast(1.0)

    val pixelMap = PointPixelMap(
        canvasSize = canvasSize,
        axisPaddingPx = axisPaddingPx,
        windowStartDay = windowStartDay,
        totalDays = totalDays,
        minY = minY,
        yRange = yRange,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CHART_ASPECT_RATIO)
            .padding(horizontal = AppDimension.screenEdge)
            .testTag("ChartCanvas"),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CHART_ASPECT_RATIO)
                .pointerInput(points, windowStartDay, windowEndDay) {
                    detectTapGestures(
                        onTap = { offset ->
                            val winner = findNearestPoint(
                                points = points,
                                tap = offset,
                                pixelMap = pixelMap,
                                tapRadiusPx = tapTargetRadiusPx,
                            )
                            if (winner != null) onPointTap(winner) else onCanvasTap()
                        },
                    )
                },
        ) {
            canvasSize = size
            drawGrid(axisPaddingPx, gridColor)
            if (points.isEmpty()) return@Canvas

            val plotMap = pixelMap.copy(canvasSize = size)
            val plotPoints = points.map(plotMap::toPx)

            val first = plotPoints.first()
            val last = plotPoints.last()
            val leftEdgeX = axisPaddingPx
            val rightEdgeX = size.width - axisPaddingPx

            val path = Path().apply {
                moveTo(leftEdgeX, first.y)
                lineTo(first.x, first.y)
                plotPoints.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(rightEdgeX, last.y)
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = lineWidthPx))

            plotPoints.forEach { offset ->
                drawCircle(color = pointFillColor, radius = pointRadiusPx, center = offset)
                drawCircle(
                    color = pointStrokeColor,
                    radius = pointRadiusPx / 2f,
                    center = offset,
                )
            }
        }

        // Canvas-local tooltip overlay. Lives inside this Box so it can position itself in
        // the same coordinate space as the Canvas. It must NOT be lifted into the parent
        // Column — that would make it a layout sibling and push the footer down.
        if (activeTooltip != null && canvasSize != Size.Zero) {
            val anchorPoint = points.firstOrNull { it.sessionUuid == activeTooltip.sessionUuid }
            if (anchorPoint != null) {
                ChartTooltipPopup(
                    tooltip = activeTooltip,
                    anchorPx = pixelMap.toPx(anchorPoint),
                    onClick = onTooltipTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(CHART_ASPECT_RATIO),
                )
            }
        }
    }
}

private fun DrawScope.drawGrid(
    axisPaddingPx: Float,
    color: Color,
) {
    val left = axisPaddingPx
    val top = axisPaddingPx
    val right = size.width - axisPaddingPx
    val bottom = size.height - axisPaddingPx
    drawLine(color = color, start = Offset(left, top), end = Offset(left, bottom))
    drawLine(color = color, start = Offset(left, bottom), end = Offset(right, bottom))
}

private fun findNearestPoint(
    points: ImmutableList<ChartPointUiModel>,
    tap: Offset,
    pixelMap: PointPixelMap,
    tapRadiusPx: Float,
): ChartPointUiModel? {
    if (points.isEmpty() || pixelMap.canvasSize == Size.Zero) return null
    var winner: ChartPointUiModel? = null
    var bestDistance = Float.POSITIVE_INFINITY
    points.forEach { point ->
        val projected = pixelMap.toPx(point)
        val distance = max(
            (projected.x - tap.x).absoluteValue,
            (projected.y - tap.y).absoluteValue,
        )
        if (distance < bestDistance && distance <= tapRadiusPx) {
            bestDistance = distance
            winner = point
        }
    }
    return winner
}

@Preview
@Composable
private fun ChartCanvasLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartCanvas(
                points = previewPoints(),
                activeTooltip = null,
                windowStartDay = LocalDate.of(2026, 4, 1),
                windowEndDay = LocalDate.of(2026, 5, 1),
                onPointTap = {},
                onCanvasTap = {},
                onTooltipTap = {},
            )
        }
    }
}

@Preview
@Composable
private fun ChartCanvasDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartCanvas(
                points = previewPoints(),
                activeTooltip = null,
                windowStartDay = LocalDate.of(2026, 4, 1),
                windowEndDay = LocalDate.of(2026, 5, 1),
                onPointTap = {},
                onCanvasTap = {},
                onTooltipTap = {},
            )
        }
    }
}

@Preview
@Composable
private fun ChartCanvasEmptyPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartCanvas(
                points = persistentListOf(),
                activeTooltip = null,
                windowStartDay = LocalDate.of(2026, 4, 1),
                windowEndDay = LocalDate.of(2026, 5, 1),
                onPointTap = {},
                onCanvasTap = {},
                onTooltipTap = {},
            )
        }
    }
}

@Preview
@Composable
private fun ChartCanvasWithTooltipPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartCanvas(
                points = previewPoints(),
                activeTooltip = ChartTooltipUiModel(
                    sessionUuid = "s3",
                    exerciseName = "Bench press",
                    dateLabel = "Apr 19, 2026",
                    displayLabel = "95 kg × 5",
                    setCountLabel = "1 set this day",
                ),
                windowStartDay = LocalDate.of(2026, 4, 1),
                windowEndDay = LocalDate.of(2026, 5, 1),
                onPointTap = {},
                onCanvasTap = {},
                onTooltipTap = {},
            )
        }
    }
}

@Suppress("MagicNumber")
private fun previewPoints(): ImmutableList<ChartPointUiModel> = listOf(
    ChartPointUiModel(LocalDate.of(2026, 4, 5), 0L, 80.0, "s1", 80.0, 5, 1),
    ChartPointUiModel(LocalDate.of(2026, 4, 12), 0L, 90.0, "s2", 90.0, 5, 1),
    ChartPointUiModel(LocalDate.of(2026, 4, 19), 0L, 95.0, "s3", 95.0, 5, 1),
    ChartPointUiModel(LocalDate.of(2026, 4, 26), 0L, 105.0, "s4", 105.0, 3, 2),
).toImmutableList()
