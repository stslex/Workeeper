// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.theme.provideAppMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * The metric indicator's curve — §10.4 territory, so it is asserted rather than photographed:
 * the goldens pin the thumb's resting offsets, not how it got there.
 */
internal class MetricTabsMotionTest {

    private val motion = provideAppMotion()

    /** the indicator travels on `travel`, and on the scale's own instance */
    @Test
    fun indicatorUsesTravel() {
        assertSame(motion.travel, metricIndicatorSpec<Dp>(motion).easing)
        // Repointing back to `out` is the regression this exists to redden.
        assertNotSame(motion.out, metricIndicatorSpec<Dp>(motion).easing)
    }

    /** the duration stays on the scale — only the curve moved */
    @Test
    fun durationIsUnchanged() {
        // §26.2 is a curve amendment and nothing else; the duration stays on the scale.
        assertEquals(motion.base, metricIndicatorSpec<Dp>(motion).durationMillis)
    }
}
