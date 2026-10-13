// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CommandRouting
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandRequest
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandResponse
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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * wear-paired-transport.md §6.2 and §10.1: every handler branch, admission and lease rule; the v2
 * dispatch and the version answer of wear-phase-2-exercise-list.md §5.1 and §6.4.
 */
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
    fun `a command of either kind is answered with the bridge response`() = runTest {
        listOf(command(), command(SKIP_BODY)).forEach { request ->
            val bridge = FakeBridge()

            val answer = handler(FakeHolder(bridge)).handle(SOURCE_NODE, WearProtocolCodec.encode(request))

            val response = (WearProtocolCodec.decodeForWatch(answer) as WatchDecodeResult.Success).envelope
            assertEquals(CommandOutcome.Applied, (response as WatchCommandResponse).outcome)
            assertEquals(listOf("command:$SOURCE_NODE:${request.body::class.simpleName}"), bridge.calls)
        }
    }

    @Test
    fun `a v1 handshake and a v1 command get exactly the version answer and never reach the bridge`() = runTest {
        listOf(V1_HANDSHAKE, V1_COMMAND).forEach { request ->
            val bridge = FakeBridge()
            val logger = RecordingLogger()

            val answer = handler(FakeHolder(bridge), logger = logger).handle(SOURCE_NODE, request.encodeToByteArray())

            assertArrayEquals(WearProtocolCodec.VERSION_ANSWER, answer)
            assertEquals(
                "{\"operation\":\"unsupported_version\",\"schemaVersion\":2}",
                answer.decodeToString(),
            )
            assertEquals(emptyList<String>(), bridge.calls)
            assertTrue(logger.lines.single().startsWith("rpc version -> unsupported: in "), logger.lines.single())
        }
    }

    @Test
    fun `a closed kill switch answers a v1 request with nothing, before any decoding`() = runTest {
        val bridge = FakeBridge(transportStatus = setOf(WearPayloadTransportStatus.TRANSPORT_POLICY_REQUIRED))

        val answer = handler(FakeHolder(bridge)).handle(SOURCE_NODE, V1_HANDSHAKE.encodeToByteArray())

        assertEquals(0, answer.size)
        assertEquals(emptyList<String>(), bridge.calls)
    }

    @Test
    fun `a skip command whose skipped is a string is dropped`() = runTest {
        val bridge = FakeBridge()
        val bytes = WearProtocolCodec.encode(command(SKIP_BODY)).decodeToString()
            .replace("\"skipped\":true", "\"skipped\":\"true\"")
            .encodeToByteArray()

        val answer = handler(FakeHolder(bridge)).handle(SOURCE_NODE, bytes)

        assertEquals(0, answer.size)
        assertEquals(emptyList<String>(), bridge.calls)
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
            CommandOutcome.ProtocolRejected(ProtocolRejectionReason.INVALID_NUMERIC_ENCODING),
            (response as WatchCommandResponse).outcome,
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
    fun `a malformed request still gets empty bytes`() = runTest {
        val bridge = FakeBridge()
        listOf("{", "[]", "{\"operation\":\"get_active_workout\",\"schemaVersion\":\"1\"}").forEach { request ->
            assertEquals(0, handler(FakeHolder(bridge)).handle(SOURCE_NODE, request.encodeToByteArray()).size, request)
        }
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
        mapOf(
            command() to "rpc watch_command:complete_set -> Applied: in ",
            command(SKIP_BODY) to "rpc watch_command:set_exercise_skipped -> Applied: in ",
        ).forEach { (request, prefix) ->
            val logger = RecordingLogger()

            handler(FakeHolder(FakeBridge()), this, logger).handle(SOURCE_NODE, WearProtocolCodec.encode(request))

            val line = logger.lines.single()
            assertTrue(line.startsWith(prefix), line)
            val forbidden = listOf(SOURCE_NODE, request.commandId.value, request.sessionUuid.value, SKIPPED_UUID.value)
            (forbidden + "\"").forEach { value ->
                assertTrue(value !in line, "the log line carries '$value': $line")
            }
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

        override suspend fun executeCommand(
            authenticatedSourceNodeId: String,
            request: WatchCommandRequest,
        ): WatchCommandResponse {
            calls += "command:$authenticatedSourceNodeId:${request.body::class.simpleName}"
            return WatchCommandResponse(
                WearProtocol.SCHEMA_VERSION,
                request.correlationId,
                request.commandId,
                CommandOutcome.Applied,
                NO_SESSION,
            )
        }

        override suspend fun protocolRejected(
            authenticatedSourceNodeId: String,
            routing: CommandRouting,
            reason: ProtocolRejectionReason,
        ): WatchCommandResponse {
            calls += "rejected:$authenticatedSourceNodeId:$reason"
            return WatchCommandResponse(
                WearProtocol.SCHEMA_VERSION,
                routing.correlationId,
                routing.commandId,
                CommandOutcome.ProtocolRejected(reason),
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

        val SKIPPED_UUID: CanonicalUuid = CanonicalUuid.parse("dddddddd-0000-4000-8000-000000000001")
        val SKIP_BODY = WatchCommandBody.SetExerciseSkipped(SKIPPED_UUID, skipped = true)

        /** A Phase 1 watch's handshake, byte for byte as a v1 watch encodes it. */
        const val V1_HANDSHAKE = "{\"operation\":\"get_active_workout\",\"schemaVersion\":1," +
            "\"correlationId\":\"aaaaaaaa-0000-4000-8000-000000000001\"}"

        /** A Phase 1 watch's command, with the v1 operation and body. */
        const val V1_COMMAND = "{\"operation\":\"complete_current_set\",\"schemaVersion\":1," +
            "\"correlationId\":\"aaaaaaaa-0000-4000-8000-000000000002\"," +
            "\"commandId\":\"aaaaaaaa-0000-4000-8000-000000000003\"," +
            "\"databaseEpoch\":\"cccccccc-0000-4000-8000-000000000001\"," +
            "\"sessionUuid\":\"aaaaaaaa-0000-4000-8000-000000000004\",\"sessionRevision\":1," +
            "\"mutationLeaseId\":\"aaaaaaaa-0000-4000-8000-000000000005\",\"mutationLeaseGeneration\":1," +
            "\"body\":{\"performedExerciseUuid\":\"aaaaaaaa-0000-4000-8000-000000000006\",\"setPosition\":0," +
            "\"reps\":5,\"weightHundredthsKg\":10000,\"exerciseType\":\"WEIGHTED\",\"setType\":\"WORK\"}}"

        fun command(
            body: WatchCommandBody = WatchCommandBody.CompleteSet(
                performedExerciseUuid = CanonicalUuid.random(),
                setPosition = 0,
                reps = 5,
                weightHundredthsKg = 10_000,
                exerciseType = ExerciseTypeWire.WEIGHTED,
                setType = SetTypeWire.WORK,
            ),
        ) = WatchCommandRequest(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = CanonicalUuid.random(),
            commandId = CanonicalUuid.random(),
            databaseEpoch = EPOCH,
            sessionUuid = CanonicalUuid.random(),
            sessionRevision = 1,
            mutationLeaseId = CanonicalUuid.random(),
            mutationLeaseGeneration = 1,
            body = body,
        )
    }
}
