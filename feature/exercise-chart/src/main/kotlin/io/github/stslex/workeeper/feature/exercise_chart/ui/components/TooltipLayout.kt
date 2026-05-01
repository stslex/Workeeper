// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max

/**
 * Pure layout decision for [ChartTooltipPopup]: where the card sits, whether the tooltip
 * flips below the anchor, and whether/where the notch should be drawn.
 *
 * Extracted from the Compose layout block so the placement math (especially the
 * "skip the notch when its ideal position falls inside the card's rounded-corner zone"
 * rule) can be unit-tested without spinning up the SubcomposeLayout.
 */
@Immutable
internal data class TooltipLayout(
    val cardLeft: Float,
    val cardTop: Float,
    /** `true` when the card sits below the anchor — top-edge guard kicked in. */
    val flipBelow: Boolean,
    /** `false` when the notch would intersect the card's corner curve; render none then. */
    val showNotch: Boolean,
    /**
     * Notch's centre X expressed in **card-local** coordinates (0..[TooltipLayoutSpec]
     * card width). Meaningful only when [showNotch]; ignored otherwise. Caller computes
     * the placement origin via `cardLeft + notchOffsetX - notchWidth / 2`.
     */
    val notchOffsetX: Float,
)

/**
 * Density-converted constants used by [computeTooltipLayout]. Pulled into a struct so the
 * function fits within the project's `LongParameterList` budget and tests can build a
 * single fixture.
 *
 * @param cornerRadiusPx must match the radius of the card's rounded corner shape — the
 *   notch's safe-zone is computed from this. Mismatch will either over-clip the safe zone
 *   (notch hides too eagerly) or under-clip it (notch slides into the corner curve).
 */
@Immutable
internal data class TooltipLayoutSpec(
    val anchorGapPx: Float,
    val edgeMarginPx: Float,
    val notchHeightPx: Float,
    val cornerRadiusPx: Float,
    val notchSafeMarginPx: Float,
)

internal fun computeTooltipLayout(
    anchor: Offset,
    canvasSize: Size,
    cardWidth: Float,
    cardHeight: Float,
    spec: TooltipLayoutSpec,
): TooltipLayout {
    val aboveTop = anchor.y - cardHeight - spec.anchorGapPx - spec.notchHeightPx
    val flipBelow = aboveTop < spec.edgeMarginPx
    val cardTop = if (flipBelow) {
        anchor.y + spec.anchorGapPx + spec.notchHeightPx
    } else {
        aboveTop
    }

    val centeredLeft = anchor.x - cardWidth / 2f
    val maxLeft = max(spec.edgeMarginPx, canvasSize.width - cardWidth - spec.edgeMarginPx)
    val cardLeft = centeredLeft.coerceIn(spec.edgeMarginPx, maxLeft)

    val notchOffsetX = anchor.x - cardLeft
    val notchSafeMin = spec.cornerRadiusPx + spec.notchSafeMarginPx
    val notchSafeMax = cardWidth - spec.cornerRadiusPx - spec.notchSafeMarginPx
    // When the card is narrower than two corner-radii plus margins, the safe zone
    // collapses (max < min) — there is nowhere to put the notch without intersecting a
    // corner, so suppress it.
    val showNotch = notchSafeMin <= notchSafeMax && notchOffsetX in notchSafeMin..notchSafeMax

    return TooltipLayout(
        cardLeft = cardLeft,
        cardTop = cardTop,
        flipBelow = flipBelow,
        showNotch = showNotch,
        notchOffsetX = notchOffsetX,
    )
}
