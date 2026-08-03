// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.paging

import android.os.SystemClock
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
 *   outgoing frame persists. Measured worst case for a real load on this app is **61 ms on a
 *   `debug` build** (a cold `all-trainings` entry, `refresh = Loading` → `NotLoading`,
 *   device-instrumented); Home's warm path is 23 ms on the same build. Both numbers state their
 *   build type because AGENTS.md requires it of any performance number, and here it cuts the safe
 *   way: **release loads are not slower than debug ones**, so a threshold that clears the debug
 *   distribution by 2.3× clears the release one by at least as much. A release re-measure would
 *   move the margin up, never the threshold down.
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
 * ## Private on purpose — the screens call [rememberDeferredSurface]
 *
 * A `Boolean` beside a surface enum is the shape that loses the hold: the caller writes
 * `if (surface == LOADING && visible)`, which is false the instant the data stops loading, and the
 * minimum then holds a value nothing draws. There is nothing to fix at four call sites if there is
 * only one thing to call.
 *
 * @param loading whether the data is loading — typically `surface == LOADING` from a screen's list
 *  surface selector.
 * @return whether the loading treatment should be drawn right now.
 */
@Composable
private fun rememberLoadingVisible(loading: Boolean): Boolean {
    val motion = AppUi.motion
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(loading) {
        when (val step = loadingStep(loading, visible, shownAt, SystemClock.elapsedRealtime(), motion)) {
            is LoadingStep.ShowAfter -> {
                delay(step.delayMillis)
                shownAt = SystemClock.elapsedRealtime()
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
 * The surface a list should **draw**, which is not always the surface its data is in.
 *
 * This is the whole call-site API: hand it the selector's verdict and the loading verdict, branch on
 * what comes back, and the two numbers above are then unavoidable rather than merely available.
 *
 * | returns | when | the screen draws |
 * |---|---|---|
 * | `loadingSurface` | shown: appear delay elapsed, minimum hold not | the loading treatment |
 * | the last settled verdict | the deferral window, something already drawn | **that**, unchanged |
 * | `null` | the deferral window on a cold open — nothing drawn yet | **nothing** |
 * | `surface` | anything else | that surface |
 *
 * ## The deferral window keeps the outgoing surface; it does not blank
 *
 * A screen renders this verdict by *removing* the block it names — every call site opens
 * `deferredSurface ?: return` — so `null` does not mean "leave the screen alone", it means "delete
 * what is there". Compose keeps no frame behind a composable that has left composition. The two
 * readings are identical on a cold open, where there is nothing to delete, and they diverge on
 * every transition **from** a settled empty-region verdict. The witness is retry: tapping retry on
 * a cold-open error moves the verdict `REFRESH_ERROR → LOADING` while the appear delay is still
 * running, so the error was removed at once and the region sat blank for up to 140 ms before the
 * spinner arrived — a blank flash in the file whose whole subject is not flashing.
 *
 * So the window returns the **last settled verdict** and `null` only before there is one. Note what
 * this cannot do: a selector returns its content verdict only at `itemCount > 0` and `LOADING` only
 * at `itemCount == 0`, so the retained verdict is an empty-region one in every reachable case, and
 * the rows cannot be what persists.
 *
 * **The residual, measured rather than assumed:** [deferredSurface]'s arithmetic is gated — the
 * retention reddens `LoadingVisibilityTest` by name — but the *wiring* from this composable into it
 * is not. Replacing `lastSettled = lastSettled` with `lastSettled = null` here leaves the whole
 * module's suite **green**, and that is a hole rather than a no-op: it changes what the screen
 * draws, which is §27's own discriminator. Same shape as the delays below, same cause — closing it
 * needs a clock-driven Compose test this module does not have.
 *
 * ## The hold draws LOADING while the data is NOT loading. That is the point
 *
 * The minimum exists for loads in the open interval (140, 400) ms — the ones that finish *after*
 * the spinner appears and *before* it has been up long enough to read. In every one of them the
 * selector has already moved to `CONTENT` (or `FIRST_RUN`, or an error) by the time the hold is
 * doing anything, so a screen that re-derives its own `surface == LOADING` beside this value draws
 * nothing for the rest of the hold and the spinner flashes for the millisecond the two numbers
 * exist to prevent. Branch on **this** return value and nothing else; the raw verdict is not what
 * is on screen.
 *
 * Both halves are needed and neither is sufficient. Call this where it **outlives** the loading
 * state — a call sited inside `if (surface == LOADING) { … }` leaves composition at the instant the
 * hold is supposed to start, so the minimum silently does nothing however the result is read.
 *
 * @param surface the list-surface verdict for the data as it is now.
 * @param loadingSurface that enum's loading verdict.
 */
@Composable
fun <T : Any> rememberDeferredSurface(surface: T, loadingSurface: T): T? {
    val visible = rememberLoadingVisible(surface == loadingSurface)
    // The outgoing frame, kept because Compose does not keep it for us. A screen renders this
    // verdict by REMOVING the block it names — `deferredSurface ?: return` at all four sites — so a
    // `null` here is not "leave what is there", it is "delete it". On a cold open that is right and
    // is what the appear delay is for: there is nothing to delete. On any transition FROM a settled
    // empty-region verdict it was wrong, and the retry path is where a user meets it — tap retry on
    // a cold-open error and the error was removed at once, leaving the region blank for up to 140ms
    // before the spinner arrived. Found in review on #212.
    var lastSettled by remember { mutableStateOf<T?>(null) }
    if (surface != loadingSurface) lastSettled = surface
    return deferredSurface(
        surface = surface,
        loadingSurface = loadingSurface,
        visible = visible,
        lastSettled = lastSettled,
    )
}

/**
 * Which body a list screen draws, from [rememberDeferredSurface]'s verdict.
 *
 * **The rows and the loading treatment are alternatives, not layers.** The minimum hold keeps
 * `loadingSurface` on screen *after* the data has arrived, so a screen whose rows are composed
 * independently of this verdict draws them under a spinner for the rest of the hold — the flash the
 * two numbers exist to remove, wearing an overlay. The hold is only a hold if the content it is
 * holding back is actually held back.
 *
 * [REGION] covers `null` as well: in the deferral window the region draws either the outgoing
 * verdict or, on a cold open, nothing at all — and rows are the one thing that must not appear
 * there in either case, since a list that pops in at 40 ms is exactly what the appear delay is
 * protecting the eye from.
 *
 * Every surface selector on this arc returns its content verdict first (`itemCount > 0 -> CONTENT`),
 * so no verdict other than the content one can coexist with rows; gating on equality is therefore
 * total rather than a heuristic.
 */
enum class ListBody {
    /** The list's own items. */
    ROWS,

    /**
     * The empty region: loading treatment, error, empty state — in the deferral window whichever of
     * those was last settled, and nothing at all only before one has been.
     */
    REGION,
}

/** See [ListBody]. Pure, so the alternative is assertable without a screen. */
fun <T : Any> listBody(surface: T?, contentSurface: T): ListBody =
    if (surface == contentSurface) ListBody.ROWS else ListBody.REGION

/**
 * See [rememberDeferredSurface]. Pure, so the table there is asserted rather than described —
 * including the row that carries the hold, which is the one a golden cannot reach and a screen can
 * silently discard.
 *
 * @param lastSettled the last non-loading verdict this call site drew, or `null` if it has not
 *  drawn one yet. **This is what makes "the outgoing frame persists" true rather than aspirational**
 *  — see [rememberDeferredSurface]'s deferral row. A caller that passes `null` unconditionally gets
 *  the blank the appear delay exists to avoid.
 */
internal fun <T : Any> deferredSurface(
    surface: T,
    loadingSurface: T,
    visible: Boolean,
    lastSettled: T?,
): T? = when {
    visible -> loadingSurface
    surface == loadingSurface -> lastSettled
    else -> surface
}

/**
 * What the deferral should do next — **pure, so both durations are assertable.**
 *
 * The composable above is dispatch only, and it must stay that way: **do not inline these delays
 * back into it.** A duration written straight into a `delay(...)` call inside a composable is
 * reachable by no test in this module — not by a golden, which photographs one settled frame, and
 * not by the hold arithmetic, which never sees it. Returning the durations as a value is what puts
 * them where a test can read them.
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
 *
 * **Fed from `SystemClock.elapsedRealtime()`, not the wall clock.** A wall-clock adjustment between
 * the spinner appearing and the load finishing lands directly in this subtraction: backwards, the
 * remainder inflates by the size of the correction and the spinner sits there; forwards, the hold is
 * released early. Neither is likely inside 260 ms and neither is bounded, which is the reason to use
 * the clock that cannot move rather than the one that rarely does.
 */
internal fun loadingHoldRemaining(
    shownAtMillis: Long,
    nowMillis: Long,
    motion: AppMotion,
): Long = (motion.base.toLong() - (nowMillis - shownAtMillis)).coerceAtLeast(0L)
