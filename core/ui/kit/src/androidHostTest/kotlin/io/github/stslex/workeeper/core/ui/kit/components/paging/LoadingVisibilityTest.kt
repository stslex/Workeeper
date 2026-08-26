// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.paging

import io.github.stslex.workeeper.core.ui.kit.theme.provideAppMotion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The loading deferral's two numbers and the arithmetic between them; a delay is invisible to a
 * golden, so the values are named and asserted directly. See the v3 redesign spec §10.4.
 */
internal class LoadingVisibilityTest {

    private val motion = provideAppMotion()

    @Test
    @DisplayName("both numbers come off the motion scale, and they are the two they claim to be")
    fun bothNumbersAreOnTheScale() {
        // §5: a duration off the scale is a design decision; catches 140 quietly becoming 150.
        assertEquals(140, motion.fast)
        assertEquals(260, motion.base)
        assertTrue(motion.fast in setOf(motion.fast, motion.base, motion.slow))
    }

    @Test
    @DisplayName("the appear delay clears the measured worst-case load with margin")
    fun appearDelayClearsMeasuredLoads() {
        // Device-instrumented: a cold all-trainings `refresh` took 61 ms; the delay must clear it.
        val worstMeasuredLoadMs = 61
        assertTrue(motion.fast > worstMeasuredLoadMs) {
            "appear delay ${motion.fast}ms must exceed the measured ${worstMeasuredLoadMs}ms load"
        }
    }

    @Test
    @DisplayName("loading with nothing shown: wait the APPEAR DELAY, then show")
    fun stepShowsAfterTheAppearDelay() {
        // With `delay(motion.fast)` inline, mutating it to `delay(0)` came back green.
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
        // Holding for 260 ms after loading ends would be the same flash with a longer tail.
        assertEquals(160L, loadingHoldRemaining(shownAtMillis = 1_000L, nowMillis = 1_100L, motion = motion))
    }

    @Test
    @DisplayName("a spinner already past the minimum is released immediately, never negatively")
    fun holdClampsAtZero() {
        assertEquals(0L, loadingHoldRemaining(shownAtMillis = 1_000L, nowMillis = 1_260L, motion = motion))
        // Clamped rather than negative: `delay(-1)` is a silent no-op, not a throw.
        assertEquals(0L, loadingHoldRemaining(shownAtMillis = 1_000L, nowMillis = 9_999L, motion = motion))
    }

    @Test
    @DisplayName("the worst case: a 141ms load costs 259ms of added delay, and that is the ceiling")
    fun worstCaseArithmetic() {
        // A load finishing 1 ms after the spinner appears is held for the full minimum.
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
        // Both ends: below the appear delay nothing shows, above appear+hold the hold adds nothing.
        val window = motion.fast + motion.base
        assertEquals(400, window)
        assertEquals(0L, loadingHoldRemaining(motion.fast.toLong(), window.toLong(), motion))
        assertEquals(0L, loadingHoldRemaining(motion.fast.toLong(), (window + 1_000).toLong(), motion))
    }

    private enum class Surface { LOADING, CONTENT, EMPTY, ERROR }

    @Test
    @DisplayName("the hold draws LOADING while the data is NOT loading")
    fun holdOutlivesTheLoadingVerdict() {
        // In the whole (140, 400) interval the selector has already left LOADING; the hold wins.
        assertEquals(
            Surface.LOADING,
            deferredSurface(
                surface = Surface.CONTENT,
                loadingSurface = Surface.LOADING,
                visible = true,
                lastSettled = Surface.CONTENT,
            ),
        )
        assertEquals(
            Surface.LOADING,
            deferredSurface(
                surface = Surface.EMPTY,
                loadingSurface = Surface.LOADING,
                visible = true,
                lastSettled = Surface.EMPTY,
            ),
        )
    }

    @Test
    @DisplayName("the deferral window draws NOTHING on a cold open — nothing has been settled yet")
    fun deferralWindowDrawsNothingOnColdOpen() {
        // Loading with nothing ever drawn: null; the raw verdict would delete the appear delay.
        assertEquals(
            null,
            deferredSurface(
                surface = Surface.LOADING,
                loadingSurface = Surface.LOADING,
                visible = false,
                lastSettled = null,
            ),
        )
    }

    @Test
    @DisplayName("the deferral window KEEPS the outgoing surface when there is one — the retry blank")
    fun deferralWindowKeepsTheOutgoingSurface() {
        // Tapping retry on a cold-open error would blank the region for up to 140ms without this.
        assertEquals(
            Surface.ERROR,
            deferredSurface(
                surface = Surface.LOADING,
                loadingSurface = Surface.LOADING,
                visible = false,
                lastSettled = Surface.ERROR,
            ),
        )
        // The WINDOW keeps it, not the hold: once the delay elapses the spinner replaces the error.
        assertEquals(
            Surface.LOADING,
            deferredSurface(
                surface = Surface.LOADING,
                loadingSurface = Surface.LOADING,
                visible = true,
                lastSettled = Surface.ERROR,
            ),
        )
    }

    @Test
    @DisplayName("the HOLD withholds the rows, not just the surface verdict")
    fun holdWithholdsRows() {
        // The hold keeps LOADING after the data says CONTENT, so the rows must be withheld too.
        assertEquals(ListBody.REGION, listBody(Surface.LOADING, Surface.CONTENT))
    }

    @Test
    @DisplayName("the deferral window withholds the rows too — nothing draws at all")
    fun deferralWindowWithholdsRows() {
        // Rows must not appear in the window: a list popping in at 40ms is what the delay prevents.
        assertEquals(ListBody.REGION, listBody(null, Surface.CONTENT))
    }

    @Test
    @DisplayName("rows draw for the content verdict, and for nothing else")
    fun rowsDrawForContentOnly() {
        assertEquals(ListBody.ROWS, listBody(Surface.CONTENT, Surface.CONTENT))
        assertEquals(ListBody.REGION, listBody(Surface.EMPTY, Surface.CONTENT))
    }

    @Test
    @DisplayName("everything else passes through untouched")
    fun settledSurfacesPassThrough() {
        assertEquals(
            Surface.CONTENT,
            deferredSurface(
                surface = Surface.CONTENT,
                loadingSurface = Surface.LOADING,
                visible = false,
                lastSettled = Surface.EMPTY,
            ),
        )
        assertEquals(
            Surface.EMPTY,
            deferredSurface(
                surface = Surface.EMPTY,
                loadingSurface = Surface.LOADING,
                visible = false,
                lastSettled = Surface.CONTENT,
            ),
        )
    }
}
