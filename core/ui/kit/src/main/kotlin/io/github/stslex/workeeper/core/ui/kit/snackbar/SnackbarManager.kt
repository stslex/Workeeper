// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicLong

object SnackbarManager {

    private val logger = Log.tag("SnackbarManager")

    /** One queued toast, stamped with the generation epoch it was produced under (Phase 5). */
    private class Queued(val model: AppSnackbarModel, val epoch: Long)

    /**
     * The toast queue: unbounded, FIFO, and never dropping while the process lives — except at
     * a COMMITTED generation handover (see [advanceGenerationEpoch]).
     * [AppSnackbarModel.onDismissed] carries a deferred delete's COMMIT (ED11), not just
     * feedback — a dropped entry is not a stale toast skipped, it is a confirmed delete
     * that silently never runs after the screen that promised it popped. So neither a full
     * buffer nor a burst may evict: entries are tiny, every producer is a user gesture,
     * and the single collector (`App.kt`) drains one per toast lifetime. DO NOT cap this
     * queue or give it an overflow policy — any bound reintroduces the eviction, and
     * [SnackbarManagerTest] holds a burst case that goes red on one. Process death cancels
     * everything queued — D-OPEN-10's recorded shape, unchanged.
     */
    private val queue = Channel<Queued>(capacity = Channel.UNLIMITED)

    /**
     * Count of [resolveSnackbarOutcomeOrRequeue] routings currently in flight. A resolve's
     * dismissed branch runs a deferred delete's COMMIT under `NonCancellable`, so it survives its
     * collector's cancellation — this counter is what lets the Phase 5 replacement machine's
     * Quiescing stage AWAIT that commit before anything irreversible
     * (`kmp-phase-5-startup-processor.md` §8.4 step 3) instead of racing it.
     */
    private val inFlightResolves = MutableStateFlow(0)

    /** Approximate count of queued-but-undelivered models. Maintained on send/receive. */
    private val queuedCount = MutableStateFlow(0)

    /**
     * The generation epoch (Phase 5 R2 — spec §8.4 Quiescing step 3). Every queued model is
     * stamped with the epoch current at enqueue; delivery FILTERS OUT models from an older
     * epoch, so a callback whose closure captured generation N's repositories can never execute
     * inside generation N+1. The stamp-at-enqueue rule also covers the requeue path: a model
     * requeued by a dying collector re-enters under the epoch still current during quiesce
     * (the epoch only advances AFTER the resolve drain completed), keeping its original tag.
     */
    private val generationEpoch = AtomicLong(0)

    val snackbar: Flow<AppSnackbarModel> = queue.receiveAsFlow()
        .mapNotNull { queued ->
            queuedCount.value = (queuedCount.value - 1).coerceAtLeast(0)
            if (queued.epoch == generationEpoch.get()) {
                queued.model
            } else {
                // The documented interruption semantics (ED11 / D-OPEN-10): a model that
                // crossed a COMMITTED generation handover is discarded — its deferred-delete
                // commit never runs and the delete reverts, the same designed outcome as
                // process death mid-window. Logged, never silent.
                logger.w {
                    "discarding snackbar '${queued.model.message}' from a replaced generation " +
                        "(epoch ${queued.epoch} < ${generationEpoch.get()})"
                }
                null
            }
        }

    fun showSnackbar(model: AppSnackbarModel) {
        if (queue.trySend(Queued(model, generationEpoch.get())).isSuccess) {
            queuedCount.value += 1
        }
    }

    /** The number of queued-but-undelivered models right now (stale-epoch entries included). */
    val pendingModelCount: Int get() = queuedCount.value

    /** Suspends until no [resolveSnackbarOutcomeOrRequeue] routing is in flight. */
    suspend fun awaitInFlightResolves() {
        inFlightResolves.first { it == 0 }
    }

    /**
     * Marks a COMMITTED generation handover: models queued under earlier epochs are discarded
     * lazily at delivery (never executed against the new generation). Called by the runtime
     * ONLY after a successful handover — an ABORTED transition never advances the epoch, so the
     * queued models are preserved and deliver normally when the outgoing generation resumes.
     */
    fun advanceGenerationEpoch() {
        generationEpoch.incrementAndGet()
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
