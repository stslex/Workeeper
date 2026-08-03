// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.theme.provideAppMotion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The metric indicator's curve — **§10.4 territory, so it is asserted rather than photographed.**
 *
 * `ChartGoldenTest` renders the tabs at rest with the thumb at a stop, which pins the offset and
 * nothing about how it got there. The curve lives strictly between two settled frames.
 */
internal class MetricTabsMotionTest {

    private val motion = provideAppMotion()

    @Test
    @DisplayName("the indicator travels on `travel`, and on the scale's own instance")
    fun indicatorUsesTravel() {
        assertSame(motion.travel, metricIndicatorSpec<Dp>(motion).easing)
        // §26.2 replaced `out` here on a device measurement; repointing back is the regression
        // this exists to redden, and `assertSame` also rejects a locally rebuilt equal curve.
        assertNotSame(motion.out, metricIndicatorSpec<Dp>(motion).easing)
    }

    @Test
    @DisplayName("the duration stays on the scale — only the curve moved")
    fun durationIsUnchanged() {
        // §26.2 is a curve amendment and nothing else. `base` is what this always ran at, and
        // pinning it here is what stops the tail being "fixed" a second time by shortening it.
        assertEquals(motion.base, metricIndicatorSpec<Dp>(motion).durationMillis)
    }
}
