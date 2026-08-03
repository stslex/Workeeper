// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The gel peak on **both** tracks, asserted against the device measurement rather than against
 * the formula's own output.
 *
 * §10.4 territory: a golden photographs the indicator at rest, where `scaleX` is exactly 1 at both
 * ends, so the peak is invisible to every image in the repository. The formula is pure precisely
 * so this is reachable.
 *
 * **The numbers here are the drawn ones, transcribed from the measurement, not recomputed.** An
 * expectation derived from [INDICATOR_STRETCH] would move with any mutation of it and assert
 * arithmetic instead of a value — the failure `NavPillTest` already had once.
 */
internal class IndicatorGelTest {

    @Test
    @DisplayName("nav pill: the coefficient lands where the device measured it")
    fun navPillPeaks() {
        // Emulator 1080x2424 @420dpi: track 411.4dp, item 129.1dp, gap 4dp.
        assertEquals(1.0971f, indicatorStretchPeak(NAV_PITCH, NAV_TRACK), TOLERANCE)
        assertEquals(1.1941f, indicatorStretchPeak(NAV_PITCH * 2, NAV_TRACK), TOLERANCE)
    }

    @Test
    @DisplayName("chart tabs: the SAME coefficient, and it does not land in the same place")
    fun chartTabPeaks() {
        // The point of the case. Both tracks are three equal stops, so `k` is close -- but the
        // tabs' track is 32dp narrower, so the peaks are 0.18pp HIGHER, not equal. "Transferable"
        // was the claim; "identical" would have been wrong and this is what discriminates them.
        assertEquals(1.0989f, indicatorStretchPeak(TAB_PITCH, TAB_TRACK), TOLERANCE)
        assertEquals(1.1979f, indicatorStretchPeak(TAB_PITCH * 2, TAB_TRACK), TOLERANCE)
        assertTrue(
            indicatorStretchPeak(TAB_PITCH * 2, TAB_TRACK) >
                indicatorStretchPeak(NAV_PITCH * 2, NAV_TRACK),
        ) { "the tabs' narrower track must give a HIGHER peak for the same coefficient" }
    }

    @Test
    @DisplayName("k is clamped, so a jump wider than the track cannot run the stretch away")
    fun clampsAtFullTrack() {
        // A four- or five-stop track would push |delta| past the width. Without the clamp the peak
        // grows without bound and the indicator inverts; this is the guard, not decoration.
        assertEquals(1f + INDICATOR_STRETCH, indicatorStretchPeak(1000.dp, 100.dp), TOLERANCE)
        assertEquals(1f + INDICATOR_STRETCH, indicatorStretchPeak((-1000).dp, 100.dp), TOLERANCE)
    }

    @Test
    @DisplayName("a settled indicator does not stretch, and a zero track does not divide")
    fun degenerateInputs() {
        assertEquals(1f, indicatorStretchPeak(0.dp, NAV_TRACK), TOLERANCE)
        // Reached on the first composition, before BoxWithConstraints has measured anything.
        assertEquals(1f, indicatorStretchPeak(NAV_PITCH, 0.dp), TOLERANCE)
    }

    @Test
    @DisplayName("the gel peaks at 42% of the travel, on whatever duration it is handed")
    fun peakFraction() {
        assertEquals(0.42f, GEL_PEAK_FRACTION)
        // Both live call sites, so a change to either duration keeps the drawn proportion.
        assertEquals(142, (340 * GEL_PEAK_FRACTION).toInt()) // nav pill, NAV_PILL_TRAVEL
        assertEquals(109, (260 * GEL_PEAK_FRACTION).toInt()) // chart tabs, motion.base
    }

    private companion object {
        const val TOLERANCE = 0.0005f
        val NAV_TRACK = 411.4f.dp
        val NAV_PITCH = 133.1f.dp
        val TAB_TRACK = 379.3f.dp
        val TAB_PITCH = 125.1f.dp
    }
}
