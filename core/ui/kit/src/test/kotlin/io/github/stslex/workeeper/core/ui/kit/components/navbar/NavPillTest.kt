// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.navbar

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.provideAppMotion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The nav pill's motion, asserted directly: durations, the stretch peak and its origin all land
 * between two settled frames, so no golden can see them. See the v3 redesign spec §26.2.
 */
internal class NavPillTest {

    @Test
    @DisplayName("the pill travels on `travel`, and it is the scale's own instance")
    fun offsetUsesTheTravelCurve() {
        // `assertSame`, so the call site reads the scale rather than an equal-looking local curve.
        val motion = provideAppMotion()
        assertSame(motion.travel, navPillOffsetSpec<Dp>(motion).easing)
        // And the negative: `out` is what this was, and repointing back is the regression.
        assertNotSame(motion.out, navPillOffsetSpec<Dp>(motion).easing)
        assertEquals(NAV_PILL_TRAVEL, navPillOffsetSpec<Dp>(motion).durationMillis)
    }

    @Test
    @DisplayName("the travel is the ledger's 340ms, not a motion-scale rung")
    fun travelDuration() {
        assertEquals(340, NAV_PILL_TRAVEL)
        // 340 is on no rung of the motion scale (140/260/520); moving it there is a ledger change.
        assertTrue(NAV_PILL_TRAVEL !in setOf(140, 260, 520)) {
            "340ms is a recorded ledger value for this member; snapping it onto the motion scale " +
                "is a decision, not a cleanup."
        }
    }

    @Test
    @DisplayName("the tint shares the pill's timeline — one state change, one duration")
    fun tintSharesTheTravel() {
        // One state change, two properties: asserted as an identity so a later divergence goes red.
        assertEquals(NAV_PILL_TRAVEL, NAV_ITEM_TINT_DURATION)
    }

    @Test
    @DisplayName("the stretch peaks at 42% of the travel")
    fun stretchPeakPosition() {
        // `@keyframes gel{0%{1} 42%{--sx} 100%{1}}` at 340ms -> 142.8ms, rounded to 143.
        assertEquals(143, NAV_PILL_STRETCH_PEAK_MS)
        val fraction = NAV_PILL_STRETCH_PEAK_MS.toDouble() / NAV_PILL_TRAVEL
        assertTrue(fraction > 0.41 && fraction < 0.43) {
            "gel peaks at 42% of the travel; measured $fraction"
        }
    }

    @Test
    @DisplayName("the coefficient is the drawn 0.30")
    fun stretchCoefficient() {
        // Pinned against `nbPick()`'s literal `(1+0.30*k)`, not `NAV_PILL_STRETCH`: a constant
        // asserted against itself certifies nothing.
        assertEquals(0.30f, NAV_PILL_STRETCH, TOLERANCE)
    }

    @Test
    @DisplayName("peak scales with distance jumped: neighbour weak, across strong")
    fun stretchScalesWithDistance() {
        // A three-item bar at the mockup's 452px shell has a ~145px pitch: k ~ 0.32 and ~ 0.64.
        val bar = 452.dp
        val pitch = 145.dp

        val neighbour = navPillStretchPeak(travel = pitch, barWidth = bar)
        val across = navPillStretchPeak(travel = pitch * 2, barWidth = bar)

        assertTrue(across > neighbour) {
            "a two-step jump must stretch further than a one-step one: $across vs $neighbour"
        }
        // Literal expectations, computed from the drawing rather than from the constant.
        assertEquals(1f + 0.30f * (145f / 452f), neighbour, TOLERANCE)
        assertEquals(1f + 0.30f * (290f / 452f), across, TOLERANCE)
    }

    @Test
    @DisplayName("a settled pill does not stretch")
    fun noTravelNoStretch() {
        // The short-circuit case (`jumped == 0`); a missing branch produces this by accident too.
        assertEquals(1f, navPillStretchPeak(travel = 0.dp, barWidth = 452.dp), TOLERANCE)
    }

    @Test
    @DisplayName("the peak is clamped at k = 1, so 1.30 is the ceiling")
    fun stretchIsClamped() {
        // `Math.min(Math.abs(dx)/bar.offsetWidth, 1)` in `nbPick()`; unreachable on a real bar.
        val ceiling = navPillStretchPeak(travel = 1000.dp, barWidth = 400.dp)
        assertEquals(1.30f, ceiling, TOLERANCE)
    }

    @Test
    @DisplayName("a zero-width bar does not divide by zero")
    fun degenerateBarWidth() {
        // `BoxWithConstraints` can report 0 before the first measure; NaN would blank the pill.
        assertEquals(1f, navPillStretchPeak(travel = 100.dp, barWidth = 0.dp), TOLERANCE)
    }
}

private const val TOLERANCE = 0.0001f
