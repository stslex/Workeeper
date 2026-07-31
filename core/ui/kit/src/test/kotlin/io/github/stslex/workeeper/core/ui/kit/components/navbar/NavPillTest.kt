// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.navbar

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The nav pill's motion, asserted directly — **because nothing else in the repository can see it.**
 *
 * §27: a golden image gates only what one static frame contains. `NavBarGoldenTest` photographs the
 * bar at rest with the pill at two different stops, which pins the offset *arithmetic* and nothing
 * about the travel. Every number below lands strictly between two settled frames:
 *
 * - the 340ms duration — a frame has no duration;
 * - the `gel` peak at 42% of it — a settled bar is at `scaleX = 1`, at both ends;
 * - the peak's *size*, which depends on how far the pill jumped — gone from the state by the time
 *   the animation ends;
 * - the leading-edge origin, which is only observable while `scaleX != 1`.
 *
 * That is four facts, zero of which a picture contains, which is why the stretch was extracted into
 * a pure `navPillStretchPeak` rather than computed inline: the extraction is what makes it
 * assertable. Same shape as `LIST_BOTTOM_CLEARANCE`/`AllTrainingsClearanceTest` and
 * `pagingTailKind`/`PagingTailKindTest`.
 */
internal class NavPillTest {

    @Test
    @DisplayName("the travel is the ledger's 340ms, not a motion-scale rung")
    fun travelDuration() {
        assertEquals(340, NAV_PILL_TRAVEL)
        // The negative control, and it is the point of this case rather than decoration. §26 "Nav
        // pill motion" records 340 explicitly, and 340 is on NO rung of the three-value motion
        // scale (140/260/520) and is not `continuityPositionalSpec`'s `base` either. Anyone
        // "tidying" this onto the scale is making a ledger change, and this assertion is what
        // turns that into a red build instead of a silent 80ms.
        assertTrue(NAV_PILL_TRAVEL !in setOf(140, 260, 520)) {
            "340ms is a recorded ledger value for this member; snapping it onto the motion scale " +
                "is a decision, not a cleanup."
        }
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
        // Pinned against the LITERAL from `nbPick()` — `(1+0.30*k)` — and not against
        // `NAV_PILL_STRETCH`, which is the value under test.
        //
        // **This case exists because its absence was measured.** The first version of this file
        // expressed every expected peak as `1f + NAV_PILL_STRETCH * k`, which reads like a precise
        // assertion and is a tautology: mutating the coefficient 0.30 -> 0.40 moved the production
        // value and the expectation together, and **1 of 6 cases went red** — the clamp case, and
        // only because it happened to carry a hard `1.30f`. That is §27's "assert on the object the
        // production path resolves, not on the token it was built from" arriving one level down: a
        // constant asserted against itself certifies nothing at all.
        assertEquals(0.30f, NAV_PILL_STRETCH, TOLERANCE)
    }

    @Test
    @DisplayName("peak scales with distance jumped: neighbour weak, across strong")
    fun stretchScalesWithDistance() {
        // The drawing's own claim, in its own words: "соседняя вкладка тянется слабо, через одну —
        // заметно". On a three-item bar at the mockup's 452px shell the pitch is ~145px, so a
        // one-step jump is k ~ 0.32 and a two-step is k ~ 0.64.
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
        // The case the component short-circuits on (`jumped == 0`), asserted anyway because the
        // absence of motion is exactly the outcome a missing branch produces by accident.
        assertEquals(1f, navPillStretchPeak(travel = 0.dp, barWidth = 452.dp), TOLERANCE)
    }

    @Test
    @DisplayName("the peak is clamped at k = 1, so 1.30 is the ceiling")
    fun stretchIsClamped() {
        // `Math.min(Math.abs(dx)/bar.offsetWidth, 1)` in `nbPick()`. Unreachable on a real
        // three-item bar — the furthest jump is two pitches, well under the full width — so it is
        // asserted rather than trusted: an unclamped formula and a clamped one are identical on
        // every input the app can produce, which is precisely why the clamp would survive being
        // deleted.
        val ceiling = navPillStretchPeak(travel = 1000.dp, barWidth = 400.dp)
        assertEquals(1.30f, ceiling, TOLERANCE)
    }

    @Test
    @DisplayName("a zero-width bar does not divide by zero")
    fun degenerateBarWidth() {
        // Reachable for one composition: `BoxWithConstraints` can report 0 before the first
        // measure pass. A NaN scaleX blanks the pill rather than mis-sizing it, so the frame it
        // would ruin is the first one the user sees.
        assertEquals(1f, navPillStretchPeak(travel = 100.dp, barWidth = 0.dp), TOLERANCE)
    }
}

private const val TOLERANCE = 0.0001f
