// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.unveilIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigationevent.NavigationEvent
import io.github.stslex.workeeper.core.ui.kit.theme.provideAppMotion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The gate for the predictive-back preview — the frames no golden can see.
 *
 * ## Why this is assertable at all, and what that cost
 *
 * `AnimatedContentTransitionScope` is a **sealed** interface with only `internal` implementations,
 * so the receiver `NavDisplay` supplies cannot be faked. Every builder in `NavTransitions.kt`
 * therefore takes `AppMotion` + `Int` and touches no receiver member; the host's lambdas hold the
 * receiver and do nothing but delegate. That is a deliberate shape, not a coincidence.
 *
 * The transitions are compared **structurally**: `EnterTransition`/`ExitTransition` compare their
 * `TransitionData`, whose `Fade`/`Scale`/`Veil` leaves are `data class`es and whose `TweenSpec`
 * compares duration, delay and easing. That works **only because this design carries no `slide` and
 * no `changeSize` channel** — both store a lambda that is reference-compared, and
 * `slideOutHorizontally` re-wraps its argument, so even a shared production lambda would not
 * survive the wrap. Avoiding those channels bought JVM-assertability along with the placement-pass
 * cost they carry.
 *
 * ## What this file CANNOT pin, stated rather than implied
 *
 * - **That `NavDisplay` receives any of these.** The wiring in `AppNavigationHost` is composable and
 *   is proven only on a device.
 * - **That the leaving screen is gone in pixels at fraction 1.0.** The arithmetic below proves alpha
 *   reaches exactly 0 at `base`; that this coincides with fraction 1.0 depends on
 *   `transition.totalDurationNanos == base` at runtime, which depends on what *other* animations the
 *   two scenes register. Two proxies are asserted — every window sums to `base`, and the scrim is
 *   pure black, which is what keeps the outgoing's veil a zero-duration animation. Neither is the
 *   thing itself.
 * - **That it looks like the system animation.** Perception is a device judgement.
 */
@OptIn(ExperimentalAnimationApi::class)
internal class NavTransitionsTest {

    private val motion = provideAppMotion()

    /** 101 samples across the closed interval — the endpoints are included deliberately. */
    private val samples = (0..SAMPLE_STEPS).map { it.toFloat() / SAMPLE_STEPS }

    // ---- the fade the gesture did NOT touch ----------------------------------------------------

    @Test
    fun `forward and tapped-back still run the fade this host has always run`() {
        val transform = navFadeTransform(motion)
        assertEquals(
            fadeIn(animationSpec = tween(BASE_MS), initialAlpha = ENTER_ALPHA),
            transform.targetContentEnter,
            "the push/pop crossfade moved; that is an app-wide change and is not this commit's",
        )
        assertEquals(
            fadeOut(animationSpec = tween(BASE_MS), targetAlpha = 0f),
            transform.initialContentExit,
        )
    }

    // ---- the gesture, channel by channel -------------------------------------------------------

    @Test
    fun `a left-edge swipe shrinks about the trailing edge and dissolves late`() {
        val expected = scaleOut(
            animationSpec = tween(BASE_MS, easing = motion.travel),
            targetScale = PREVIEW_SCALE,
            transformOrigin = TransformOrigin(TRAILING, CENTRE),
        ) + fadeOut(
            animationSpec = tween(HANDOFF_MS, delayMillis = HOLD_MS, easing = motion.linear),
            targetAlpha = 0f,
        )
        assertEquals(expected, predictivePopExit(motion, NavigationEvent.EDGE_LEFT))
    }

    @Test
    fun `a right-edge swipe is the exact mirror`() {
        val expected = scaleOut(
            animationSpec = tween(BASE_MS, easing = motion.travel),
            targetScale = PREVIEW_SCALE,
            transformOrigin = TransformOrigin(LEADING, CENTRE),
        ) + fadeOut(
            animationSpec = tween(HANDOFF_MS, delayMillis = HOLD_MS, easing = motion.linear),
            targetAlpha = 0f,
        )
        assertEquals(expected, predictivePopExit(motion, NavigationEvent.EDGE_RIGHT))
        assertNotEquals(
            predictivePivot(NavigationEvent.EDGE_LEFT),
            predictivePivot(NavigationEvent.EDGE_RIGHT),
        )
    }

    @Test
    fun `an edgeless back shrinks concentrically, and so does an unknown edge`() {
        val centre = TransformOrigin(CENTRE, CENTRE)
        assertEquals(centre, predictivePivot(NavigationEvent.EDGE_NONE))
        assertEquals(centre, predictivePivot(UNKNOWN_EDGE))
    }

    @Test
    fun `the screen being uncovered gets a scrim and nothing else`() {
        assertEquals(
            unveilIn(
                animationSpec = tween(HANDOFF_MS, delayMillis = HOLD_MS, easing = motion.linear),
                initialColor = Color.Black.copy(alpha = SCRIM_ALPHA),
            ),
            predictivePopEnter(motion),
        )
    }

    @Test
    fun `the transform is the two halves and nothing is dropped between them`() {
        val transform = predictivePopTransform(motion, NavigationEvent.EDGE_LEFT)
        assertEquals(predictivePopEnter(motion), transform.targetContentEnter)
        assertEquals(
            predictivePopExit(motion, NavigationEvent.EDGE_LEFT),
            transform.initialContentExit,
        )
    }

    // ---- the windows, read off the scale AND pinned to numbers ---------------------------------

    @Test
    fun `every window is composed from the motion scale, not written out`() {
        assertEquals(motion.base, predictiveGeometrySpec(motion).durationMillis)
        assertEquals(0, predictiveGeometrySpec(motion).delay)
        assertEquals(motion.fast, predictiveHandoffSpec<Float>(motion).delay)
        assertEquals(
            motion.base - motion.fast,
            predictiveHandoffSpec<Float>(motion).durationMillis,
        )
    }

    @Test
    fun `and the numbers those come out as are 260, 140 and 120`() {
        // Pinned as its own claim: the site above proves the windows are composed from the scale,
        // this one proves the scale is where it was when the timings were judged.
        assertEquals(BASE_MS, predictiveGeometrySpec(motion).durationMillis)
        assertEquals(HOLD_MS, predictiveHandoffSpec<Float>(motion).delay)
        assertEquals(HANDOFF_MS, predictiveHandoffSpec<Float>(motion).durationMillis)
    }

    @Test
    fun `each curve is the token it claims, by identity`() {
        assertSame(motion.travel, predictiveGeometrySpec(motion).easing)
        assertSame(motion.linear, predictiveHandoffSpec<Float>(motion).easing)
    }

    // ---- the two invariants the whole design rests on -------------------------------------------

    /**
     * Channel windows must sum to the same total, or the fraction the gesture drives stops meaning
     * what the arithmetic assumed. This is the machine-checkable half of the clearing proof.
     */
    @Test
    fun `every channel finishes at base, which is what empties the leaving screen`() {
        val geometry = predictiveGeometrySpec(motion)
        val handoff = predictiveHandoffSpec<Float>(motion)
        assertEquals(motion.base, geometry.delay + geometry.durationMillis)
        assertEquals(motion.base, handoff.delay + handoff.durationMillis)
    }

    /** Evaluated with Compose's own evaluator, not a reimplementation of tween arithmetic. */
    @Test
    fun `the card is still fully opaque when the hold ends, and exactly gone at the end`() {
        val fade = TargetBasedAnimation(
            animationSpec = predictiveHandoffSpec(motion),
            typeConverter = Float.VectorConverter,
            initialValue = 1f,
            targetValue = 0f,
        )
        assertEquals(1f, fade.getValueFromNanos(motion.fast * MILLIS_TO_NANOS))
        assertEquals(0f, fade.getValueFromNanos(motion.base * MILLIS_TO_NANOS))
    }

    /**
     * The scrim's base colour is load-bearing far beyond how it looks: `AnimatedContent` hands the
     * enter transition to the *outgoing* scene too, where the veil's endpoints are this colour at
     * alpha 0 and `Color.Transparent`. Equal → a zero-duration spring. Unequal → a ~600ms spring
     * joins the transition, `totalDurationNanos` leaves `base`, and the assertion above stops
     * describing fraction 1.0.
     */
    @Test
    fun `the scrim is pure black, which is what keeps the transition base-long`() {
        assertEquals(Color.Transparent, predictiveScrimColor().copy(alpha = 0f))
        // Within a quantisation step, not exactly: Color stores alpha in 8 bits, so 0.32f comes
        // back as 82/255 = 0.3215686. The tolerance is one step — two orders of magnitude below
        // any strength that would be a different design decision.
        assertEquals(SCRIM_ALPHA, predictiveScrimColor().alpha, ALPHA_QUANTISATION)
    }

    // ---- monotone under a seek, and the controls that prove the check discriminates -------------

    /**
     * Under a seek the fraction is the finger, so a non-monotone curve means the card moves
     * backwards while the thumb moves forwards. Sampled on the value the spec **resolves** to —
     * §27 — not on the token it was built from.
     */
    private fun scaleExcursions(spec: TweenSpec<Float>): List<Float> {
        val animation = TargetBasedAnimation(spec, Float.VectorConverter, 1f, PREVIEW_SCALE)
        val values = samples.map {
            animation.getValueFromNanos((it * BASE_MS).toLong() * MILLIS_TO_NANOS)
        }
        val outOfRange = values.filter { it < PREVIEW_SCALE - TOLERANCE || it > 1f + TOLERANCE }
        val reversals = values.zipWithNext().filter { (a, b) -> b > a + TOLERANCE }.map { it.second }
        return outOfRange + reversals
    }

    @Test
    fun `the shrink never overshoots its target and never runs backwards`() {
        val excursions = scaleExcursions(predictiveGeometrySpec(motion))
        assertTrue(
            excursions.isEmpty(),
            "the shrink left [0.9, 1.0] or reversed at $excursions",
        )
    }

    @Test
    fun `spring fails that check — so the check above is not vacuous`() {
        val springy: TweenSpec<Float> = tween(motion.base, easing = motion.spring)
        assertTrue(
            scaleExcursions(springy).isNotEmpty(),
            "spring stayed inside the band; the monotonicity check is then certifying nothing, " +
                "and the seek ruling has no gate behind it",
        )
    }

    @Test
    fun `out is too front-loaded to be a finger response — the curve choice discriminates`() {
        // Not an overshoot failure: `out` is monotone and bounded, so the check above cannot see
        // it. This is the second control, and it is why `travel` and not `out`.
        val outAtQuarter = motion.out.transform(QUARTER)
        val travelAtQuarter = motion.travel.transform(QUARTER)
        assertTrue(
            outAtQuarter > FRONT_LOAD_DISCRIMINATION,
            "out consumed only $outAtQuarter of the shrink in the first quarter of the drag",
        )
        assertTrue(
            travelAtQuarter < outAtQuarter,
            "travel ($travelAtQuarter) is no gentler than out ($outAtQuarter) at a quarter drag",
        )
    }

    private companion object {
        const val SAMPLE_STEPS = 100
        const val TOLERANCE = 1e-4f
        const val MILLIS_TO_NANOS = 1_000_000L

        const val BASE_MS = 260
        const val HOLD_MS = 140
        const val HANDOFF_MS = 120

        const val PREVIEW_SCALE = 0.9f
        const val SCRIM_ALPHA = 0.32f

        /** `Color` packs alpha into 8 bits; one step is the floor on any alpha claim. */
        const val ALPHA_QUANTISATION = 1f / 255f
        const val ENTER_ALPHA = 0.3f

        const val LEADING = 0f
        const val TRAILING = 1f
        const val CENTRE = 0.5f
        const val UNKNOWN_EDGE = 99

        const val QUARTER = 0.25f
        const val FRONT_LOAD_DISCRIMINATION = 0.8f
    }
}
