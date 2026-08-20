// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.unveilIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigationevent.NavigationEvent
import io.github.stslex.workeeper.core.ui.kit.theme.provideAppMotion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    fun `forward and tapped-back run the crossfade, not the preview`() {
        val transform = navFadeTransform(motion)
        assertEquals(
            fadeIn(animationSpec = tween(BASE_MS), initialAlpha = ENTER_ALPHA),
            transform.targetContentEnter,
            "the push/pop crossfade moved; that is an app-wide change and owes its own commit",
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
            animationSpec = predictiveGeometrySpec(motion),
            targetScale = PREVIEW_SCALE,
            transformOrigin = TransformOrigin(TRAILING, CENTRE),
        ) + fadeOut(
            animationSpec = predictiveDepartureSpec(motion),
            targetAlpha = 0f,
        )
        assertEquals(expected, predictivePopExit(motion, NavigationEvent.EDGE_LEFT))
    }

    @Test
    fun `a right-edge swipe is the exact mirror`() {
        val expected = scaleOut(
            animationSpec = predictiveGeometrySpec(motion),
            targetScale = PREVIEW_SCALE,
            transformOrigin = TransformOrigin(LEADING, CENTRE),
        ) + fadeOut(
            animationSpec = predictiveDepartureSpec(motion),
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
    fun `the screen being uncovered is scrimmed and grows into place, and never fades`() {
        assertEquals(
            unveilIn(
                animationSpec = predictiveDepartureSpec(motion),
                initialColor = Color.Black.copy(alpha = SCRIM_ALPHA),
            ) + scaleIn(
                animationSpec = predictiveGeometrySpec(motion),
                initialScale = REVEAL_INITIAL_SCALE,
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
        assertEquals(motion.base, predictiveDepartureSpec<Float>(motion).durationMillis)
    }

    @Test
    fun `and the number that comes out as is 260`() {
        // Pinned as its own claim: the site above proves the windows are composed from the scale,
        // this one proves the rung the scale resolves to. A retune of `AppMotion.base` passes the
        // first and fails this one, which is the point — the gesture's duration is judged on a
        // device and does not follow the scale silently.
        assertEquals(BASE_MS, predictiveGeometrySpec(motion).durationMillis)
        assertEquals(BASE_MS, predictiveDepartureSpec<Float>(motion).durationMillis)
    }

    @Test
    fun `the two gesture curves are distinct, and neither is a motion-scale token`() {
        val preview = predictiveGeometrySpec(motion).easing
        val departure = predictiveDepartureSpec<Float>(motion).easing
        assertTrue(preview !== departure, "preview and departure collapsed onto one curve")
        // Deliberate: both reproduce Android's gesture response rather than expressing the app,
        // so they are file-local and not on the scale. Guard against a tidy-up that "fixes" that.
        listOf(motion.out, motion.travel, motion.linear, motion.spring).forEach { token ->
            assertTrue(preview !== token, "the preview curve was repointed at a motion token")
            assertTrue(departure !== token, "the departure curve was repointed at a motion token")
        }
    }

    // ---- the two invariants the whole design rests on -------------------------------------------

    /**
     * Channel windows must sum to the same total, or the fraction the gesture drives stops meaning
     * what the arithmetic assumed. This is the machine-checkable half of the clearing proof.
     */
    @Test
    fun `every channel finishes at base, which is what empties the leaving screen`() {
        val geometry = predictiveGeometrySpec(motion)
        val departure = predictiveDepartureSpec<Float>(motion)
        assertEquals(motion.base, geometry.delay + geometry.durationMillis)
        assertEquals(motion.base, departure.delay + departure.durationMillis)
        // Zero delay is the point, not an accident: a delayed channel puts a CORNER in a curve the
        // finger is seeking. See `the card never starts dissolving at an instant`.
        assertEquals(0, geometry.delay)
        assertEquals(0, departure.delay)
    }

    /** Evaluated with Compose's own evaluator, not a reimplementation of tween arithmetic. */
    @Test
    fun `the card starts solid and is exactly gone at the end`() {
        val fade = TargetBasedAnimation(
            animationSpec = predictiveDepartureSpec(motion),
            typeConverter = Float.VectorConverter,
            initialValue = 1f,
            targetValue = 0f,
        )
        assertEquals(1f, fade.getValueFromNanos(0L))
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

    // ---- the two shapes this design must not have, each with its own gate ---------------

    /**
     * **The kink gate.** A delayed curve is exactly a curve that does not move at the start, and
     * under a seek the fraction is the finger — so a delay boundary is a frame in which the card
     * goes from perfectly solid to visibly dissolving. The departure must therefore be already
     * moving before a tenth of the drag, and still mostly solid at half.
     *
     * The control below rebuilds a delayed spec and runs it through the same detector, which is
     * what makes this a gate rather than a description. §26's gesture-curve row carries the ruling.
     */
    @Test
    fun `the card never starts dissolving at an instant — it is moving from the first frame`() {
        val curve = predictiveDepartureSpec<Float>(motion).easing
        assertTrue(
            curve.transform(TENTH) > 0f,
            "the departure is flat at a tenth of the drag, so it has a corner somewhere later",
        )
        assertTrue(
            curve.transform(HALF) < BACK_LOAD_CEILING,
            "the card is only ${1 - curve.transform(HALF)} opaque at half a drag; the point of an " +
                "ease-in departure is that the preview stays solid while the finger is on it",
        )
    }

    @Test
    fun `a delayed curve is what that gate is aimed at, and it does fail it`() {
        // A delayed spec is the shape the kink gate exists to reject — flat for `fast`, then
        // linear. Run through the same detector, so the check above is shown to discriminate
        // rather than merely to pass.
        val delayed: TweenSpec<Float> =
            tween(motion.base - motion.fast, delayMillis = motion.fast, easing = motion.linear)
        val animation = TargetBasedAnimation(delayed, Float.VectorConverter, 1f, 0f)
        val atTenth = animation.getValueFromNanos((TENTH * BASE_MS).toLong() * MILLIS_TO_NANOS)
        assertEquals(
            1f,
            atTenth,
            "the delayed control was NOT flat at a tenth of the drag; the kink gate is then " +
                "certifying nothing",
        )
    }

    /**
     * **The response gate.** The preview must be front-loaded: a curve that lags the finger early
     * reads as unresponsive, and one that keeps moving after the gesture commits reads as
     * unbounded. The band below is Material's own quarter-drag value with slack, and no curve on
     * the motion scale falls inside it — which is why the assertion compares against a scale token
     * rather than only against a number. The values are in `architecture.md` § "Navigation host and
     * shared element transitions".
     */
    @Test
    fun `the preview answers the finger early, ahead of every curve on the motion scale`() {
        val preview = predictiveGeometrySpec(motion).easing.transform(QUARTER)
        assertTrue(
            preview > motion.travel.transform(QUARTER),
            "the preview ($preview at a quarter drag) is no more responsive than travel " +
                "(${motion.travel.transform(QUARTER)})",
        )
        assertTrue(
            preview > FRONT_LOAD_FLOOR && preview < FRONT_LOAD_CEILING,
            "the preview consumed $preview of the shrink in the first quarter; Material's own " +
                "curve spends ~0.68 there, and leaving that band is a different interaction",
        )
    }

    private companion object {
        const val SAMPLE_STEPS = 100
        const val TOLERANCE = 1e-4f
        const val MILLIS_TO_NANOS = 1_000_000L

        const val BASE_MS = 260

        const val PREVIEW_SCALE = 0.9f
        const val REVEAL_INITIAL_SCALE = 0.95f
        const val SCRIM_ALPHA = 0.32f

        /** `Color` packs alpha into 8 bits; one step is the floor on any alpha claim. */
        const val ALPHA_QUANTISATION = 1f / 255f
        const val ENTER_ALPHA = 0.3f

        const val LEADING = 0f
        const val TRAILING = 1f
        const val CENTRE = 0.5f
        const val UNKNOWN_EDGE = 99

        const val TENTH = 0.1f
        const val QUARTER = 0.25f
        const val HALF = 0.5f

        /** The card must still be more than three quarters opaque at half a drag. */
        const val BACK_LOAD_CEILING = 0.25f

        /**
         * The band a front-loaded preview must land in. Its derivation — Material's quarter-drag
         * value and the slack around it — is in `documentation/architecture.md` § "Navigation host
         * and shared element transitions".
         */
        const val FRONT_LOAD_FLOOR = 0.58f
        const val FRONT_LOAD_CEILING = 0.78f
    }
}
