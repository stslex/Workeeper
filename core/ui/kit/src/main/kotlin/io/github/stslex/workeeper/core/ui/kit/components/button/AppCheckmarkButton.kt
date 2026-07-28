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

/**
 * The set's done-marker — the mockup's `.mark` (`session-v3f.html`), specified exactly.
 *
 * |            | resting                          | done                                    |
 * |------------|----------------------------------|-----------------------------------------|
 * | shape      | circle, 38dp (`inset: 4px`)      | squircle **radius 13dp**, 42dp (`inset: 2px`) |
 * | fill       | transparent                      | `max` — or `molten.solid` when it is a record |
 * | ring       | 2dp, the `hair-s` tier           | 2dp, the same colour as the fill        |
 * | tick       | present but **fully undrawn**    | **strokes itself in** over 260ms, 60ms delay |
 * | tick hue   | —                                | `base` — the page colour, on the filled plate |
 * | pressed    | `scale(.9)`                      | `scale(.9)`                             |
 *
 * **The morph is circle to rounded-square with the checkmark drawing in.** Not a checkbox, not a
 * colour swap. The previous implementation kept `CircleShape` in both states, drew a static
 * `Icons.Filled.Check`, and ringed the resting state in `accent` — every part of which was wrong,
 * and the accent ring in particular said "actionable chip" where the mockup says "quiet outline
 * waiting to be filled".
 *
 * ## Two deliberate deviations from the drawing, both measured
 *
 * 1. **The resting ring is [AppColors.borderStrong], not literal `hair-s`.** `hair-s` (#2B333B /
 *    #D2D7DD) measures 1.12–1.52:1 against every surface in this palette, and an unchecked mark's
 *    ring carries the entire affordance — there is no fill and no label inside it — so WCAG 1.4.11
 *    applies at 3:1. `borderStrong` *is* `hair-s` moved by the smallest step that clears 3:1
 *    everywhere, and its KDoc names "the unchecked set-mark ring" as the case that forced it. This
 *    is that call site.
 * 2. **A record's tick is `molten.onSolid`, not `onAccent`.** The mockup paints the tick `--base`
 *    unconditionally, which is right in dark, where `molten.onSolid` *is* `base`. In light it is
 *    not: `base` on `molten.solid` #F97316 measures 2.61:1, which is the exact trap `onSolid`
 *    exists to close — see [io.github.stslex.workeeper.core.ui.kit.theme.MoltenAccent.onSolid].
 *
 * ## Where the curves split
 *
 * Geometry — the size and corner radius — rides `closedFraction`, which is `spring` and
 * legitimately overshoots past 1.0; the canvas is sized to 46dp so the overshoot has room rather
 * than clipping. Every colour and the tick's own progress ride `out`. The press scale is `spring`
 * too: it is geometry.
 */
@Composable
fun AppCheckmarkButton(
    isDone: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isRecord: Boolean = false,
) {
    // §9's merged automaton. `isRecord` is a parameter to the same motion, never a second
    // path: the morph and its timing are identical, and only the accent resolves differently.
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
        targetValue = if (isDone && enabled) plate else Color.Transparent,
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
            .size(TOUCH_SIZE)
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

/**
 * The mark's pixels, as a **pure function of its progress values**.
 *
 * Stateless on purpose. A transient captured as a static frame is unfalsifiable by eye (§10.2), so
 * the golden has to be able to ask for a specific frame — and it can only do that if the frame is
 * an argument rather than something a frame clock decides. `SetDoneMarkGoldenTest` drives this
 * directly at rest, mid-transition and done; `AppCheckmarkButton` drives it from the automaton.
 */
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
        // Inset by half the stroke so the ring sits INSIDE the shape, as a CSS `border` does,
        // rather than straddling its edge the way a centred stroke would.
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
 * `stroke-dasharray: 26; stroke-dashoffset: 26 -> 0`, reproduced with [PathMeasure] rather than a
 * dash effect: measuring the real path and drawing the first [progress] of it is what
 * `stroke-dashoffset` means, and it does not depend on the mockup's `26` happening to exceed the
 * path's actual length (it does — the path measures ~22.3 viewBox units).
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

/** The touch target. The mockup's `.mark` is 46px; 48dp is the rung and the minimum target. */
private val TOUCH_SIZE: Dp = 48.dp

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

/*
 * The mockup's tick path, verbatim: `M4 12.5 l5 5 L20 7`, in [TICK_VIEWBOX] units.
 * Named rather than inlined because these are three points of one glyph, not three magic
 * numbers — moving any of them changes the shape of the checkmark.
 */

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
