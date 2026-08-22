// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * One transient message. **There is deliberately no `withDismissAction` here, and none may
 * be added.** `AppSnackbar` maps [AppSnackbarModel] onto `AppToast`, which draws a message
 * and an optional action and nothing else — `session-v3f.html`'s `.toast` is `<span>` +
 * `<button>Отменить</button>` with no dismiss mark anywhere, a dismiss mark would be an
 * appearance decision, and §0.1 gives those to the drawing. Why M3's recommendation does
 * not bind once the host times the toast itself — B25, resolution.
 *
 * ## [onDismissed] — the undo window's close, for a deferred delete (ED11)
 *
 * The host owns the toast's lifetime (B25), so the host is the only thing that knows when the
 * undo window CLOSED — timeout or user dismissal, both of which mean «Отменить» was declined.
 * A deferred delete (ED11's strict order: timer expires → snackbar dismissed → only then the
 * delete commits) hands its commit here; [action] is its inverse and fires instead of it,
 * never with it. The host's own outcome routing ([resolveSnackbarOutcome]) guarantees the
 * invocation — a call site cannot wire this and have it silently ignored.
 *
 * Suspend, and run inside the host's collector: nothing else outlives the popped screen that
 * scheduled the delete without outliving the process — which is D-OPEN-10 for free, since a
 * process death cancels the collector and the commit simply never ran.
 */
@Stable
data class AppSnackbarModel(
    val message: String,
    val actionLabel: String? = null,
    val action: () -> Unit = { },
    val onDismissed: suspend () -> Unit = { },
)

/**
 * The host's outcome routing, as a named function so the wiring is directly assertable (§27:
 * splitting a surface out is what makes its selector invisible — so the selector is named):
 *
 *  - [SnackbarResult.ActionPerformed] → [AppSnackbarModel.action], and ONLY it;
 *  - [SnackbarResult.Dismissed] and `null` — the host's timeout cancelled the show —
 *    → [AppSnackbarModel.onDismissed], and ONLY it, run [NonCancellable]: the undo window
 *    has CLOSED by then, so the commit it carries must not be torn mid-transaction by the
 *    host dying — a commit either never starts (the requeue's case, below) or finishes.
 *
 * Both callbacks run inside the app-level collector — the one coroutine every toast in the
 * process shares, and the only thing that outlives the screen that scheduled a deferred
 * delete. So a callback's failure is CONTAINED here: a throwing commit (B-E7's RESTRICT
 * gap can reach one until its arc widens the eligibility predicate) degrades to the failure
 * surfacing nothing — B17/B21's recorded class — rather than cancelling the collector,
 * which would crash the composition and take every later toast with it.
 * [CancellationException] is the collector's own stop signal, never a callback failure,
 * and still propagates.
 */
suspend fun resolveSnackbarOutcome(result: SnackbarResult?, model: AppSnackbarModel) {
    try {
        when (result) {
            SnackbarResult.ActionPerformed -> model.action()
            SnackbarResult.Dismissed, null -> withContext(NonCancellable) { model.onDismissed() }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        // Contained by the KDoc's contract: the pipeline outlives the failure.
    }
}

/**
 * One toast's whole lifetime at the host: [show] displays it (the host times it — B25) and
 * the result routes through [resolveSnackbarOutcome] — and if the host dies BETWEEN taking
 * the model off the queue and completing that routing, the model goes back on the queue for
 * the collector that replaces it. The queue does not replay ([SnackbarManager]'s channel
 * delivers once), and the collector is a `LaunchedEffect` that dies with its composition —
 * so without the requeue, an activity recreated under a visible toast drops the model with
 * neither callback run, and a deferred delete's confirmed commit silently never happens
 * while the process is still alive. Only process death may drop it — D-OPEN-10's recorded
 * shape, unchanged.
 *
 * The requeue covers the model the host dies holding BEFORE the outcome is known — a
 * commit that BEGAN always finishes ([resolveSnackbarOutcome] runs it [NonCancellable]),
 * so a requeued model is one whose window genuinely never closed. A callback that throws
 * [CancellationException] of its own still escapes and requeues: that is the collector's
 * stop signal, not an outcome.
 */
suspend fun resolveSnackbarOutcomeOrRequeue(
    model: AppSnackbarModel,
    show: suspend () -> SnackbarResult?,
) {
    var routed = false
    // In-flight accounting brackets the WHOLE routing, including the NonCancellable commit inside
    // resolveSnackbarOutcome — the Phase 5 Quiescing stage awaits this count reaching zero before
    // a database replacement may close the generation's database (spec §8.4 step 3).
    SnackbarManager.resolveStarted()
    try {
        resolveSnackbarOutcome(show(), model)
        routed = true
    } finally {
        if (!routed) {
            SnackbarManager.showSnackbar(model)
        }
        SnackbarManager.resolveFinished()
    }
}
