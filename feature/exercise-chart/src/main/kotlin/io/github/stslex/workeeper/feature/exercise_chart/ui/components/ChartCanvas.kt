// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
 * - dataset changes: the drawn dataset is never swapped. Every point is a member of
 *   [ChartPointsAnimator], whose normalised x/y are animated state, and a new dataset
 *   RETARGETS them — see that class for the enter/exit policy and for why the old
 *   value-space morph cancelled itself. One path, three triggers: the metric tabs, the
 *   preset chips and the exercise picker all reach it through `State.points`.
 * - motion: `out` at `base` for the data, because overshoot on value-encoding geometry
 *   would draw a reading the data never contained. The mockup's 420ms and the previous
 *   `slow` (520ms) both go; the scale's own default carries it.
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

    val targets = points.toTargets()
    val scope = rememberCoroutineScope()
    val dataSpec: AnimationSpec<Float> = tween(
        durationMillis = AppUi.motion.base,
        easing = AppUi.motion.out,
    )
    // Seeded at rest by its constructor, then only ever retargeted. `remember` without keys
    // is deliberate: this object outlives every dataset, which is the point of it.
    val animator = remember { ChartPointsAnimator(scope, dataSpec, targets) }
    LaunchedEffect(points) { animator.retarget(targets, animate = true) }

    // Identity, not position: the record and the scrubbed point are resolved by day key, so
    // they survive a retarget that moves every index (a preset change) without the flags
    // sliding onto the wrong disc mid-animation.
    val activeKey = activeIndex?.let { points.getOrNull(it)?.dayMillis }
    val recordKey = recordIndex?.let { points.getOrNull(it)?.dayMillis }

    // The scrub bar encodes no value — it marks which point is being read — so it may glide
    // on its own clock (`fast`, and `spring` would be legal here too). It first glides to a
    // newly selected point, then tracks that point exactly while the data retargets under it.
    val scrubX = remember { Animatable(targets.firstOrNull { it.key == activeKey }?.x ?: 0f) }
    val scrubSpec: AnimationSpec<Float> = tween(
        durationMillis = AppUi.motion.fast,
        easing = AppUi.motion.out,
    )
    LaunchedEffect(activeKey, animator) {
        val point = animator.series.firstOrNull { it.key == activeKey } ?: return@LaunchedEffect
        // Glide to the newly selected point (a no-op on first composition, where the bar is
        // already seeded there), then follow that point exactly while the data retargets.
        scrubX.animateTo(point.x.value, scrubSpec)
        snapshotFlow { point.x.value }.collect { x -> scrubX.snapTo(x) }
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
        val series = animator.series
        if (series.size < 2) return@Canvas

        // The ONLY arithmetic left in the draw phase: normalised → pixels. No domain, no
        // interpolation, no dataset — every position below is read from animated state.
        val xPx = { x: Float -> x * (size.width - 2 * X_INSET.toPx()) + X_INSET.toPx() }
        val yPx = { y: Float -> PAD_TOP.toPx() + (y.toDouble() * plotBandPx()).toFloat() }

        // Draw order is the mockup's: grid → scrub → series → points. The scrub sits UNDER
        // the series.
        if (activeKey != null && series.any { it.key == activeKey }) {
            val x = xPx(scrubX.value)
            drawLine(
                color = scrubColor,
                start = Offset(x, PAD_TOP.toPx() - SCRUB_OVERSHOOT_TOP.toPx()),
                end = Offset(x, size.height - PAD_BOTTOM.toPx() + SCRUB_OVERSHOOT_BOTTOM.toPx()),
                strokeWidth = AppDimension.Border.small.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(SCRUB_DASH_ON.toPx(), SCRUB_DASH_OFF.toPx()),
                ),
            )
        }

        val seriesPath = Path().apply {
            series.forEachIndexed { index, point ->
                val x = xPx(point.x.value)
                val y = yPx(point.y.value)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
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

        // `drawn`, not `series`: a point on its way out keeps its disc until it has faded.
        animator.drawn.forEach { point ->
            val presence = point.presence.value
            if (presence <= 0f) return@forEach
            val center = Offset(xPx(point.x.value), yPx(point.y.value))
            val isRecord = point.key == recordKey
            val isActive = point.key == activeKey
            val radius =
                (if (isActive) ACTIVE_POINT_RADIUS.toPx() else POINT_RADIUS.toPx()) * presence
            when {
                // The record's roles invert: solid molten disc ringed by the page colour —
                // and it STAYS molten when active (`.pt.pr.act` keeps the molten fill).
                isRecord -> {
                    drawCircle(
                        color = recordColor,
                        radius = radius,
                        center = center,
                        alpha = presence,
                    )
                    drawCircle(
                        color = baseColor,
                        radius = radius,
                        center = center,
                        alpha = presence,
                        style = Stroke(width = RECORD_STROKE.toPx()),
                    )
                }

                isActive -> drawCircle(
                    color = seriesColor,
                    radius = radius,
                    center = center,
                    alpha = presence,
                )

                else -> {
                    drawCircle(
                        color = baseColor,
                        radius = radius,
                        center = center,
                        alpha = presence,
                    )
                    drawCircle(
                        color = seriesColor,
                        radius = radius,
                        center = center,
                        alpha = presence,
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
    ChartPointUiModel(LocalDate.of(2026, 4, 5), 0L, 80.0, 1),
    ChartPointUiModel(LocalDate.of(2026, 4, 12), 0L, 90.0, 1),
    ChartPointUiModel(LocalDate.of(2026, 4, 19), 0L, 95.0, 1),
    ChartPointUiModel(LocalDate.of(2026, 4, 26), 0L, 105.0, 2),
).toImmutableList()
