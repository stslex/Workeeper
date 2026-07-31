// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlin.math.abs
import kotlin.math.min

/**
 * The `gel` stretch, shared by every sliding indicator that moves at constant width.
 *
 * Extracted from `AppNavBar` when the chart tabs took it too (§26, `.tabs` gel row). One formula
 * with two call sites rather than two copies: the coefficient is a ledger value, and a copy is how
 * a ledger value quietly becomes two.
 *
 * ## What the coefficient means, and why it transfers here
 *
 * `k = |Δ| / trackWidth`, clamped to 1, and the peak is `1 + [INDICATOR_STRETCH] × k` — so the
 * stretch is proportional to **how far the indicator jumped as a fraction of its own track**, not
 * to an absolute distance. That is what makes it portable between indicators of different size,
 * and it is why the same 0.30 lands within 0.2 percentage points on both of this app's tracks:
 *
 * | | track | item | neighbour peak | two-step peak |
 * |---|---|---|---|---|
 * | `AppNavBar` pill | 411.4dp | 129.1dp | +9.71% (12.5dp) | +19.41% (25.1dp) |
 * | `MetricTabs` thumb | 379.3dp | 121.1dp | +9.89% (12.0dp) | +19.79% (24.0dp) |
 *
 * Both are three equal-width stops, so `pitch / track` is ~1/3 and ~2/3 on each. **Transferable is
 * not identical** — the tabs' item is 8dp narrower, so the same percentage is ~1dp less drawn
 * width. A track with unequal or differently-many stops would not land here, and the numbers above
 * would have to be recomputed rather than assumed.
 *
 * ## The restriction this carries
 *
 * §26: gel is available to an indicator that moves at **constant size**, and unavailable to one
 * that resizes — a `scaleX` excursion has to be the only width change in the frame or it stops
 * reading as "how far it jumped". Both current callers qualify: measured frame by frame on device,
 * `MetricTabs`' thumb is 318px in every frame of a two-step jump.
 *
 * @param travelMillis the transit's own duration; the gel runs on the same clock.
 * @param selectedIndex the stop the indicator is travelling to. Drives the effect; the animation
 *  restarts on every change.
 * @param itemPitch centre-to-centre distance between adjacent stops.
 * @param trackWidth the OUTER track width — the denominator `k` is taken against. Passing the
 *  padded inner width instead inflates every peak.
 * @return the `scaleX` to hand to a `graphicsLayer`, and the origin to pin it by. The origin is
 *  the LEADING edge, so the tail lags and catches up.
 */
@Composable
fun rememberIndicatorGel(
    selectedIndex: Int,
    itemPitch: Dp,
    trackWidth: Dp,
    travelMillis: Int,
    peakMillis: Int = (travelMillis * GEL_PEAK_FRACTION).toInt(),
): IndicatorGel {
    val easing = AppUi.motion.out
    val stretch = remember { Animatable(1f) }
    // Seeded with the initial selection so first composition does not read 0 -> n as travel and
    // fire a stretch nobody asked for. A settled indicator animates nothing.
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    // LATCHED, not derived: the origin belongs to the jump in flight, and the jump's delta is gone
    // from the state as soon as `previousIndex` catches up. Recomputing it during composition
    // would flip the origin part-way through and stretch from the wrong edge for the rest.
    var origin by remember { mutableStateOf(LEADING_EDGE_RIGHT) }

    LaunchedEffect(selectedIndex) {
        val jumped = selectedIndex - previousIndex
        previousIndex = selectedIndex
        if (jumped == 0) return@LaunchedEffect
        origin = if (jumped > 0) LEADING_EDGE_RIGHT else LEADING_EDGE_LEFT
        val peak = indicatorStretchPeak(itemPitch * abs(jumped), trackWidth)
        stretch.snapTo(1f)
        stretch.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = travelMillis
                1f at 0 using easing
                peak at peakMillis using easing
                1f at travelMillis
            },
        )
    }
    return IndicatorGel(stretch.asState(), origin)
}

/** [scale] for `graphicsLayer`, pinned by [origin]. */
data class IndicatorGel(
    val scale: State<Float>,
    val origin: TransformOrigin,
)

/** `1 + 0.30·k`, `k = |Δ| / trackWidth` clamped to 1. Pure, so the peaks above are assertable. */
fun indicatorStretchPeak(travel: Dp, trackWidth: Dp): Float {
    if (trackWidth.value <= 0f) return 1f
    val k = min(abs(travel.value) / trackWidth.value, 1f)
    return 1f + INDICATOR_STRETCH * k
}

/** §26 "Nav pill motion". A ledger value — moving it is a ledger decision, not a tuning. */
const val INDICATOR_STRETCH: Float = 0.30f

/** The `gel` keyframe peaks at 42% of the travel. */
const val GEL_PEAK_FRACTION: Float = 0.42f

private const val ORIGIN_VERTICAL_CENTRE = 0.5f
private val LEADING_EDGE_RIGHT = TransformOrigin(1f, ORIGIN_VERTICAL_CENTRE)
private val LEADING_EDGE_LEFT = TransformOrigin(0f, ORIGIN_VERTICAL_CENTRE)
