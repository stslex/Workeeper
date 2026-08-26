// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * GUARD: these peaks are transcribed from the device measurement, never derived from
 * [INDICATOR_STRETCH] — a derived expectation asserts arithmetic instead of a value.
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
        // Same coefficient, narrower track: the tabs' peaks are higher, not equal.
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
        // Without the clamp a jump wider than the track grows unbounded and inverts the indicator.
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
