// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

object SnackbarManager {

    /**
     * The toast queue: unbounded, FIFO, and never dropping while the process lives.
     * [AppSnackbarModel.onDismissed] carries a deferred delete's COMMIT (ED11), not just
     * feedback — a dropped entry is not a stale toast skipped, it is a confirmed delete
     * that silently never runs after the screen that promised it popped. So neither a full
     * buffer nor a burst may evict: entries are tiny, every producer is a user gesture,
     * and the single collector (`App.kt`) drains one per toast lifetime. DO NOT cap this
     * queue or give it an overflow policy — any bound reintroduces the eviction, and
     * [SnackbarManagerTest] holds a burst case that goes red on one. Process death cancels
     * everything queued — D-OPEN-10's recorded shape, unchanged.
     */
    private val queue = Channel<AppSnackbarModel>(capacity = Channel.UNLIMITED)

    /**
     * Count of [resolveSnackbarOutcomeOrRequeue] routings currently in flight. A resolve's
     * dismissed branch runs a deferred delete's COMMIT under `NonCancellable`, so it survives its
     * collector's cancellation — this counter is what lets the Phase 5 replacement machine's
     * Quiescing stage AWAIT that commit before closing the database
     * (`kmp-phase-5-startup-processor.md` §8.4 step 3) instead of racing it.
     */
    private val inFlightResolves = MutableStateFlow(0)

    /**
     * Approximate count of queued-but-undelivered models. Maintained on send/receive; read at
     * Quiescing to RECORD (never silently drop) models that will cross a generation boundary —
     * their closures captured the old generation's repositories, and executing one after a
     * replacement follows the ED11 interruption semantics (the deferred delete reverts), which is
     * the same designed outcome as process death mid-window (D-OPEN-10).
     */
    private val queuedCount = MutableStateFlow(0)

    val snackbar: Flow<AppSnackbarModel> = queue.receiveAsFlow()
        .onEach { queuedCount.value = (queuedCount.value - 1).coerceAtLeast(0) }

    fun showSnackbar(model: AppSnackbarModel) {
        if (queue.trySend(model).isSuccess) {
            queuedCount.value += 1
        }
    }

    /** The number of queued-but-undelivered models right now (see [queuedCount]). */
    val pendingModelCount: Int get() = queuedCount.value

    /** Suspends until no [resolveSnackbarOutcomeOrRequeue] routing is in flight. */
    suspend fun awaitInFlightResolves() {
        inFlightResolves.first { it == 0 }
    }

    internal fun resolveStarted() {
        inFlightResolves.value += 1
    }

    internal fun resolveFinished() {
        inFlightResolves.value = (inFlightResolves.value - 1).coerceAtLeast(0)
    }

    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        action: () -> Unit = {},
    ): Unit = showSnackbar(
        AppSnackbarModel(
            message = message,
            actionLabel = actionLabel,
            action = action,
        ),
    )
}
