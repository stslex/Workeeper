// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.PhoneDecodeResult
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.WearEnvelope
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.runtime.ConnectedWatchRuntime
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope

/**
 * A phone that answers with bytes from the real codec. Every request is decoded the way the phone
 * decodes it, so a test sees exactly what crossed the link; [answer] decides the reply.
 */
internal class FakePhoneLink : WearLink {
    var phones: List<PhoneNode> = listOf(PhoneNode(PHONE_NODE, isNearby = true))
    var localNode: String = WATCH_NODE
    val requests = mutableListOf<WearEnvelope>()
    val sentTo = mutableListOf<String>()
    var reachability: ((Boolean) -> Unit)? = null

    /** Held requests stay in flight until the test completes this gate. */
    var gate: CompletableDeferred<Unit>? = null

    var answer: (WearEnvelope) -> ByteArray = { ByteArray(0) }

    override suspend fun localNodeId(): String = localNode

    override suspend fun reachablePhones(): List<PhoneNode> = phones

    override suspend fun request(nodeId: String, request: ByteArray): ByteArray {
        sentTo += nodeId
        val decoded = WearProtocolCodec.decodeForPhone(request, WATCH_NODE)
        val envelope = (decoded as PhoneDecodeResult.Success).request
        requests += envelope
        gate?.await()
        return answer(envelope)
    }

    override fun observeReachability(onChange: (reachable: Boolean) -> Unit) {
        reachability = onChange
    }

    val handshakes: List<GetActiveWorkoutRequest> get() = requests.filterIsInstance<GetActiveWorkoutRequest>()
    val commands: List<CompleteCurrentSetRequest> get() = requests.filterIsInstance<CompleteCurrentSetRequest>()

    companion object {
        const val PHONE_NODE = "phone-node-a"
        const val WATCH_NODE = "watch-node-local"
    }
}

/** Answers a handshake with [snapshot] and a command with [outcome] + [replacement], all encoded. */
internal fun phoneAnswers(
    snapshot: () -> SnapshotData,
    outcome: () -> CompleteCommandOutcome = { CompleteCommandOutcome.Applied },
    replacement: () -> SnapshotData = snapshot,
): (WearEnvelope) -> ByteArray = { request ->
    when (request) {
        is GetActiveWorkoutRequest -> WearProtocolCodec.encode(
            ActiveWorkoutSnapshotResponse(WearProtocol.SCHEMA_VERSION, request.correlationId, snapshot()),
        )
        is CompleteCurrentSetRequest -> WearProtocolCodec.encode(
            CompleteCurrentSetResponse(
                schemaVersion = WearProtocol.SCHEMA_VERSION,
                correlationId = request.correlationId,
                commandId = request.commandId,
                outcome = outcome(),
                replacement = replacement(),
            ),
        )
        else -> ByteArray(0)
    }
}

/** Every line a logger received, to check what telemetry would carry (§7.9). */
internal class CapturingLogger : Logger {
    val lines = mutableListOf<String>()
    override fun e(throwable: Throwable, message: String?) { lines += "${message.orEmpty()} ${throwable.message}" }
    override fun d(message: String) { lines += message }
    override fun d(e: Throwable, message: String) { lines += message }
    override fun d(e: Throwable, message: () -> String) { lines += message() }
    override fun d(message: () -> String) { lines += message() }
    override fun i(message: String) { lines += message }
    override fun i(message: () -> String) { lines += message() }
    override fun v(message: String) { lines += message }
    override fun v(message: () -> String) { lines += message() }
    override fun w(message: String) { lines += message }
    override fun w(message: () -> String) { lines += message() }
    override fun w(message: String, throwable: Throwable) { lines += message }
    override fun w(throwable: Throwable, message: () -> String) { lines += message() }
}

/**
 * The real owner and the coordinator on the test dispatcher's virtual clock: the owner's elapsed
 * time and the request timeouts both follow `advanceTimeBy`.
 */
internal class TransportHarness(scope: TestScope, nodeId: LocalNodeId? = null) {
    val clock = ElapsedRealtimeClock { BASE_MS + scope.testScheduler.currentTime }
    val localNode = nodeId ?: LocalNodeId()
    val env = RuntimeTestEnvironment(
        transformClock = { clock },
        nodeId = if (nodeId == null) ({ "synthetic-test-watch" }) else localNode::value,
    )
    val owner = env.owner
    val link = FakePhoneLink()
    val logger = CapturingLogger()
    val coordinator = WatchTransportCoordinator(
        owner = owner,
        link = link,
        localNode = localNode,
        scope = scope.backgroundScope,
        clock = clock,
        logger = logger,
    )
    val runtime = ConnectedWatchRuntime(owner, coordinator)

    private companion object {
        const val BASE_MS = 1_000L
    }
}
