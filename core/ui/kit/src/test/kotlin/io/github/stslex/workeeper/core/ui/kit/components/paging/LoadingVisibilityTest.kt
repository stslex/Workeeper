// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.paging

import io.github.stslex.workeeper.core.ui.kit.theme.provideAppMotion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The loading deferral's two numbers and the arithmetic between them.
 *
 * **Nothing else in the repository can see any of this.** §10.4: a delay is invisible to a golden —
 * Paparazzi renders one frame and has no clock, so neither the 140 ms before the spinner appears
 * nor the 260 ms it is held afterwards can reach an image. The same class as
 * `LIST_BOTTOM_CLEARANCE`, and the same remedy: name the values, extract the arithmetic, assert it
 * directly.
 */
internal class LoadingVisibilityTest {

    private val motion = provideAppMotion()

    @Test
    @DisplayName("both numbers come off the motion scale, and they are the two they claim to be")
    fun bothNumbersAreOnTheScale() {
        // §5: "anything that needs a duration not on this list is a design decision". The point of
        // asserting this is that a future edit to either number has to move it to another RUNG,
        // not to an invented value — the failure mode this catches is `140` quietly becoming `150`.
        assertEquals(140, motion.fast)
        assertEquals(260, motion.base)
        assertTrue(motion.fast in setOf(motion.fast, motion.base, motion.slow))
    }

    @Test
    @DisplayName("the appear delay clears the measured worst-case load with margin")
    fun appearDelayClearsMeasuredLoads() {
        // Device-instrumented: a cold all-trainings entry resolved `refresh` in 61 ms, Home's warm
        // path in 23 ms. The delay must exceed the slower of those or the spinner still flashes on
        // a normal load, which is the whole complaint.
        val worstMeasuredLoadMs = 61
        assertTrue(motion.fast > worstMeasuredLoadMs) {
            "appear delay ${motion.fast}ms must exceed the measured ${worstMeasuredLoadMs}ms load"
        }
    }

    @Test
    @DisplayName("loading with nothing shown: wait the APPEAR DELAY, then show")
    fun stepShowsAfterTheAppearDelay() {
        // The case that was ungated until the step was extracted: with `delay(motion.fast)` inline
        // in the composable, mutating it to `delay(0)` — which restores the flash outright — came
        // back GREEN. The duration now lives in a value a test can read.
        assertEquals(
            LoadingStep.ShowAfter(140L),
            loadingStep(loading = true, visible = false, shownAtMillis = 0L, nowMillis = 0L, motion = motion),
        )
    }

    @Test
    @DisplayName("loading ended while shown: wait out the remaining minimum, then hide")
    fun stepHidesAfterTheRemainingHold() {
        assertEquals(
            LoadingStep.HideAfter(160L),
            loadingStep(loading = false, visible = true, shownAtMillis = 1_000L, nowMillis = 1_100L, motion = motion),
        )
    }

    @Test
    @DisplayName("already in the right state: nothing, in both directions")
    fun stepDoesNothingWhenSettled() {
        // Both branches, because "does nothing" is what a missing branch also produces.
        assertEquals(
            LoadingStep.Nothing,
            loadingStep(loading = true, visible = true, shownAtMillis = 0L, nowMillis = 0L, motion = motion),
        )
        assertEquals(
            LoadingStep.Nothing,
            loadingStep(loading = false, visible = false, shownAtMillis = 0L, nowMillis = 0L, motion = motion),
        )
    }

    @Test
    @DisplayName("a spinner just shown is held for the full minimum")
    fun holdIsFullWhenJustShown() {
        assertEquals(260L, loadingHoldRemaining(shownAtMillis = 1_000L, nowMillis = 1_000L, motion = motion))
    }

    @Test
    @DisplayName("the hold counts from when it APPEARED, not from when loading ended")
    fun holdCountsFromAppearance() {
        // The defect this rules out: holding for 260 ms after loading *ends* would keep a spinner
        // that had already been up for a second on screen for another 260 ms — the same flash
        // defect with a longer tail. 100 ms in, 160 ms remain.
        assertEquals(160L, loadingHoldRemaining(shownAtMillis = 1_000L, nowMillis = 1_100L, motion = motion))
    }

    @Test
    @DisplayName("a spinner already past the minimum is released immediately, never negatively")
    fun holdClampsAtZero() {
        assertEquals(0L, loadingHoldRemaining(shownAtMillis = 1_000L, nowMillis = 1_260L, motion = motion))
        // Clamped rather than negative: `delay(-1)` would not throw but would be a silent no-op,
        // and a negative here would mean the arithmetic had gone wrong somewhere it could not be
        // seen. Asserted on a value far past the minimum so the clamp is what is being measured.
        assertEquals(0L, loadingHoldRemaining(shownAtMillis = 1_000L, nowMillis = 9_999L, motion = motion))
    }

    @Test
    @DisplayName("the worst case: a 141ms load costs 259ms of added delay, and that is the ceiling")
    fun worstCaseArithmetic() {
        // The row of the KDoc's table that decides whether a two-number rule is worth it. A load
        // finishing one millisecond after the spinner appears is held for the full minimum, so the
        // content it was hiding is delayed by the minimum less that millisecond.
        val loadMs = motion.fast + 1
        val shownAt = motion.fast.toLong()
        val addedDelay = loadingHoldRemaining(shownAt, loadMs.toLong(), motion)
        assertEquals(259L, addedDelay)

        // And it IS a ceiling: no load can be delayed by more than the hold itself.
        assertTrue(addedDelay < motion.base) {
            "added delay must be strictly bounded by the hold (${motion.base}ms)"
        }
    }

    @Test
    @DisplayName("only loads in (140, 400) are delayed at all")
    fun onlyTheWindowIsDelayed() {
        // Below the appear delay nothing is shown, so nothing is held — the function is never
        // consulted. Above appear+hold the spinner has already outlived its minimum by the time
        // the data lands, so the hold adds nothing. Both ends asserted, because "the window is
        // bounded on both sides" is the claim that makes the trade acceptable.
        val window = motion.fast + motion.base
        assertEquals(400, window)
        assertEquals(0L, loadingHoldRemaining(motion.fast.toLong(), window.toLong(), motion))
        assertEquals(0L, loadingHoldRemaining(motion.fast.toLong(), (window + 1_000).toLong(), motion))
    }

    private enum class Surface { LOADING, CONTENT, EMPTY }

    @Test
    @DisplayName("the hold draws LOADING while the data is NOT loading")
    fun holdOutlivesTheLoadingVerdict() {
        // The whole point of the minimum, and the one row every other test above was blind to: the
        // durations were asserted while the value they produce was discarded downstream. In the
        // entire interval the hold exists for — (140, 400) — the selector has ALREADY left LOADING,
        // so a screen reading its own verdict beside `visible` draws nothing and the spinner
        // flashes for the millisecond the two numbers exist to prevent.
        assertEquals(
            Surface.LOADING,
            deferredSurface(surface = Surface.CONTENT, loadingSurface = Surface.LOADING, visible = true),
        )
        assertEquals(
            Surface.LOADING,
            deferredSurface(surface = Surface.EMPTY, loadingSurface = Surface.LOADING, visible = true),
        )
    }

    @Test
    @DisplayName("the deferral window draws NOTHING — not the spinner, and not the surface under it")
    fun deferralWindowDrawsNothing() {
        // Loading, nothing shown yet: null, so the outgoing frame persists. Returning the raw
        // LOADING verdict here would put the spinner up at once and delete the appear delay; the
        // two are the same value and mean opposite things, which is why the window has its own.
        assertEquals(
            null,
            deferredSurface(surface = Surface.LOADING, loadingSurface = Surface.LOADING, visible = false),
        )
    }

    @Test
    @DisplayName("everything else passes through untouched")
    fun settledSurfacesPassThrough() {
        assertEquals(
            Surface.CONTENT,
            deferredSurface(surface = Surface.CONTENT, loadingSurface = Surface.LOADING, visible = false),
        )
        assertEquals(
            Surface.EMPTY,
            deferredSurface(surface = Surface.EMPTY, loadingSurface = Surface.LOADING, visible = false),
        )
    }
}
