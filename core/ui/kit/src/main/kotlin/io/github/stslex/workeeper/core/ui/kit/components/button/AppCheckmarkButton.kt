package io.github.stslex.workeeper.core.ui.kit.components.button

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.motion.rememberSetClosureVisuals
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.fadedOut

/**
 * The set's done-marker (`.mark`): a circle morphing to a squircle as the tick strokes itself in.
 * Geometry rides `spring`, colours and the tick `out`. See documentation/design-system.md.
 */
@Composable
fun AppCheckmarkButton(
    isDone: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isRecord: Boolean = false,
) {
    // §9's merged automaton: `isRecord` parameterises the same motion, never a second path.
    val closure = rememberSetClosureVisuals(isDone = isDone, isRecord = isRecord)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // `.mark:active .shape{transform:scale(.9)}` — geometry, so `spring`.
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.spring),
        label = "setMark-pressScale",
    )
    val plate = if (isRecord) closure.accent else AppUi.colors.accent
    val fill by animateColorAsState(
        targetValue = if (isDone && enabled) plate else plate.fadedOut(),
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "setMark-fill",
    )
    val restingRing = if (enabled) AppUi.colors.borderStrong else AppUi.colors.borderDefault
    val ring by animateColorAsState(
        targetValue = if (isDone && enabled) plate else restingRing,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "setMark-ring",
    )
    val tick = if (isRecord) AppUi.colors.molten.onSolid else AppUi.colors.onAccent
    val state = if (isDone) ToggleableState.On else ToggleableState.Off

    Box(
        modifier = modifier
            .size(AppCheckmarkButtonTouchSize)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle,
            )
            .semantics {
                toggleableState = state
                stateDescription = if (isDone) "completed" else "pending"
            },
        contentAlignment = Alignment.Center,
    ) {
        SetDoneMark(
            closedFraction = closure.closedFraction,
            tickProgress = closure.tickProgress,
            fill = fill,
            ring = ring,
            tick = tick,
            scale = scale,
        )
    }
}

/** The mark's pixels as a pure function of its progress values, so a golden can ask for a frame. */
@Composable
internal fun SetDoneMark(
    closedFraction: Float,
    tickProgress: Float,
    fill: Color,
    ring: Color,
    tick: Color,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    Canvas(
        modifier = modifier
            .size(MARK_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        val side = lerp(SHAPE_REST.toPx(), SHAPE_DONE.toPx(), closedFraction)
        val radius = lerp(REST_RADIUS.toPx(), DONE_RADIUS.toPx(), closedFraction)
        val strokeWidth = RING_WIDTH.toPx()
        val origin = (size.width - side) / 2f

        drawRoundRect(
            color = fill,
            topLeft = Offset(origin, origin),
            size = Size(side, side),
            cornerRadius = CornerRadius(radius, radius),
        )
        // Inset by half the stroke so the ring sits INSIDE the shape, as a CSS `border` does.
        drawRoundRect(
            color = ring,
            topLeft = Offset(origin + strokeWidth / 2f, origin + strokeWidth / 2f),
            size = Size(side - strokeWidth, side - strokeWidth),
            cornerRadius = CornerRadius(radius - strokeWidth / 2f, radius - strokeWidth / 2f),
            style = Stroke(width = strokeWidth),
        )
        drawTick(progress = tickProgress, color = tick)
    }
}

/**
 * `stroke-dashoffset` via [PathMeasure]: the mockup's dasharray 26 exceeds the path's real length
 * (~22.3 viewBox units), so a dash effect would not map onto it.
 */
private fun DrawScope.drawTick(progress: Float, color: Color) {
    if (progress <= 0f) return
    val unit = TICK_SIZE.toPx() / TICK_VIEWBOX
    val originX = (size.width - TICK_SIZE.toPx()) / 2f
    val originY = (size.height - TICK_SIZE.toPx()) / 2f
    val full = Path().apply {
        moveTo(originX + TICK_START_X * unit, originY + TICK_START_Y * unit)
        lineTo(originX + TICK_ELBOW_X * unit, originY + TICK_ELBOW_Y * unit)
        lineTo(originX + TICK_TIP_X * unit, originY + TICK_TIP_Y * unit)
    }
    val measure = PathMeasure().apply { setPath(full, false) }
    val drawn = Path()
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), drawn, true)
    drawPath(
        path = drawn,
        color = color,
        style = Stroke(
            width = TICK_STROKE_VIEWBOX * unit,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * The touch target, and the mark's SLOT in the set row: `SetColumnHeader`'s trailing gutter
 * mirrors this number rather than a copy of it.
 */
val AppCheckmarkButtonTouchSize: Dp = 48.dp

/** `.mark{width:46px;height:46px}` — also the room `closedFraction`'s overshoot needs. */
private val MARK_SIZE: Dp = 46.dp

/** `.mark .shape{inset:4px}` on a 46px mark. */
private val SHAPE_REST: Dp = 38.dp

/** `.set.done .mark .shape{inset:2px}` on a 46px mark. */
private val SHAPE_DONE: Dp = 42.dp

/** `border-radius: 50%` of [SHAPE_REST] — a circle. */
private val REST_RADIUS: Dp = 19.dp

/** `.set.done .mark .shape{border-radius:13px}` — the squircle. */
private val DONE_RADIUS: Dp = 13.dp

/** `.mark .shape{border:2px solid}`. */
private val RING_WIDTH: Dp = 2.dp

/** `.mark svg{width:19px;height:19px}`. */
private val TICK_SIZE: Dp = 19.dp

/** The SVG's coordinate system; the path is authored in it and scaled to [TICK_SIZE]. */
private const val TICK_VIEWBOX = 24f

/** `M4 12.5` — where the short descending stroke starts. */
private const val TICK_START_X = 4f
private const val TICK_START_Y = 12.5f

/** `l5 5` — the elbow at the bottom of the V. */
private const val TICK_ELBOW_X = 9f
private const val TICK_ELBOW_Y = 17.5f

/** `L20 7` — the tip of the long ascending stroke. */
private const val TICK_TIP_X = 20f
private const val TICK_TIP_Y = 7f

/** `.mark svg{stroke-width:2.7}`, in viewBox units — 2.14dp once scaled to [TICK_SIZE]. */
private const val TICK_STROKE_VIEWBOX = 2.7f

/** `.mark:active .shape{transform:scale(.9)}`. */
private const val PRESSED_SCALE = 0.9f

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppCheckmarkButtonPreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier1)
                .padding(AppDimension.Space.lg),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppCheckmarkButton(isDone = false, enabled = true, onToggle = {})
            AppCheckmarkButton(isDone = true, enabled = true, onToggle = {})
            AppCheckmarkButton(isDone = true, enabled = true, isRecord = true, onToggle = {})
            AppCheckmarkButton(isDone = false, enabled = false, onToggle = {})
            AppCheckmarkButton(isDone = true, enabled = false, onToggle = {})
        }
    }
}
