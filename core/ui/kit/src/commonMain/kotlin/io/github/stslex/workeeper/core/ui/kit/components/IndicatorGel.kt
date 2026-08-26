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
 * The `gel` stretch for a sliding indicator that moves at constant width; the peak is
 * `1 + [INDICATOR_STRETCH] × k`, `k = |Δ| / trackWidth`. See the v3 redesign spec §26.
 *
 * @param trackWidth the OUTER track width; passing the padded inner width inflates every peak.
 * @return the `scaleX` for a `graphicsLayer` and the LEADING-edge origin to pin it by.
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
    // Seeded with the initial selection so first composition does not read 0 -> n as travel.
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    // LATCHED, not derived: the origin belongs to the jump in flight, whose delta is soon gone.
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

/** `1 + 0.30·k`, `k = |Δ| / trackWidth` clamped to 1. Pure, so the peak is assertable. */
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
