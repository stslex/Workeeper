// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.PhoneDecodeResult
import io.github.stslex.workeeper.core.wear.protocol.WearEnvelope
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.feature.wear_bridge.PhoneWorkoutBridge
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeWorkDepsHolder
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeWorkLease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * Routes one Data Layer request to the phone authority and answers with its encoded response, or
 * with no bytes when there is nothing the phone may answer (wear-paired-transport.md §6.2).
 *
 * Pure Kotlin: no Android or Data Layer type, so every branch is host-tested. It adds no
 * validation of its own; authentication, idempotency, leases and receipts stay in the bridge.
 */
internal class PhoneWearRpcHandler(
    private val holder: WearBridgeWorkDepsHolder?,
    /** Process lifetime: an admission outlives its timeout, and its lease must still be released. */
    private val admissionScope: CoroutineScope,
    private val admissionTimeoutMs: Long = PHONE_ADMISSION_TIMEOUT_MS,
    private val logger: Logger = Log.tag(LOG_TAG),
) {

    suspend fun handle(authenticatedSourceNodeId: String, request: ByteArray): ByteArray {
        val started = TimeSource.Monotonic.markNow()
        // §6.2 step 3: any failure other than cancellation answers empty, logged by class only.
        val answer = runCatching { answer(authenticatedSourceNodeId, request) }.getOrElse { failure ->
            if (failure is CancellationException || failure !is Exception) throw failure
            Answer(operation = "failed", result = failure::class.simpleName ?: "Exception")
        }
        // §7.9: operation, result class, byte counts and elapsed time only.
        logger.i {
            "rpc ${answer.operation} -> ${answer.result}: in ${request.size} B, out ${answer.bytes.size} B, " +
                "${started.elapsedNow().inWholeMilliseconds} ms"
        }
        return answer.bytes
    }

    private suspend fun answer(sourceNodeId: String, request: ByteArray): Answer {
        val holder = holder ?: return Answer("admission", "no_holder")
        val lease = admit(holder) ?: return Answer("admission", "refused_or_timed_out")
        // GUARD: once admitted, the bridge call and the encoding run to completion. The bridge
        // publishes its process-memory lease state after its database transaction, so a
        // cancellation between the two must be impossible; the lease is released on every path.
        return withContext(NonCancellable) {
            try {
                respond(lease.deps.phoneWorkoutBridge, sourceNodeId, request)
            } finally {
                lease.release()
            }
        }
    }

    /**
     * Waits for a lease for at most [admissionTimeoutMs]. GUARD: never
     * `withTimeout { awaitWearBridgeWorkLease() }`: a lease granted as the timeout fires is dropped
     * by the cancelled caller and never released, and a leaked lease blocks every later restore's
     * worker drain. The admission runs in [admissionScope] instead; a caller that stops waiting
     * hands the lease, whenever it arrives, to a completion handler that releases it at once.
     */
    private suspend fun admit(holder: WearBridgeWorkDepsHolder): WearBridgeWorkLease? {
        val admission = admissionScope.async { holder.awaitWearBridgeWorkLease() }
        var lease: WearBridgeWorkLease? = null
        try {
            lease = withTimeoutOrNull(admissionTimeoutMs) { admission.await() }
        } finally {
            if (lease == null) {
                admission.invokeOnCompletion { cause ->
                    if (cause == null) admission.getCompleted()?.release()
                }
            }
        }
        return lease
    }

    private suspend fun respond(bridge: PhoneWorkoutBridge, sourceNodeId: String, request: ByteArray): Answer {
        // §6.2 step 1: the enum stays as a compile-time kill switch (Phase 1 §6.1 closure record).
        if (bridge.transportStatus.isNotEmpty()) return Answer("gate", "transport_closed")
        return when (val decoded = WearProtocolCodec.decodeForPhone(request, sourceNodeId)) {
            is PhoneDecodeResult.Success -> when (val envelope = decoded.request) {
                is GetActiveWorkoutRequest -> bridge.getActiveWorkout(sourceNodeId, envelope).let { response ->
                    encoded("get_active_workout", response.snapshot.payload::class.simpleName, response)
                }
                is CompleteCurrentSetRequest -> bridge.completeCurrentSet(sourceNodeId, envelope).let { response ->
                    encoded("complete_current_set", response.outcomeName(), response)
                }
                // decodeForPhone yields only the two request shapes; anything else is not answered.
                else -> Answer("unexpected", envelope::class.simpleName ?: "Envelope")
            }
            is PhoneDecodeResult.CorrelatedProtocolRejection ->
                bridge.protocolRejected(sourceNodeId, decoded.routing, decoded.reason).let { response ->
                    encoded("protocol_rejected", response.outcomeName(), response)
                }
            PhoneDecodeResult.Dropped -> Answer("dropped", "no_answer")
        }
    }

    private fun encoded(operation: String, result: String?, envelope: WearEnvelope): Answer =
        Answer(operation, result ?: "Response", WearProtocolCodec.encode(envelope))

    private fun CompleteCurrentSetResponse.outcomeName(): String? = outcome::class.simpleName

    /** No [bytes] is the Phase 1 "drop": nothing the phone may answer (§5.3). */
    private class Answer(val operation: String, val result: String, val bytes: ByteArray = ByteArray(0))

    private companion object {
        const val LOG_TAG = "WearRpc"
    }
}

/** Deadline for lease admission only; an admitted call runs to completion (§5.4, §6.2 step 4). */
internal const val PHONE_ADMISSION_TIMEOUT_MS: Long = 5_000L
