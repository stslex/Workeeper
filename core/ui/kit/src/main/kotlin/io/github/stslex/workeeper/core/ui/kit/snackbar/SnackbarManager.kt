// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import java.util.concurrent.atomic.AtomicLong

/** Toast delivered with its enqueue epoch, preserved through requeue. */
data class DeliveredSnackbar internal constructor(
    val model: AppSnackbarModel,
    internal val epoch: Long,
)

object SnackbarManager {

    private val logger = Log.tag("SnackbarManager")

    /** One queued toast, stamped with the generation epoch it was produced under. */
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

    /** Linearizable routing count and admission fence. */
    private data class ResolveGate(val inFlight: Int = 0, val fenced: Boolean = false)

    private val resolveGate = MutableStateFlow(ResolveGate())

    /** Approximate count of queued-but-undelivered models. Maintained on send/receive. */
    private val queuedCount = MutableStateFlow(0)

    /** Epoch advances before successor publication, excluding stale callbacks at delivery. */
    private val generationEpoch = AtomicLong(0)

    val snackbar: Flow<DeliveredSnackbar> = queue.receiveAsFlow()
        .mapNotNull { queued ->
            queuedCount.update { count -> (count - 1).coerceAtLeast(0) }
            val epoch = generationEpoch.get()
            if (queued.epoch == epoch) {
                DeliveredSnackbar(queued.model, queued.epoch)
            } else {
                // A committed handover discards stale callbacks rather than running them in N+1.
                logger.w {
                    "discarding snackbar '${queued.model.message}' from a replaced generation " +
                        "(epoch ${queued.epoch} < $epoch)"
                }
                null
            }
        }

    fun showSnackbar(model: AppSnackbarModel) {
        enqueue(model, generationEpoch.get())
    }

    /** Requeues with original epoch so a stale callback cannot become current. */
    internal fun requeue(delivered: DeliveredSnackbar) {
        enqueue(delivered.model, delivered.epoch)
    }

    private fun enqueue(model: AppSnackbarModel, epoch: Long) {
        if (queue.trySend(Queued(model, epoch)).isSuccess) {
            queuedCount.update { count -> count + 1 }
        }
    }

    /** The number of queued-but-undelivered models right now (stale-epoch entries included). */
    val pendingModelCount: Int get() = queuedCount.value

    /**
     * Atomically observes "no resolve in flight" AND closes admission for new ones. Suspends
     * until both hold; the caller bounds it. Every path that does not commit MUST call
     * [unfenceResolves].
     */
    suspend fun fenceResolves() {
        while (true) {
            resolveGate.first { gate -> gate.inFlight == 0 }
            val after = resolveGate.updateAndGet { gate ->
                if (gate.inFlight == 0) gate.copy(fenced = true) else gate
            }
            if (after.fenced) return
        }
    }

    /** Reopens resolve admission — an ABORTED transition, or a completed handover. */
    fun unfenceResolves() {
        resolveGate.update { gate -> gate.copy(fenced = false) }
    }

    /**
     * Admits one routing. Returns false when the gate is fenced: the caller must NOT run the
     * outcome and must requeue the model with its own epoch instead.
     */
    internal fun beginResolve(): Boolean {
        val after = resolveGate.updateAndGet { gate ->
            if (gate.fenced) gate else gate.copy(inFlight = gate.inFlight + 1)
        }
        return !after.fenced
    }

    internal fun endResolve() {
        resolveGate.update { gate -> gate.copy(inFlight = (gate.inFlight - 1).coerceAtLeast(0)) }
    }

    /**
     * Marks a COMMITTED generation handover: models queued under earlier epochs are discarded
     * lazily at delivery (never executed against the new generation). Called by the runtime
     * ONLY after a successful handover and BEFORE the successor is published — an ABORTED
     * transition never advances the epoch, so the queued models are preserved and deliver
     * normally when the outgoing generation resumes.
     */
    fun advanceGenerationEpoch() {
        generationEpoch.incrementAndGet()
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
