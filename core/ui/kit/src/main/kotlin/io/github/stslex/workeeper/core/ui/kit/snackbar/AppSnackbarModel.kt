// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable

/**
 * One transient message. **There is deliberately no `withDismissAction` here.**
 *
 * There used to be, and it was a lie at every call site that set it: `AppSnackbar` maps
 * [AppSnackbarModel] onto `AppToast`, which draws a message and an optional action and nothing
 * else — because `session-v3f.html`'s `.toast` is `<span>` + `<button>Отменить</button>` with no
 * dismiss mark anywhere. Two features passed `withDismissAction = true` and were silently ignored.
 *
 * The parameter goes rather than an undrawn glyph arriving: adding a dismiss mark would be an
 * appearance decision, and §0.1 gives those to the drawing. Why M3's recommendation no longer
 * binds once the host times the toast itself — B25, resolution.
 *
 * ## [onDismissed] — the undo window's close, for a deferred delete (ED11)
 *
 * The host owns the toast's lifetime (B25), so the host is the only thing that knows when the
 * undo window CLOSED — timeout or user dismissal, both of which mean «Отменить» was declined.
 * A deferred delete (ED11's strict order: timer expires → snackbar dismissed → only then the
 * delete commits) hands its commit here; [action] is its inverse and fires instead of it, never
 * with it. Unlike the deleted `withDismissAction`, this cannot be a silent lie at the call
 * site: the host's own outcome routing ([resolveSnackbarOutcome]) invokes it.
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
 *    → [AppSnackbarModel.onDismissed], and ONLY it.
 */
suspend fun resolveSnackbarOutcome(result: SnackbarResult?, model: AppSnackbarModel) {
    when (result) {
        SnackbarResult.ActionPerformed -> model.action()
        SnackbarResult.Dismissed, null -> model.onDismissed()
    }
}
