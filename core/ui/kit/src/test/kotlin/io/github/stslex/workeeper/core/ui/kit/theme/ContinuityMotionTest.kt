// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The gate for §26's **characterless transit specs** — the frames no golden can see.
 *
 * **Scope.** §26 splits an animation into *transit* (that a property is interpolated at all — the
 * class) and *character* (which curve carries it — §5's business). This file gates the **defaults**
 * a transit takes when it carries no character. It does **not** assert that nothing in the app
 * overshoots: the FAB's `border-radius` rides `--e-spring` and the nav pill stretches, both
 * approved and both recorded in §26, neither routed through this file. A member carrying character
 * is gated by its own row.
 *
 * Why the frames and not the endpoints: §27, "a golden image gates only what a single static frame
 * contains" — motion's confirmed casualty on this exact interaction was a mid-frame that both
 * endpoint pictures were right about.
 *
 * ## There are two specs now, and that is a cost this file pays in full
 *
 * The class used to publish **one** spec, and that was load-bearing here: a single assertion covered
 * every animation in the class, and the file said so. The amendment splits the default **by what is
 * interpolated** — [continuityPositionalSpec] on `out`, [continuityAlphaSpec] on `linear` — because
 * `out` is near-expo and a fade driven by it lands 83 % of its travel in five frames, reading as
 * ~85ms of a declared 260 and becoming indistinguishable from no transit at all (§26.1, measured).
 *
 * The named cost of that split is exactly this file: **one assertion no longer covers the class.**
 * So every property below is asserted on **both** specs, and `spring` is run as the negative
 * control against **both**, because a detector proven to fire on one of two specs is a detector that
 * certifies nothing about the other. That doubling is not ceremony; it is the price of the split,
 * paid where it comes due.
 *
 * ## What "the midpoint is implied by the endpoints" means, and why it needs asserting
 *
 * Every site in the class animates **alpha or a bounded placement** — no site interpolates a
 * colour, which is what keeps the `fadedOut` rule (§27, `FadeOut.kt`) satisfied by construction
 * rather than by care. But bounded-property-only is not sufficient on its own: it is only true that
 * no mid-frame lies outside the two endpoints **if the driving curve stays inside [0, 1] and never
 * reverses**. Drive either spec with [AppMotion.spring] and the claim fails at t≈0.57 — which is
 * not hypothetical, it is §5's overshoot rule and the reason that rule exists.
 *
 * So the property is asserted directly on each spec, and then **asserted to discriminate**.
 */
internal class ContinuityMotionTest {

    private val motion = provideAppMotion()

    /** 101 samples across the closed interval — the endpoints are included deliberately. */
    private val samples = (0..SAMPLE_STEPS).map { it.toFloat() / SAMPLE_STEPS }

    private val positional: TweenSpec<Float> get() = continuityPositionalSpec(motion)
    private val alpha: TweenSpec<Float> get() = continuityAlphaSpec(motion)

    // ---- both specs are composed from the tokens, not written out ------------------------------

    @Test
    fun `both specs read their duration off the motion scale`() {
        assertEquals(motion.base, positional.durationMillis)
        assertEquals(motion.base, alpha.durationMillis)
    }

    @Test
    fun `the class runs at base, and base is 260`() {
        // Pinned as two claims, not one: the site above proves the specs are composed from the
        // token, this proves which rung the token is. A literal 260 in a spec would pass the
        // first and a silent retune of `base` would pass neither.
        assertEquals(BASE_MS, motion.base)
    }

    @Test
    fun `neither spec delays — continuity motion never sequences`() {
        // A delay is how a stage is built, and a staged tween reads as a moment (§26: nothing
        // here may compete with set closure or the record sweep).
        assertEquals(0, positional.delay)
        assertEquals(0, alpha.delay)
    }

    // ---- the split is by what is interpolated, and each half took the curve it took -------------

    @Test
    fun `positional uses out, and out is the same instance the scale publishes`() {
        assertSame(motion.out, positional.easing)
    }

    @Test
    fun `alpha uses linear, and linear is the same instance the scale publishes`() {
        assertSame(motion.linear, alpha.easing)
    }

    /**
     * **The split is real, not two names for one spec.**
     *
     * The failure this catches is the tidy-up: someone collapses the two back onto one curve,
     * every other assertion in this file still passes, and the amendment silently reverts. It is
     * the same shape as the indirection §27 already records — a gate that reads the right name
     * while the frames are wrong.
     */
    @Test
    fun `the two halves do not share a curve`() {
        assertTrue(
            positional.easing !== alpha.easing,
            "positional and alpha resolved to the same easing; the §26.1 split is gone",
        )
    }

    @Test
    fun `neither default uses spring`() {
        // Narrow on purpose. Not "nothing overshoots" — the FAB's radius does, legally. This says
        // the *characterless defaults* carry no character, which is what their names promise.
        assertTrue(positional.easing !== motion.spring)
        assertTrue(alpha.easing !== motion.spring)
    }

    // ---- the transit, asserted on the spec's own curve ------------------------------------------

    /**
     * **The curve under test is each spec's own, never [AppMotion.out] or [AppMotion.linear].**
     *
     * **Sample the curve the spec RESOLVES, never the token it was built from** — §27. Sampling
     * `motion.out` instead of `continuityPositionalSpec(motion).easing` reads correctly and gates
     * nothing: it asserts a property of the curve the class is *supposed* to use.
     */
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

    /**
     * The alpha half's whole reason for existing, asserted as the identity it is.
     *
     * `linear` is on the scale for being **nothing**: progress equals elapsed fraction at every
     * sample, so perceived duration equals declared duration and the remaining dial is a single
     * number a device can judge. Any easing at all would fail this — including `out`, which is the
     * curve this half was moved off, and which is what makes the assertion a gate on the amendment
     * rather than a restatement of it.
     */
    @Test
    fun `the alpha curve is the identity, which is what makes its duration judgeable`() {
        val worst = samples.maxOf { t -> kotlin.math.abs(alpha.easing.transform(t) - t) }
        assertTrue(
            worst <= TOLERANCE,
            "the alpha curve deviated from the identity by $worst; perceived duration then stops " +
                "tracking declared duration and §26.1's single judgeable number is gone",
        )
    }

    // ---- the detector is proven to fire, on BOTH specs -------------------------------------------

    @Test
    fun `spring fails the same excursion check — the assertions above discriminate`() {
        val peak = samples.maxOf { motion.spring.transform(it) }
        assertTrue(
            peak > 1f + TOLERANCE,
            "spring peaked at $peak; the excursion checks above are then vacuous and are " +
                "certifying nothing, for either spec",
        )
    }

    /**
     * The second negative control, and the reason it is separate from the one above.
     *
     * The excursion check discriminates by overshoot. The identity check discriminates by *shape*,
     * and `spring` is not the only thing that would break it — `out` breaks it too, hard. Running
     * both curves through it proves the alpha assertion is not satisfied by any curve that happens
     * to stay inside `[0, 1]`, which `out` does.
     */
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

        /**
         * A curve must miss the identity by more than this to count as discriminated. Set well
         * above [TOLERANCE] so the control asserts a real shape difference rather than solver noise
         * — `out` misses by ~0.4 and `spring` by more.
         */
        const val IDENTITY_DISCRIMINATION = 0.1f
    }
}
