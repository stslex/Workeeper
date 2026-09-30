// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetBody
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchDecodeResult
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeOwner
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.CommandStatus
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.RequestOperation
import io.github.stslex.workeeper.wear.state.RequestToken
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/** What starts a request chain (wear-paired-transport.md §7.4). Every request has one; nothing polls. */
internal enum class RefreshOrigin {
    /** O1: the controller became interactive (resumed and not ambient, or ambient exit). */
    CONTROLLER_INTERACTIVE,

    /** O2: a Tile render, when no handshake completed within [TILE_REFRESH_MIN_AGE_MS]. */
    TILE,

    /** O3: a phone became reachable while the controller is interactive. */
    PHONE_REACHABLE,

    /** O4: local mutation authority expired (fresh → stale) while the controller is interactive. */
    AUTHORITY_EXPIRED,

    /** O5: the user's Complete set or Retry. Neither counted by nor limited by the budget. */
    USER,
}

/** The watch's own node id, resolved by the first request and kept for the process (§5.2 step 4). */
internal class LocalNodeId {
    @Volatile
    var value: String? = null
}

/**
 * Sends every request the watch makes and applies its result to the owner
 * (wear-paired-transport.md §7.2 to §7.4). Pure Kotlin: the Data Layer is behind [link].
 *
 * GUARD: every state access runs on [scope], which must be single-threaded
 * (`limitedParallelism(1)` in production). The public entry points only post to it, so the
 * single-flight, coalescing and budget bookkeeping never race.
 *
 * Invariants:
 * - single flight: at most one request in flight, the rest in FIFO order;
 * - late handshake tokens: a refresh is an intent that gets its token from the owner only when it
 *   starts, never while another request is in flight;
 * - retry preservation: while the visible command is retryable and its binding is inside its
 *   deadline, no automatic refresh starts; checked when it would start, and a suppressed one is
 *   dropped;
 * - coalescing: a refresh is dropped while a handshake is queued or in flight; commands never are;
 * - finite follow-ups: at most one automatic follow-up handshake per chain, never from a follow-up;
 * - budget: at most [AUTO_REFRESH_BUDGET] automatic handshakes per [AUTO_REFRESH_WINDOW_MS];
 * - no polling: no timer, alarm, wake lock or loop issues a request.
 */
internal class WatchTransportCoordinator(
    private val owner: WatchRuntimeOwner,
    private val link: WearLink,
    private val localNode: LocalNodeId,
    private val scope: CoroutineScope,
    private val clock: ElapsedRealtimeClock,
    private val requestTimeoutMs: Long = REQUEST_TIMEOUT_MS,
    private val logger: Logger = Log.tag(LOG_TAG),
) {
    private val queue = ArrayDeque<Work>()
    private var inFlight: Work? = null
    private var interactive = false
    private var reachable: Boolean? = null
    private var observingReachability = false
    private var lastHandshakeCompletedAtMs: Long? = null
    private val automaticStartsMs = ArrayDeque<Long>()
    private var lastSnapshotFresh = false

    init {
        scope.launch { owner.snapshot.collect { snapshot -> onOwnerSnapshot(snapshot) } }
    }

    /** O1, from the controller: resumed while not ambient, or ambient exit. `onWake` is not an origin. */
    fun setInteractive(value: Boolean) = post {
        val becameInteractive = value && !interactive
        interactive = value
        if (becameInteractive) enqueueRefresh(Chain(RefreshOrigin.CONTROLLER_INTERACTIVE))
    }

    /** O5: a user Retry the owner answered with a refresh. */
    fun requestUserRefresh() = post { enqueueRefresh(Chain(RefreshOrigin.USER)) }

    /** O5: a command attempt the owner issued (Complete set, or a retry of it). */
    fun submitCommand(token: RequestToken, fingerprint: FingerprintCommand) = post {
        queue.addLast(Work.Command(Chain(RefreshOrigin.USER), token, fingerprint))
        pump()
    }

    /** O2: after a Tile render, only if no handshake completed within [TILE_REFRESH_MIN_AGE_MS]. */
    fun onTileRendered() = post {
        val last = lastHandshakeCompletedAtMs
        if (last == null || clock.nowMs() - last >= TILE_REFRESH_MIN_AGE_MS) {
            enqueueRefresh(Chain(RefreshOrigin.TILE))
        }
    }

    private fun post(block: () -> Unit) {
        scope.launch { guarded("event") { block() } }
    }

    private fun enqueueRefresh(chain: Chain, followUp: Boolean = false, pump: Boolean = true) {
        val queued = queue.firstOrNull { it is Work.Refresh } as? Work.Refresh
        if (inFlight is Work.Refresh || queued != null) {
            // A user refresh folded into a queued automatic one keeps it unlimited (§7.2).
            if (chain.origin == RefreshOrigin.USER && !followUp) queued?.userRequested = true
            logger.i { "refresh ${chain.origin} coalesced" }
            return
        }
        queue.addLast(Work.Refresh(chain, followUp))
        if (pump) pump()
    }

    private fun pump() {
        while (inFlight == null) {
            val next = nextWork() ?: return
            val token = start(next) ?: continue
            inFlight = next
            val refreshRequiredAtStart = owner.snapshot.value.workout.refreshRequired
            scope.launch {
                val startedAt = clock.nowMs()
                var acceptedUnavailable = false
                try {
                    val request = runCatching { encode(next, token) }.getOrNull()
                    val exchange = if (request == null) Exchange.Unencodable else exchange(request)
                    guarded("completion") {
                        val result = apply(next, token, exchange)
                        acceptedUnavailable = result.acceptedUnavailable
                        // §7.9: operation, result class, byte counts and elapsed time only.
                        logger.i {
                            "${token.operation.label()} -> ${result.label}: out ${request?.size ?: 0} B, " +
                                "in ${(exchange as? Exchange.Answer)?.bytes?.size ?: 0} B, " +
                                "${clock.nowMs() - startedAt} ms"
                        }
                    }
                } finally {
                    if (next is Work.Refresh) lastHandshakeCompletedAtMs = clock.nowMs()
                    if (next is Work.Command) guarded("settle") { settle(next, token) }
                    // GUARD: cleared on every path, before the follow-up may start the next request.
                    inFlight = null
                }
                guarded("follow-up") { followUp(next, refreshRequiredAtStart, acceptedUnavailable) }
                pump()
            }
        }
    }

    /**
     * The next item to start. GUARD: no handshake starts while the owner holds an in-flight command
     * attempt, because its token would retire that attempt's authority (Phase 1 §3). A queued command
     * goes first; a command issued but not yet handed over makes the refresh wait for it.
     */
    private fun nextWork(): Work? {
        val head = queue.firstOrNull() ?: return null
        if (head is Work.Refresh && owner.snapshot.value.workout.command?.status == CommandStatus.IN_FLIGHT) {
            val command = queue.firstOrNull { it is Work.Command } ?: return null
            queue.remove(command)
            return command
        }
        return queue.removeFirst()
    }

    /** The request's token, or null when the item is dropped at the moment it would start. */
    private fun start(work: Work): RequestToken? = when (work) {
        is Work.Command -> if (attemptCurrent(work)) {
            work.token
        } else {
            // Retired or expired authority authorizes no delivery (Phase 1 §3): never sent.
            logger.i { "${work.token.operation.label()} not sent: attempt retired" }
            runCatching { owner.transportTimeout(work.token.correlationId) }
            guarded("follow-up") {
                followUp(work, refreshRequiredAtStart = false, acceptedUnavailable = false, pump = false)
            }
            null
        }
        is Work.Refresh -> when {
            work.automatic && retryPreserved() -> dropped(work, "retry preserved")
            work.automatic && !budgetAvailable() -> dropped(work, "budget")
            // Late token: issuing it retires the current authority, so it is issued only now.
            else -> runCatching { owner.issueHandshake() }.getOrNull()?.also {
                if (work.automatic) automaticStartsMs.addLast(clock.nowMs())
            }
        }
    }

    private fun attemptCurrent(work: Work.Command): Boolean {
        val binding = owner.snapshot.value.workout.authority as? LocalMutationAuthority.AttemptBound ?: return false
        return binding.commandId == work.fingerprint.commandId && clock.nowMs() < binding.effectiveDeadlineMs
    }

    /** A command whose exchange ended without reaching the owner (a failure) is a transport timeout. */
    private fun settle(work: Work.Command, token: RequestToken) {
        val command = owner.snapshot.value.workout.command ?: return
        if (command.commandId == work.fingerprint.commandId && command.status == CommandStatus.IN_FLIGHT) {
            owner.transportTimeout(token.correlationId)
        }
    }

    private fun dropped(work: Work, reason: String): RequestToken? {
        logger.i { "refresh ${work.chain.origin} dropped: $reason" }
        return null
    }

    private fun retryPreserved(): Boolean {
        val workout = owner.snapshot.value.workout
        val status = workout.command?.status
        if (status != CommandStatus.TIMED_OUT_RETRYABLE && status != CommandStatus.RETRY_READY) return false
        val binding = workout.authority as? LocalMutationAuthority.AttemptBound ?: return false
        return clock.nowMs() < binding.effectiveDeadlineMs
    }

    /** The slot itself is taken only once a token was issued ([start]). */
    private fun budgetAvailable(): Boolean {
        val now = clock.nowMs()
        while (automaticStartsMs.firstOrNull()?.let { now - it >= AUTO_REFRESH_WINDOW_MS } == true) {
            automaticStartsMs.removeFirst()
        }
        return automaticStartsMs.size < AUTO_REFRESH_BUDGET
    }

    private fun encode(work: Work, token: RequestToken): ByteArray = WearProtocolCodec.encode(
        when (work) {
            is Work.Refresh -> GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, token.correlationId)
            is Work.Command -> work.fingerprint.toRequest(token)
        },
    )

    /** §7.3 steps 3 and 4: node lookup and the request, under one local deadline. */
    private suspend fun exchange(request: ByteArray): Exchange = withTimeoutOrNull(requestTimeoutMs) {
        val phones = attempt { link.reachablePhones() } ?: return@withTimeoutOrNull Exchange.Unreachable
        observeReachabilityOnce()
        // What this lookup learned, so a later "reachable" from the listener is O3 (§7.4).
        reachable = phones.isNotEmpty()
        val phone = phones.preferredPhone() ?: return@withTimeoutOrNull Exchange.Unreachable
        if (localNode.value == null) localNode.value = attempt { link.localNodeId() }
        // §7.6: no authority without an identity, so a watch that cannot name itself asks nothing.
        if (localNode.value == null) return@withTimeoutOrNull Exchange.NoAnswer
        val answer = attempt { link.request(phone.id, request) }
        if (answer == null || answer.isEmpty()) Exchange.NoAnswer else Exchange.Answer(answer)
    } ?: Exchange.NoAnswer

    private fun observeReachabilityOnce() {
        if (observingReachability) return
        observingReachability = true
        runCatching { link.observeReachability { value -> post { onReachability(value) } } }
            .onFailure { failure -> logger.w { "reachability observer unavailable: ${failure::class.simpleName}" } }
    }

    /** §7.3 step 5: classify the exchange and apply it to the owner. */
    private fun apply(work: Work, token: RequestToken, exchange: Exchange): Applied = when (work) {
        is Work.Refresh -> applyHandshake(token, exchange)
        is Work.Command -> applyCommand(token, exchange)
    }

    private fun applyHandshake(token: RequestToken, exchange: Exchange): Applied {
        val decoded = when (exchange) {
            Exchange.Unreachable -> return Applied("unreachable").also { owner.handshakeUnanswered(false) }
            Exchange.NoAnswer -> return Applied("no_answer").also { owner.handshakeUnanswered(true) }
            Exchange.Unencodable -> return Applied("unencodable").also { owner.protocolFailure(token.correlationId) }
            is Exchange.Answer -> WearProtocolCodec.decodeForWatch(exchange.bytes)
        }
        val envelope = when (decoded) {
            is WatchDecodeResult.ProtocolMismatch -> return Applied("mismatch:${decoded.failure::class.simpleName}")
                .also { owner.protocolFailure(token.correlationId) }
            is WatchDecodeResult.Success -> decoded.envelope
        }
        return when {
            // Phase 1 ignores an unknown correlation: this request got no response.
            envelope.correlationId != token.correlationId ->
                Applied("other_correlation").also { owner.handshakeUnanswered(true) }
            envelope is ActiveWorkoutSnapshotResponse -> {
                val accepted = owner.receiveSnapshot(envelope)
                // An answer the owner did not accept creates no authority; the link shows it.
                if (!accepted) owner.handshakeUnanswered(true)
                val payload = envelope.snapshot.payload
                val authority = (payload as? SnapshotPayload.ActiveWithTarget)?.mutationAuthority
                Applied(
                    label = "snapshot:${payload::class.simpleName}:${if (accepted) "accepted" else "rejected"}",
                    acceptedUnavailable = accepted && authority is MutationAuthority.Unavailable,
                )
            }
            // A shape change for one correlation is a protocol failure (Phase 1).
            else -> Applied("wrong_shape").also { owner.protocolFailure(token.correlationId) }
        }
    }

    private fun applyCommand(token: RequestToken, exchange: Exchange): Applied {
        val decoded = when (exchange) {
            Exchange.Unreachable -> return Applied("unreachable").also { owner.transportTimeout(token.correlationId) }
            Exchange.NoAnswer -> return Applied("no_answer").also { owner.transportTimeout(token.correlationId) }
            // A local protocol error closes the command without retry (Phase 1 §9).
            Exchange.Unencodable -> return Applied("unencodable").also { owner.protocolFailure(token.correlationId) }
            is Exchange.Answer -> WearProtocolCodec.decodeForWatch(exchange.bytes)
        }
        val envelope = when (decoded) {
            is WatchDecodeResult.ProtocolMismatch -> return Applied("mismatch:${decoded.failure::class.simpleName}")
                .also { owner.protocolFailure(token.correlationId) }
            is WatchDecodeResult.Success -> decoded.envelope
        }
        return when {
            envelope.correlationId != token.correlationId ->
                Applied("other_correlation").also { owner.transportTimeout(token.correlationId) }
            envelope is CompleteCurrentSetResponse -> {
                val admitted = owner.receiveCommandResponse(envelope)
                // A response the reducer does not admit is no response for this attempt.
                if (admitted == null) owner.transportTimeout(token.correlationId)
                val authority = (envelope.replacement.payload as? SnapshotPayload.ActiveWithTarget)?.mutationAuthority
                Applied(
                    label = "response:${envelope.outcome::class.simpleName}:${admitted.admissionLabel()}",
                    acceptedUnavailable = admitted == true && authority is MutationAuthority.Unavailable,
                )
            }
            else -> Applied("wrong_shape").also { owner.protocolFailure(token.correlationId) }
        }
    }

    /** §7.4: one automatic follow-up per chain, never from a follow-up. */
    private fun followUp(
        work: Work,
        refreshRequiredAtStart: Boolean,
        acceptedUnavailable: Boolean,
        pump: Boolean = true,
    ) {
        if (work.followUp || work.chain.followUpUsed) return
        val workout = owner.snapshot.value.workout
        if (workout.display is WatchDisplayState.ProtocolMismatch) return
        val origin = work.chain.origin
        if ((origin == RefreshOrigin.TILE || origin == RefreshOrigin.AUTHORITY_EXPIRED) && !interactive) return
        val refreshTurnedTrue = !refreshRequiredAtStart && workout.refreshRequired
        if (!refreshTurnedTrue && !acceptedUnavailable) return
        work.chain.followUpUsed = true
        enqueueRefresh(work.chain, followUp = true, pump = pump)
    }

    /** Reachability lost is the owner's disconnect; reachability gained is O3 (§7.7). */
    private fun onReachability(nowReachable: Boolean) {
        val previous = reachable
        reachable = nowReachable
        if (previous == nowReachable) return
        if (!nowReachable) {
            owner.disconnected()
        } else if (previous == false && interactive) {
            enqueueRefresh(Chain(RefreshOrigin.PHONE_REACHABLE))
        }
    }

    /** O4: authority expiry is the reducer's fresh → stale transition of an active display. */
    private fun onOwnerSnapshot(snapshot: WatchRuntimeSnapshot) = guarded("snapshot") {
        val freshness = (snapshot.workout.display as? WatchDisplayState.Active)?.freshness
        val expired = lastSnapshotFresh && freshness == ActiveFreshness.STALE
        lastSnapshotFresh = freshness == ActiveFreshness.FRESH
        if (expired && interactive) enqueueRefresh(Chain(RefreshOrigin.AUTHORITY_EXPIRED))
    }

    /** A failure is logged by class only (§7.9) and never escapes into the process. */
    private inline fun guarded(stage: String, block: () -> Unit) {
        runCatching(block).onFailure { failure ->
            if (failure is CancellationException) throw failure
            logger.w { "transport $stage failed: ${failure::class.simpleName}" }
        }
    }

    /**
     * A failed call is null. GUARD: a cancelled Play services Task also surfaces as a
     * CancellationException; only this coroutine's own cancellation (the request deadline) may
     * propagate, or a cancelled Task would leave the request unsettled.
     */
    private suspend fun <T> attempt(block: suspend () -> T): T? = runCatching { block() }.getOrElse { failure ->
        if (failure is CancellationException) currentCoroutineContext().ensureActive()
        null
    }

    private class Chain(val origin: RefreshOrigin) {
        var followUpUsed = false
    }

    private sealed interface Work {
        val chain: Chain
        val followUp: Boolean

        class Refresh(override val chain: Chain, override val followUp: Boolean) : Work {
            /** Set when a user refresh was coalesced into this one. */
            var userRequested: Boolean = chain.origin == RefreshOrigin.USER && !followUp

            /** Every refresh but a user's; a follow-up of a user chain is automatic too. */
            val automatic: Boolean get() = !userRequested
        }

        class Command(
            override val chain: Chain,
            val token: RequestToken,
            val fingerprint: FingerprintCommand,
        ) : Work {
            override val followUp: Boolean = false
        }
    }

    private sealed interface Exchange {
        data object Unreachable : Exchange
        data object NoAnswer : Exchange
        data object Unencodable : Exchange
        class Answer(val bytes: ByteArray) : Exchange
    }

    private class Applied(val label: String, val acceptedUnavailable: Boolean = false)

    private companion object {
        const val LOG_TAG = "WearTransport"
    }
}

/** Built field for field from the owner's command and the request's correlation; nothing recomputed (§5.3). */
private fun FingerprintCommand.toRequest(token: RequestToken) = CompleteCurrentSetRequest(
    schemaVersion = schemaVersion,
    correlationId = token.correlationId,
    commandId = commandId,
    databaseEpoch = databaseEpoch,
    sessionUuid = sessionUuid,
    sessionRevision = sessionRevision,
    mutationLeaseId = mutationLeaseId,
    mutationLeaseGeneration = mutationLeaseGeneration,
    body = CompleteCurrentSetBody(
        performedExerciseUuid = performedExerciseUuid,
        setPosition = setPosition,
        reps = reps,
        weightHundredthsKg = weightHundredthsKg,
        exerciseType = exerciseType,
        setType = setType,
    ),
)

private fun RequestOperation.label(): String = when (this) {
    RequestOperation.HANDSHAKE -> "handshake"
    RequestOperation.COMMAND_INITIAL -> "command"
    RequestOperation.COMMAND_RETRY -> "command_retry"
}

private fun Boolean?.admissionLabel(): String = when (this) {
    null -> "not_admitted"
    true -> "accepted"
    false -> "rejected"
}
