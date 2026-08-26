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
     * The toast queue: unbounded FIFO, dropping only at a committed generation handover.
     * GUARD: never cap it or give it an overflow policy; an entry can carry a deferred commit.
     */
    private val queue = Channel<Queued>(capacity = Channel.UNLIMITED)

    /** Linearizable routing count and admission fence. */
    private data class ResolveGate(val inFlight: Int = 0, val fenced: Boolean = false)

    private val resolveGate = MutableStateFlow(ResolveGate())

    /** Approximate count of queued-but-undelivered models. Maintained on send/receive. */
    private val queuedCount = MutableStateFlow(0)

    /**
     * Epoch advances before successor publication, excluding stale callbacks at delivery.
     * GUARD: advance only through `update` — a read-then-set loses increments under concurrency.
     */
    private val generationEpoch = MutableStateFlow(0L)

    val snackbar: Flow<DeliveredSnackbar> = queue.receiveAsFlow()
        .mapNotNull { queued ->
            queuedCount.update { count -> (count - 1).coerceAtLeast(0) }
            val epoch = generationEpoch.value
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
        enqueue(model, generationEpoch.value)
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
     * Atomically observes "no resolve in flight" and closes admission for new ones; the caller
     * bounds the suspend. GUARD: every path that does not commit MUST call [unfenceResolves].
     */
    @SnackbarGenerationTransition
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
    @SnackbarGenerationTransition
    fun unfenceResolves() {
        resolveGate.update { gate -> gate.copy(fenced = false) }
    }

    /**
     * Admits one routing; false means fenced — do not run the outcome, requeue the model with
     * its own epoch instead.
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
     * Marks a COMMITTED generation handover; earlier-epoch models are discarded at delivery.
     * An aborted transition never advances the epoch, so its queued models still deliver.
     */
    @SnackbarGenerationTransition
    fun advanceGenerationEpoch() {
        generationEpoch.update { epoch -> epoch + 1 }
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
