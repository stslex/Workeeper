// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Gates the characterless continuity transit specs: duration, curve, and the mid-frames no
 * golden can see. See documentation/feature-specs/v3-redesign-spec.md §26.
 */
internal class ContinuityMotionTest {

    private val motion = provideAppMotion()

    /** 101 samples across the closed interval — the endpoints are included deliberately. */
    private val samples = (0..SAMPLE_STEPS).map { it.toFloat() / SAMPLE_STEPS }

    private val positional: TweenSpec<Float> get() = continuityPositionalSpec(motion)
    private val alpha: TweenSpec<Float> get() = continuityAlphaSpec(motion)

    @Test
    fun `both specs read their duration off the motion scale`() {
        assertEquals(motion.base, positional.durationMillis)
        assertEquals(motion.base, alpha.durationMillis)
    }

    @Test
    fun `the class runs at base, and base is 260`() {
        // Two claims, not one: that the specs read the token, and which rung the token is.
        assertEquals(BASE_MS, motion.base)
    }

    @Test
    fun `neither spec delays — continuity motion never sequences`() {
        // A delay builds a stage, and a staged tween reads as a moment.
        assertEquals(0, positional.delay)
        assertEquals(0, alpha.delay)
    }

    @Test
    fun `positional uses out, and out is the same instance the scale publishes`() {
        assertSame(motion.out, positional.easing)
    }

    @Test
    fun `alpha uses linear, and linear is the same instance the scale publishes`() {
        assertSame(motion.linear, alpha.easing)
    }

    /** Catches the tidy-up that collapses the two specs back onto one curve. */
    @Test
    fun `the two halves do not share a curve`() {
        assertTrue(
            positional.easing !== alpha.easing,
            "positional and alpha resolved to the same easing; the §26.1 split is gone",
        )
    }

    @Test
    fun `neither default uses spring`() {
        // Narrow on purpose: the defaults carry no character; other sites overshoot legally.
        assertTrue(positional.easing !== motion.spring)
        assertTrue(alpha.easing !== motion.spring)
    }

    /** Samples the curve each spec RESOLVES, never the token it was built from. */
    private fun excursions(curve: Easing) = samples.filter { t ->
        val v = curve.transform(t)
        v < -TOLERANCE || v > 1f + TOLERANCE
    }

    private fun reversals(curve: Easing) = samples.zipWithNext().filter { (a, b) ->
        curve.transform(b) < curve.transform(a) - TOLERANCE
    }

    @Test
    fun `neither curve leaves the unit interval, so no mid-frame lies outside the endpoints`() {
        assertTrue(
            excursions(positional.easing).isEmpty(),
            "the positional curve excursed outside [0,1] at t = ${excursions(positional.easing)}",
        )
        assertTrue(
            excursions(alpha.easing).isEmpty(),
            "the alpha curve excursed outside [0,1] at t = ${excursions(alpha.easing)}",
        )
    }

    @Test
    fun `neither curve reverses, so a fade only ever fades one way`() {
        assertTrue(
            reversals(positional.easing).isEmpty(),
            "the positional curve went backwards between ${reversals(positional.easing)}",
        )
        assertTrue(
            reversals(alpha.easing).isEmpty(),
            "the alpha curve went backwards between ${reversals(alpha.easing)}",
        )
    }

    /** `linear` is the identity, so perceived duration equals declared duration. */
    @Test
    fun `the alpha curve is the identity, which is what makes its duration judgeable`() {
        val worst = samples.maxOf { t -> kotlin.math.abs(alpha.easing.transform(t) - t) }
        assertTrue(
            worst <= TOLERANCE,
            "the alpha curve deviated from the identity by $worst; perceived duration then stops " +
                "tracking declared duration and §26.1's single judgeable number is gone",
        )
    }

    @Test
    fun `spring fails the same excursion check — the assertions above discriminate`() {
        val peak = samples.maxOf { motion.spring.transform(it) }
        assertTrue(
            peak > 1f + TOLERANCE,
            "spring peaked at $peak; the excursion checks above are then vacuous and are " +
                "certifying nothing, for either spec",
        )
    }

    /** Discriminates by shape rather than overshoot: `out` stays inside [0,1] and still fails. */
    @Test
    fun `out and spring both fail the identity check — the alpha assertion discriminates`() {
        val outWorst = samples.maxOf { t -> kotlin.math.abs(motion.out.transform(t) - t) }
        val springWorst = samples.maxOf { t -> kotlin.math.abs(motion.spring.transform(t) - t) }
        assertTrue(
            outWorst > IDENTITY_DISCRIMINATION,
            "out deviated from the identity by only $outWorst; the alpha identity assertion is " +
                "then not distinguishing linear from the curve it was moved off",
        )
        assertTrue(
            springWorst > IDENTITY_DISCRIMINATION,
            "spring deviated from the identity by only $springWorst",
        )
    }

    private companion object {
        const val SAMPLE_STEPS = 100
        const val BASE_MS = 260

        /** Float slack on a cubic solve. The overshoot this separates from is 0.098. */
        const val TOLERANCE = 1e-4f

        /** A curve must miss the identity by more than this to count as discriminated. */
        const val IDENTITY_DISCRIMINATION = 0.1f
    }
}
