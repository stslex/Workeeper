package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable

/**
 * The v3 motion scale: three durations, three curves.
 *
 * The previous set had five durations and four easings, of which production read exactly two
 * durations and zero easings — measured, not assumed. Five names for two used values is not a
 * scale, it is a menu. This is the whole vocabulary; anything that needs a duration not on this
 * list is a design decision, not a call site's decision.
 *
 * ## Overshoot is valid on geometry and invalid on colour
 *
 * [spring] rises above 1.0 on its way to 1.0 (peak ~1.098 at t≈0.57 — sampled, see below).
 * That is the point of it: a scale or a translation that overshoots and settles reads as
 * physical.
 *
 * It is **not** valid to drive a colour with it. `lerp(colorA, colorB, 1.098)` extrapolates
 * past the target: channels leave `[0,1]` and are either clamped — silently flattening the
 * overshoot the animation was chosen for — or wrapped into a garbage colour, depending on the
 * path taken. Use [out] for every colour transition. Step 5's transient wow state animates
 * both a scale and a tint; they must not share a curve.
 *
 * Applies to anything interpolated in a bounded space, not only colour: alpha, and any
 * normalised progress fraction, have the same problem.
 */
@Immutable
data class AppMotion(
    /** 140ms — state flips the user should not perceive as animated: ripples, toggles, tints. */
    val fast: Int,
    /** 260ms — the default. Screen transitions, expand/collapse, anything with travel. */
    val base: Int,
    /** 520ms — deliberate, attention-seeking motion. Rare by design. */
    val slow: Int,
    /**
     * Decelerate. Fast departure, long settle, no overshoot — monotone into its target.
     * The safe default, and the only correct curve for colour.
     */
    val out: Easing,
    /**
     * Overshoot-and-settle. **Geometry only** — see the class KDoc. Peak ~1.098 at t≈0.57,
     * returning to exactly 1.0 at t=1.
     */
    val spring: Easing,
    /**
     * No shape at all — progress equals elapsed fraction, exactly, at every sample.
     *
     * **The curve for alpha** (§26, continuity motion, as amended). It is on the scale for being
     * *nothing*, not for being good, and that is the whole argument: any easing applied to a fade
     * is character by the class's own definition, so the characterless default for alpha is the
     * curve that has none.
     *
     * The measurement that put it here: [out] is near-expo, so a 260ms crossfade driven by it puts
     * **83 % of its alpha travel in the first five frames** and spends the rest below 8-bit
     * visibility — a correct transit that reads as ~85ms and is therefore indistinguishable from no
     * transit at all. Duration cannot recover it, because perceived crossfade length tracks the
     * *middle* of the curve and [out]'s middle is already over: doubling to [slow] buys 7.7 frames
     * in the perceptible band where `linear` at 160ms buys 6.7. Under `linear`, perceived duration
     * **equals** declared duration, which is what makes the number judgeable on a device.
     *
     * Bounded-space-safe by construction, so §5's overshoot rule is satisfied without care rather
     * than by it: a curve that cannot exceed its endpoints cannot extrapolate a colour or drive a
     * fade past 1.0.
     */
    val linear: Easing,
)

private const val FAST_MS = 140
private const val BASE_MS = 260
private const val SLOW_MS = 520

/** `out` — decelerate, monotone. (x1, y1, x2, y2). */
private const val OUT_X1 = 0.16f
private const val OUT_Y1 = 1f
private const val OUT_X2 = 0.3f
private const val OUT_Y2 = 1f

/** `spring` — the overshoot lives in [SPRING_Y1] = 1.56, past the 1.0 target. */
private const val SPRING_X1 = 0.34f
private const val SPRING_Y1 = 1.56f
private const val SPRING_X2 = 0.64f
private const val SPRING_Y2 = 1f

/**
 * `CubicBezierEasing`'s four arguments are the two control points as (x1, y1, x2, y2). The
 * overshoot lives in y2 = 1.56 for [AppMotion.spring].
 *
 * Verified by construction rather than from the signature: both curves below were instantiated
 * and sampled across t ∈ [0,1] in 0.01 steps. `spring` peaks at **1.0977899 at t = 0.57** and
 * returns to 1.0 at t = 1; `out` is monotone with a peak of exactly 1.0. Compose accepted both.
 *
 * Note for anyone tightening this later: on Compose BOM 2026.07.00 `CubicBezierEasing` does
 * **not** validate its arguments. A control point with x = 1.56 — outside the [0,1] the cubic
 * solver assumes — constructed *and* evaluated without complaint (`transform(0.5f)` returned
 * 0.1504). So a successful construction is not evidence that a curve is well-formed; only
 * sampling it is.
 */
fun provideAppMotion(): AppMotion = AppMotion(
    fast = FAST_MS,
    base = BASE_MS,
    slow = SLOW_MS,
    out = CubicBezierEasing(OUT_X1, OUT_Y1, OUT_X2, OUT_Y2),
    spring = CubicBezierEasing(SPRING_X1, SPRING_Y1, SPRING_X2, SPRING_Y2),
    // Compose's own, not a bezier reproduction of it: `CubicBezierEasing(0,0,1,1)` is linear only
    // to the precision of a cubic solve, and this scale is sampled against an exact identity.
    linear = LinearEasing,
)
