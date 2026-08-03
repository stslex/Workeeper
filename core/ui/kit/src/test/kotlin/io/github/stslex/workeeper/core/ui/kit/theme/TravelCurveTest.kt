// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [AppMotion.travel] — **the property it was adopted for, asserted as a property.**
 *
 * §26.2 replaced `out` on positional travel because `out` is near-expo: the last 10% of the journey
 * occupies most of the animation, so the indicator arrives and then creeps. The number that decides
 * that is the **tail fraction** — the share of the animation spent covering the last 10% of the
 * distance — and it is a pure function of the curve, so it is assertable here even though §10.4 puts
 * the device measurement itself out of reach of any gate.
 *
 * **Asserted against the drawn thresholds, not against the curve's own output.** An expectation
 * computed from [AppMotion.travel] would assert arithmetic and move with any mutation; these
 * compare it to `out` and to fixed bounds, so repointing the constant reddens.
 */
internal class TravelCurveTest {

    private val motion = provideAppMotion()

    /** Share of the animation spent covering the last 10% of the distance. */
    private fun tailFraction(easing: androidx.compose.animation.core.Easing): Double {
        var lo = 0.0
        var hi = 1.0
        repeat(SOLVE_STEPS) {
            val mid = (lo + hi) / 2
            if (easing.transform(mid.toFloat()) < LAST_TENTH) lo = mid else hi = mid
        }
        return 1.0 - (lo + hi) / 2
    }

    @Test
    @DisplayName("travel has materially less tail than out — the reason it exists")
    fun travelBeatsOut() {
        val travel = tailFraction(motion.travel)
        val out = tailFraction(motion.out)
        // Device-measured equivalents on the nav pill: out 50.6%, travel 37.1% (a 1px floor makes
        // the measurement a lower bound on the analytic value, which is why the bound here is the
        // analytic one). The ORDERING is the contract; the margin is what makes it worth a token.
        assertTrue(travel < out - MIN_IMPROVEMENT) {
            "travel tail ${"%.3f".format(travel)} must beat out's ${"%.3f".format(out)} by " +
                "$MIN_IMPROVEMENT; a curve that does not is not worth a fourth scale entry"
        }
    }

    @Test
    @DisplayName("travel still DECELERATES — §26.1's reasoning is kept, only the severity changed")
    fun travelStillDecelerates() {
        // The whole argument for not shipping `linear`. Progress in the first half of the time must
        // exceed the linear share, i.e. the curve front-loads — that IS deceleration. `linear`
        // returns exactly 0.5 here and fails, which is what makes this discriminate.
        val half = motion.travel.transform(HALF)
        assertTrue(half > HALF + MIN_DECELERATION) {
            "travel must be decelerating at the midpoint (got $half); a curve at or below 0.5 " +
                "is linear or accelerating and discards §26.1's stated reason"
        }
    }

    @Test
    @DisplayName("travel is monotone and lands exactly on its endpoints")
    fun travelIsWellFormed() {
        // `CubicBezierEasing` does not validate its arguments (see AppMotion's KDoc), so a
        // malformed curve constructs and evaluates silently. Sampling is the only check.
        assertEquals(0f, motion.travel.transform(0f))
        assertEquals(1f, motion.travel.transform(1f))
        var previous = 0f
        repeat(SAMPLES + 1) { step ->
            val value = motion.travel.transform(step.toFloat() / SAMPLES)
            assertTrue(value >= previous) { "travel must be monotone; fell at t=$step/$SAMPLES" }
            assertTrue(value <= 1f) { "travel must not overshoot; got $value at t=$step/$SAMPLES" }
            previous = value
        }
    }

    @Test
    @DisplayName("travel departs immediately — it must not trade the tail for a slow start")
    fun travelDepartsAtOnce() {
        // The failure mode of the obvious alternative: CSS ease-out (.25,.1,.25,1) has a smaller
        // tail than `out` and covers only 3.2% in the first frame, which moves the lag from the end
        // of the animation to the beginning. Measured on the first frame of a 340ms run at 60Hz.
        val firstFrame = motion.travel.transform(FIRST_FRAME_OF_340MS)
        assertTrue(firstFrame > MIN_FIRST_FRAME) {
            "travel must cover more than $MIN_FIRST_FRAME in the first frame (got $firstFrame); " +
                "a curve that eases IN moves the reported lag to the start of the move"
        }
    }

    private companion object {
        const val LAST_TENTH = 0.90f
        const val SOLVE_STEPS = 60
        const val SAMPLES = 100
        const val HALF = 0.5f

        /** `out` measures ~0.67, `travel` ~0.60 at the midpoint; `linear` is exactly 0.5. */
        const val MIN_DECELERATION = 0.05f

        /** out 67.1%, travel 40.4% analytically — a margin of 0.15 is well inside that. */
        const val MIN_IMPROVEMENT = 0.15

        /** 16.667ms of 340ms — one frame at 60Hz on the nav pill's own duration. */
        const val FIRST_FRAME_OF_340MS = 0.049f

        /** `travel` covers 17.2%; CSS ease-out covers 3.2% and is what this rules out. */
        const val MIN_FIRST_FRAME = 0.10f
    }
}
