// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandRouting
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetBody
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchDecodeResult
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.feature.wear_bridge.PhoneWorkoutBridge
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeDeps
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeWorkDepsHolder
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeWorkLease
import io.github.stslex.workeeper.feature.wear_bridge.WearPayloadTransportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** wear-paired-transport.md §6.2 and §10.1: every handler branch, admission and lease rule. */
internal class PhoneWearRpcHandlerTest {

    @Test
    fun `a handshake is answered with the bridge snapshot`() = runTest {
        val bridge = FakeBridge()
        val request = GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, CanonicalUuid.random())

        val answer = handler(FakeHolder(bridge)).handle(SOURCE_NODE, WearProtocolCodec.encode(request))

        val decoded = WearProtocolCodec.decodeForWatch(answer) as WatchDecodeResult.Success
        assertEquals(request.correlationId, decoded.envelope.correlationId)
        assertInstanceOf(ActiveWorkoutSnapshotResponse::class.java, decoded.envelope)
        assertEquals(listOf("get:$SOURCE_NODE"), bridge.calls)
    }

    @Test
    fun `a command is answered with the bridge response`() = runTest {
        val bridge = FakeBridge()
        val request = command()

        val answer = handler(FakeHolder(bridge)).handle(SOURCE_NODE, WearProtocolCodec.encode(request))

        val response = (WearProtocolCodec.decodeForWatch(answer) as WatchDecodeResult.Success).envelope
        assertEquals(CompleteCommandOutcome.Applied, (response as CompleteCurrentSetResponse).outcome)
        assertEquals(listOf("complete:$SOURCE_NODE"), bridge.calls)
    }

    @Test
    fun `a correlatable invalid numeric body is answered with the bridge rejection`() = runTest {
        val bridge = FakeBridge()
        val bytes = WearProtocolCodec.encode(command()).decodeToString()
            .replace("\"reps\":5", "\"reps\":5.0")
            .encodeToByteArray()

        val answer = handler(FakeHolder(bridge)).handle(SOURCE_NODE, bytes)

        val response = (WearProtocolCodec.decodeForWatch(answer) as WatchDecodeResult.Success).envelope
        assertEquals(
            CompleteCommandOutcome.ProtocolRejected(ProtocolRejectionReason.INVALID_NUMERIC_ENCODING),
            (response as CompleteCurrentSetResponse).outcome,
        )
        assertEquals(listOf("rejected:$SOURCE_NODE:INVALID_NUMERIC_ENCODING"), bridge.calls)
    }

    @Test
    fun `a dropped request is answered with no bytes and never reaches the bridge`() = runTest {
        val bridge = FakeBridge()

        val answer = handler(FakeHolder(bridge)).handle(SOURCE_NODE, "{\"operation\":\"nope\"}".encodeToByteArray())

        assertEquals(0, answer.size)
        assertEquals(emptyList<String>(), bridge.calls)
    }

    @Test
    fun `an open transport gate answers nothing`() = runTest {
        val bridge = FakeBridge(transportStatus = setOf(WearPayloadTransportStatus.TRANSPORT_POLICY_REQUIRED))
        val holder = FakeHolder(bridge)
        val request = GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, CanonicalUuid.random())

        val answer = handler(holder).handle(SOURCE_NODE, WearProtocolCodec.encode(request))

        assertEquals(0, answer.size)
        assertEquals(emptyList<String>(), bridge.calls)
        assertEquals(1, holder.released, "the lease is released on the kill-switch path too")
    }

    @Test
    fun `no lease, no holder and a failing bridge each answer nothing`() = runTest {
        val request = handshakeBytes()

        assertEquals(0, handler(FakeHolder(FakeBridge(), grant = false)).handle(SOURCE_NODE, request).size)
        assertEquals(0, handler(null).handle(SOURCE_NODE, request).size)
        val failing = FakeHolder(FakeBridge(failure = IllegalStateException("synthetic bridge failure")))
        assertEquals(0, handler(failing).handle(SOURCE_NODE, request).size)
        assertEquals(1, failing.released, "the lease is released when the bridge throws")
    }

    @Test
    fun `admission times out empty and a lease granted later is released at once`() = runTest {
        val holder = FakeHolder(FakeBridge(), grantAfterMs = PHONE_ADMISSION_TIMEOUT_MS + 1_000L)
        val request = handshakeBytes()

        val answer = handler(holder, this).handle(SOURCE_NODE, request)

        assertEquals(0, answer.size, "admission timed out")
        assertEquals(0, holder.granted, "no lease yet")
        advanceTimeBy(1_001L)
        runCurrent()
        assertEquals(1, holder.granted)
        assertEquals(1, holder.released, "the late lease was released, never leaked")
    }

    @Test
    fun `an admitted call that outlives the admission timeout completes and releases its lease`() = runTest {
        val holder = FakeHolder(FakeBridge(delayMs = PHONE_ADMISSION_TIMEOUT_MS * 2))
        val request = handshakeBytes()

        val answer = handler(holder, this).handle(SOURCE_NODE, request)

        assertInstanceOf(WatchDecodeResult.Success::class.java, WearProtocolCodec.decodeForWatch(answer))
        assertEquals(1, holder.released)
    }

    @Test
    fun `a caller cancelled during the bridge call still completes it and releases the lease once`() = runTest {
        val bridge = FakeBridge(delayMs = 1_000L)
        val holder = FakeHolder(bridge)
        val caller = launch { handler(holder, this@runTest).handle(SOURCE_NODE, handshakeBytes()) }
        advanceTimeBy(500L)
        caller.cancel()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(listOf("get:$SOURCE_NODE"), bridge.calls, "the admitted call ran to completion")
        assertEquals(1, holder.released)
    }

    @Test
    fun `the handler logs operation, result class, byte counts and time only`() = runTest {
        val logger = RecordingLogger()
        val request = command()

        handler(FakeHolder(FakeBridge()), this, logger).handle(SOURCE_NODE, WearProtocolCodec.encode(request))

        val line = logger.lines.single()
        assertTrue(line.startsWith("rpc complete_current_set -> Applied: in "), line)
        listOf(SOURCE_NODE, request.commandId.value, request.sessionUuid.value, "\"").forEach { forbidden ->
            assertTrue(forbidden !in line, "the log line carries '$forbidden': $line")
        }
    }

    private fun handshakeBytes(): ByteArray =
        WearProtocolCodec.encode(GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, CanonicalUuid.random()))

    private fun TestScope.handler(
        holder: WearBridgeWorkDepsHolder?,
        scope: CoroutineScope = backgroundScope,
        logger: Logger = RecordingLogger(),
    ) = PhoneWearRpcHandler(holder = holder, admissionScope = scope, logger = logger)

    private class FakeBridge(
        override val transportStatus: Set<WearPayloadTransportStatus> = emptySet(),
        private val failure: Exception? = null,
        private val delayMs: Long = 0L,
    ) : PhoneWorkoutBridge {
        val calls = mutableListOf<String>()

        override suspend fun getActiveWorkout(
            authenticatedSourceNodeId: String,
            request: GetActiveWorkoutRequest,
        ): ActiveWorkoutSnapshotResponse {
            failure?.let { throw it }
            delay(delayMs)
            calls += "get:$authenticatedSourceNodeId"
            return ActiveWorkoutSnapshotResponse(WearProtocol.SCHEMA_VERSION, request.correlationId, NO_SESSION)
        }

        override suspend fun completeCurrentSet(
            authenticatedSourceNodeId: String,
            request: CompleteCurrentSetRequest,
        ): CompleteCurrentSetResponse {
            calls += "complete:$authenticatedSourceNodeId"
            return CompleteCurrentSetResponse(
                WearProtocol.SCHEMA_VERSION,
                request.correlationId,
                request.commandId,
                CompleteCommandOutcome.Applied,
                NO_SESSION,
            )
        }

        override suspend fun protocolRejected(
            authenticatedSourceNodeId: String,
            routing: CompleteCommandRouting,
            reason: ProtocolRejectionReason,
        ): CompleteCurrentSetResponse {
            calls += "rejected:$authenticatedSourceNodeId:$reason"
            return CompleteCurrentSetResponse(
                WearProtocol.SCHEMA_VERSION,
                routing.correlationId,
                routing.commandId,
                CompleteCommandOutcome.ProtocolRejected(reason),
                NO_SESSION,
            )
        }
    }

    private class FakeHolder(
        private val bridge: PhoneWorkoutBridge,
        private val grant: Boolean = true,
        private val grantAfterMs: Long = 0L,
    ) : WearBridgeWorkDepsHolder {
        var granted = 0
        var released = 0

        override suspend fun awaitWearBridgeWorkLease(): WearBridgeWorkLease? {
            delay(grantAfterMs)
            if (!grant) return null
            granted += 1
            return object : WearBridgeWorkLease {
                override val deps: WearBridgeDeps = object : WearBridgeDeps {
                    override val phoneWorkoutBridge: PhoneWorkoutBridge = bridge
                }

                override fun release() {
                    released += 1
                }
            }
        }
    }

    private class RecordingLogger : Logger {
        val lines = mutableListOf<String>()
        override fun e(throwable: Throwable, message: String?) { lines += message.orEmpty() }
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

    private companion object {
        const val SOURCE_NODE = "watch-node-7f3a"
        val EPOCH: CanonicalUuid = CanonicalUuid.parse("cccccccc-0000-4000-8000-000000000001")
        val NO_SESSION = SnapshotData(EPOCH, SnapshotPayload.NoSession)

        fun command() = CompleteCurrentSetRequest(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = CanonicalUuid.random(),
            commandId = CanonicalUuid.random(),
            databaseEpoch = EPOCH,
            sessionUuid = CanonicalUuid.random(),
            sessionRevision = 1,
            mutationLeaseId = CanonicalUuid.random(),
            mutationLeaseGeneration = 1,
            body = CompleteCurrentSetBody(
                performedExerciseUuid = CanonicalUuid.random(),
                setPosition = 0,
                reps = 5,
                weightHundredthsKg = 10_000,
                exerciseType = ExerciseTypeWire.WEIGHTED,
                setType = SetTypeWire.WORK,
            ),
        )
    }
}
