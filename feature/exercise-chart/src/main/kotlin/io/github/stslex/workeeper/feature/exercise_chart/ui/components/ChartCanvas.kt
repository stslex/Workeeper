// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The mockup's `.chartwrap` — extraction §4.6, normative, transcribed from `draw()`:
 *
 * - geometry: fixed height 212, PADT 16 / PADB 24, `xs(i) = (i/(n-1))*(W-10)+5` —
 *   **index-spaced, not date-spaced** (the render window is the point list itself);
 *   `ys` normalises to the visible min/max, `(mx-mn)||1` guarding the all-equal case
 *   at the mockup's own answer (a flat run on the bottom line);
 * - exactly four horizontal gridlines in `--grid`, full width, no axes;
 * - one series: `--max`, 2.2 → 2dp, round caps and joins, no fill;
 * - points: plain r4 donut (fill `--base`, stroke `--max` 2dp); record fill
 *   `--molten-solid` / stroke `--base` at 2.5 — the roles invert; active r5.5 fills
 *   `--max`; a record that is active stays molten;
 * - scrub line: vertical at the active x, `--dim`, 1px, dash `3 4`, running past the
 *   plot band (PADT−8 → H−PADB+6), drawn UNDER the series (grid → scrub → series → pts);
 * - interaction: pointer capture on down, scrub while pressed, snapping to the NEAREST
 *   index — the haptic tick per crossed point lives in the handler (dedup on index);
 * - metric switch: every value tweens old → new and the line morphs (the mockup's 420ms
 *   ease-out-cubic; the motion scale has no 420 rung — `slow`/`out`, reported). The
 *   morph runs only when the day buckets are unchanged, which is exactly a metric flip.
 */
@Composable
internal fun ChartCanvas(
    points: ImmutableList<ChartPointUiModel>,
    activeIndex: Int?,
    recordIndex: Int?,
    onScrub: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = AppUi.colors.grid
    val scrubColor = AppUi.colors.textDim
    val seriesColor = AppUi.colors.textPrimary
    val baseColor = AppUi.colors.surfaceTier0
    val recordColor = AppUi.colors.record.solid

    // The metric-switch morph: when a reload lands on the SAME day buckets (a metric flip),
    // values tween old → new and the line morphs instead of jumping. First composition and
    // every bucket change snap — deterministic under the golden harness.
    val morph = remember { Animatable(1f) }
    var morphFrom by remember { mutableStateOf(listOf<Double>()) }
    var lastPoints by remember { mutableStateOf(points) }
    val slowMillis = AppUi.motion.slow
    val outEasing = AppUi.motion.out
    LaunchedEffect(points) {
        val old = lastPoints
        lastPoints = points
        val sameDays = old.map(ChartPointUiModel::day) == points.map(ChartPointUiModel::day)
        val valuesChanged = old.map(ChartPointUiModel::value) != points.map(ChartPointUiModel::value)
        if (sameDays && valuesChanged) {
            morphFrom = old.map(ChartPointUiModel::value)
            morph.snapTo(0f)
            morph.animateTo(1f, tween(durationMillis = slowMillis, easing = outEasing))
        } else {
            morph.snapTo(1f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge)
            .height(CANVAS_HEIGHT)
            .testTag("ChartCanvas")
            .pointerInput(points.size) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    scrubIndexAt(down.position.x, size.width.toFloat(), points.size)
                        ?.let(onScrub)
                    drag(down.id) { change ->
                        change.consume()
                        scrubIndexAt(change.position.x, size.width.toFloat(), points.size)
                            ?.let(onScrub)
                    }
                }
            },
    ) {
        drawGridLines(gridColor)
        if (points.size < 2) return@Canvas

        val values = if (morph.value < 1f && morphFrom.size == points.size) {
            points.mapIndexed { index, point ->
                morphFrom[index] + (point.value - morphFrom[index]) * morph.value
            }
        } else {
            points.map(ChartPointUiModel::value)
        }
        val min = values.min()
        val range = (values.max() - min).takeIf { it > 0.0 } ?: 1.0
        val xs = { index: Int ->
            (index.toFloat() / (points.size - 1)) * (size.width - 2 * X_INSET.toPx()) +
                X_INSET.toPx()
        }
        val ys = { value: Double ->
            PAD_TOP.toPx() + ((1.0 - (value - min) / range) * plotBandPx()).toFloat()
        }
        val plotted = values.mapIndexed { index, value -> Offset(xs(index), ys(value)) }

        // Draw order is the mockup's: grid → scrub → series → points. The scrub sits UNDER
        // the series.
        activeIndex?.takeIf { it in plotted.indices }?.let { index ->
            drawLine(
                color = scrubColor,
                start = Offset(plotted[index].x, PAD_TOP.toPx() - SCRUB_OVERSHOOT_TOP.toPx()),
                end = Offset(
                    plotted[index].x,
                    size.height - PAD_BOTTOM.toPx() + SCRUB_OVERSHOOT_BOTTOM.toPx(),
                ),
                strokeWidth = AppDimension.Border.small.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(SCRUB_DASH_ON.toPx(), SCRUB_DASH_OFF.toPx()),
                ),
            )
        }

        val seriesPath = Path().apply {
            plotted.forEachIndexed { index, offset ->
                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
            }
        }
        drawPath(
            path = seriesPath,
            color = seriesColor,
            style = Stroke(
                width = AppDimension.Border.medium.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        plotted.forEachIndexed { index, center ->
            val isRecord = index == recordIndex
            val isActive = index == activeIndex
            val radius = if (isActive) ACTIVE_POINT_RADIUS.toPx() else POINT_RADIUS.toPx()
            when {
                // The record's roles invert: solid molten disc ringed by the page colour —
                // and it STAYS molten when active (`.pt.pr.act` keeps the molten fill).
                isRecord -> {
                    drawCircle(color = recordColor, radius = radius, center = center)
                    drawCircle(
                        color = baseColor,
                        radius = radius,
                        center = center,
                        style = Stroke(width = RECORD_STROKE.toPx()),
                    )
                }

                isActive -> drawCircle(color = seriesColor, radius = radius, center = center)

                else -> {
                    drawCircle(color = baseColor, radius = radius, center = center)
                    drawCircle(
                        color = seriesColor,
                        radius = radius,
                        center = center,
                        style = Stroke(width = AppDimension.Border.medium.toPx()),
                    )
                }
            }
        }
    }
}

/** Four horizontal lines at `y = PADT + k*(H-PADT-PADB)/3`, k = 0..3, full width, no axes. */
private fun DrawScope.drawGridLines(color: Color) {
    val band = plotBandPx()
    repeat(GRID_LINE_COUNT) { k ->
        val y = PAD_TOP.toPx() + (k * band / (GRID_LINE_COUNT - 1)).toFloat()
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = AppDimension.Border.small.toPx(),
        )
    }
}

private fun DrawScope.plotBandPx(): Double =
    (size.height - PAD_TOP.toPx() - PAD_BOTTOM.toPx()).toDouble()

/**
 * The mockup's `scrub()`: normalise x over the full inner width and snap to the NEAREST
 * index (`Math.round`). The 5-unit series inset is deliberately ignored, as the mockup's
 * own scrub ignores it.
 */
private fun scrubIndexAt(x: Float, width: Float, pointCount: Int): Int? {
    if (pointCount < 2 || width <= 0f) return null
    val fraction = (x / width).coerceIn(0f, 1f)
    return (fraction * (pointCount - 1)).roundToInt()
}

// `draw()` geometry, kept literal like stroke widths — these are the mockup's own numbers,
// not ladder values (extraction §4.6): H=212, PADT=16, PADB=24, xs inset 5.
private val CANVAS_HEIGHT: Dp = 212.dp
private val PAD_TOP: Dp = 16.dp
private val PAD_BOTTOM: Dp = 24.dp
private val X_INSET: Dp = 5.dp
private val POINT_RADIUS: Dp = 4.dp
private val ACTIVE_POINT_RADIUS: Dp = 5.5.dp
private val RECORD_STROKE: Dp = 2.5.dp
private val SCRUB_OVERSHOOT_TOP: Dp = 8.dp
private val SCRUB_OVERSHOOT_BOTTOM: Dp = 6.dp
private val SCRUB_DASH_ON: Dp = 3.dp
private val SCRUB_DASH_OFF: Dp = 4.dp
private const val GRID_LINE_COUNT = 4

@Preview
@Composable
private fun ChartCanvasDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(vertical = AppDimension.Space.lg),
        ) {
            ChartCanvas(
                points = previewPoints(),
                activeIndex = 2,
                recordIndex = 3,
                onScrub = {},
            )
        }
    }
}

@Preview
@Composable
private fun ChartCanvasLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(vertical = AppDimension.Space.lg),
        ) {
            ChartCanvas(
                points = previewPoints(),
                activeIndex = 3,
                recordIndex = 3,
                onScrub = {},
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
