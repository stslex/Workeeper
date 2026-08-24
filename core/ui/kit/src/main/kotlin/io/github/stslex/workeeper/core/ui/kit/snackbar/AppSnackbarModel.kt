// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * One transient message: text plus an optional action; the host owns the toast's lifetime.
 * [onDismissed] is the undo window's close, where a deferred delete commits. See v3-editors ED11.
 */
@Stable
data class AppSnackbarModel(
    val message: String,
    val actionLabel: String? = null,
    val action: () -> Unit = { },
    val onDismissed: suspend () -> Unit = { },
)

/**
 * Routes a shown toast's outcome: ActionPerformed runs [AppSnackbarModel.action], dismissal or
 * `null` runs [AppSnackbarModel.onDismissed] under [NonCancellable]. Callback failures stay here.
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
 * [show]s one toast and routes its outcome, requeueing the model with its original epoch if the
 * host dies before routing completes — the queue delivers once and never replays.
 */
suspend fun resolveSnackbarOutcomeOrRequeue(
    delivered: DeliveredSnackbar,
    show: suspend () -> SnackbarResult?,
) {
    // Fence before routing so a quiescing transition cannot miss this callback.
    if (!SnackbarManager.beginResolve()) {
        SnackbarManager.requeue(delivered)
        return
    }
    var routed = false
    try {
        resolveSnackbarOutcome(show(), delivered.model)
        routed = true
    } finally {
        if (!routed) {
            SnackbarManager.requeue(delivered)
        }
        SnackbarManager.endResolve()
    }
}
