// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.paging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.stslex.workeeper.core.ui.kit.theme.AppMotion
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.coroutines.delay

/**
 * Whether a loading treatment should be **on screen**, as opposed to whether the data is loading.
 *
 * ## Two numbers, because one does not solve it
 *
 * A delay alone stops the spinner appearing for fast loads and leaves a load just over the
 * threshold flashing it for a handful of milliseconds — and flashing is the complaint, so a
 * one-number fix moves the problem rather than removing it. Once the spinner is shown it therefore
 * **holds for a minimum**, whatever the data does.
 *
 * Both come off the motion scale rather than being invented (§5, "anything that needs a duration
 * not on this list is a design decision"):
 *
 * - **Appear delay** = [AppMotion.fast], **140 ms** — nothing renders below it, so the
 *   outgoing frame persists. Measured worst case for a real load on this app is **61 ms** (a cold
 *   `all-trainings` entry, `refresh = Loading` → `NotLoading`, device-instrumented), and Home's warm
 *   path is 23 ms, so 140 clears the real distribution by 2.3× and the spinner never appears at all
 *   on the loads that were flashing.
 * - **Minimum hold** = [AppMotion.base], **260 ms** — once visible, visible for at least this
 *   long. The same rung the app's crossfades already run at, so a spinner that does appear lasts
 *   about as long as everything else that moves.
 *
 * ## The arithmetic in between, which is where a two-number rule is judged
 *
 * For a load of duration `T`, with `D = 140` and `H = 260`:
 *
 * | `T` | spinner shown | visible for | content delayed by |
 * |---|---|---|---|
 * | ≤ 140 | never | 0 | 0 |
 * | 141 | 140 → 400 | 260 | **259** ← the worst case |
 * | 300 | 140 → 400 | 260 | 100 |
 * | 400 | 140 → 400 | 260 | 0 |
 * | > 400 | 140 → `T` | `T` − 140 | 0 |
 *
 * So the **maximum** delay this can add is `H` (at `T = D + ε`), it is bounded, and only loads
 * landing in the open interval (140, 400) are delayed at all. That trade is the whole decision: a
 * load of 141 ms now takes 400 ms to resolve visually, in exchange for no load ever flashing. The
 * alternative — a bare delay — leaves exactly that 141 ms load showing a spinner for 1 ms.
 *
 * ## Why this cannot be gated by a picture
 *
 * §10.4: a delay is invisible to a golden. Paparazzi renders one frame and has no clock, so every
 * number here is unreachable from an image. [loadingStep] and [loadingHoldRemaining] are the pure
 * parts and carry both durations, so a mutation of either reddens; the constants are asserted
 * against the scale they claim to come from, and the worst-case row of the table above is asserted
 * as arithmetic — see `LoadingVisibilityTest`.
 *
 * ## Call it where it OUTLIVES the loading state
 *
 * The hold is implemented by this composable staying in composition after `loading` goes false. Put
 * the call inside the branch that renders the spinner — `if (surface == LOADING) { if
 * (rememberLoadingVisible(true)) … }` — and it leaves composition at the very moment it is supposed
 * to start holding, so the minimum silently does nothing. The first version of every call site here
 * had exactly that shape. Call it once, high enough that it survives the transition, and branch on
 * the result.
 *
 * @param loading whether the data is loading — typically `surface == LOADING` from a screen's list
 *  surface selector.
 * @return whether the loading treatment should be drawn right now.
 */
@Composable
fun rememberLoadingVisible(loading: Boolean): Boolean {
    val motion = AppUi.motion
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(loading) {
        when (val step = loadingStep(loading, visible, shownAt, System.currentTimeMillis(), motion)) {
            is LoadingStep.ShowAfter -> {
                delay(step.delayMillis)
                shownAt = System.currentTimeMillis()
                visible = true
            }

            is LoadingStep.HideAfter -> {
                delay(step.delayMillis)
                visible = false
            }

            LoadingStep.Nothing -> Unit
        }
    }
    return visible
}

/**
 * What the deferral should do next — **pure, so both durations are assertable.**
 *
 * The composable above is dispatch only. That split is not tidiness: with `delay(motion.fast)`
 * written inline, mutating it to `delay(0)` — which restores the exact flash this file exists to
 * remove — came back **green**, because the tests could only reach the hold arithmetic and the
 * constants. Moving the durations into a returned value puts them where a test can read them.
 *
 * **The residual, stated rather than papered over:** nothing here proves the composable *honours*
 * the returned delay. Replacing `delay(step.delayMillis)` with `delay(0)` still passes, and closing
 * that needs a clock-driven Compose test this module does not have. Same shape as
 * `FadeToTransparentRule`'s laundering gap — the pure function and the device measurement are the
 * guard together, and neither is sufficient alone.
 */
internal sealed interface LoadingStep {

    /** Loading began and nothing is shown: wait, then show. */
    data class ShowAfter(val delayMillis: Long) : LoadingStep

    /** Loading ended while shown: wait out the remaining minimum, then hide. */
    data class HideAfter(val delayMillis: Long) : LoadingStep

    /** Already in the right state. */
    data object Nothing : LoadingStep
}

/** See [LoadingStep]. Pure. */
internal fun loadingStep(
    loading: Boolean,
    visible: Boolean,
    shownAtMillis: Long,
    nowMillis: Long,
    motion: AppMotion,
): LoadingStep = when {
    loading && !visible -> LoadingStep.ShowAfter(motion.fast.toLong())
    !loading && visible -> LoadingStep.HideAfter(
        loadingHoldRemaining(shownAtMillis, nowMillis, motion),
    )

    else -> LoadingStep.Nothing
}

/**
 * Milliseconds the loading treatment must still be held, given when it appeared and the time now.
 *
 * Clamped at zero: a spinner already up for longer than the minimum is released immediately, and a
 * negative value would mean the arithmetic had gone wrong somewhere nothing could see it.
 */
internal fun loadingHoldRemaining(
    shownAtMillis: Long,
    nowMillis: Long,
    motion: AppMotion,
): Long = (motion.base.toLong() - (nowMillis - shownAtMillis)).coerceAtLeast(0L)
