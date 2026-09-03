package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable

/**
 * The v3 motion scale: three durations, four curves. Overshoot is valid on geometry and invalid
 * on colour or any bounded space — see documentation/feature-specs/v3-redesign-spec.md §5.
 */
@Immutable
data class AppMotion(
    /** 140ms — state flips the user should not perceive as animated: ripples, toggles, tints. */
    val fast: Int,
    /** 260ms — the default. Screen transitions, expand/collapse, anything with travel. */
    val base: Int,
    /** 520ms — deliberate, attention-seeking motion. Rare by design. */
    val slow: Int,
    /** Decelerate, monotone into its target. The safe default and the only curve for colour. */
    val out: Easing,
    /** Overshoot-and-settle; geometry only. Peak ~1.098 at t≈0.57, back to exactly 1.0 at t=1. */
    val spring: Easing,
    /**
     * Decelerate without [out]'s tail, for positional travel: [out] on a position reads as "it
     * moves, then hangs". See documentation/feature-specs/v3-redesign-spec.md §26.1.
     */
    val travel: Easing,
    /**
     * No shape at all — progress equals elapsed fraction, so perceived duration equals declared
     * duration. The characterless default for alpha; see v3-redesign-spec.md §26.1.
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

// A standard decelerate: no ease-in at all, and x2 = 0.2 keeps the settle short.
private const val TRAVEL_X1 = 0f
private const val TRAVEL_Y1 = 0f
private const val TRAVEL_X2 = 0.2f
private const val TRAVEL_Y2 = 1f

/**
 * GUARD: `CubicBezierEasing` does not validate its control points, so a curve that constructs is
 * not a curve that is well-formed — sample it. See v3-redesign-spec.md §5.
 */
fun provideAppMotion(): AppMotion = AppMotion(
    fast = FAST_MS,
    base = BASE_MS,
    slow = SLOW_MS,
    out = CubicBezierEasing(OUT_X1, OUT_Y1, OUT_X2, OUT_Y2),
    spring = CubicBezierEasing(SPRING_X1, SPRING_Y1, SPRING_X2, SPRING_Y2),
    travel = CubicBezierEasing(TRAVEL_X1, TRAVEL_Y1, TRAVEL_X2, TRAVEL_Y2),
    // GUARD: Compose's own, not a bezier reproduction — the scale is sampled against an exact
    // identity, and a cubic solve would put solver noise against it.
    linear = LinearEasing,
)
