// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class WearProtocolCodecTest {

    @Test
    fun `round trips every request and snapshot authority shape deterministically`() {
        val unavailable = MutationAuthority.Unavailable(
            MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED,
        )
        val requests = listOf(
            GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, ProtocolFixtures.correlationId),
            ProtocolFixtures.command(),
        )
        val responses = listOf(
            ActiveWorkoutSnapshotResponse(
                WearProtocol.SCHEMA_VERSION,
                ProtocolFixtures.correlationId,
                ProtocolFixtures.snapshot(),
            ),
            ActiveWorkoutSnapshotResponse(
                WearProtocol.SCHEMA_VERSION,
                ProtocolFixtures.correlationId,
                ProtocolFixtures.snapshot(ProtocolFixtures.activePayload(unavailable)),
            ),
        )

        requests.forEach { envelope ->
            val bytes = WearProtocolCodec.encode(envelope)
            val decoded = assertIs<PhoneDecodeResult.Success>(
                WearProtocolCodec.decodeForPhone(bytes, authenticatedSourceNodeId = "watch-node"),
            ).request
            assertEquals(envelope, decoded)
            assertContentEquals(bytes, WearProtocolCodec.encode(decoded))
        }
        responses.forEach { envelope ->
            val bytes = WearProtocolCodec.encode(envelope)
            val decoded = assertIs<WatchDecodeResult.Success>(
                WearProtocolCodec.decodeForWatch(bytes),
            ).envelope
            assertEquals(envelope, decoded)
            assertContentEquals(bytes, WearProtocolCodec.encode(decoded))
        }
    }

    @Test
    fun `phone accepts integer domain violations for typed gateway validation`() {
        val invalidDomainRequest = ProtocolFixtures.command(
            ProtocolFixtures.commandBody(reps = 0, weightHundredthsKg = -1),
        )
        val result = assertIs<PhoneDecodeResult.Success>(
            WearProtocolCodec.decodeForPhone(
                WearProtocolCodec.encode(invalidDomainRequest),
                authenticatedSourceNodeId = "watch-node",
            ),
        )
        assertEquals(invalidDomainRequest, result.request)
    }

    @Test
    fun `complete routing turns only numeric token corruption into correlated rejection`() {
        val valid = WearProtocolCodec.encode(ProtocolFixtures.command()).decodeToString()
        listOf(
            valid.replace("\"reps\":8", "\"reps\":1.5"),
            valid.replace("\"reps\":8", "\"reps\":\"8\""),
            valid.replace("\"reps\":8", "\"reps\":2147483648"),
            valid.replace("\"reps\":8", "\"reps\":NaN"),
            valid.replace("\"reps\":8", "\"reps\":Infinity"),
            valid.replace("\"weightHundredthsKg\":10000", "\"weightHundredthsKg\":1.25"),
        ).forEach { malformed ->
            val rejected = assertIs<PhoneDecodeResult.CorrelatedProtocolRejection>(
                WearProtocolCodec.decodeForPhone(
                    malformed.toByteArray(StandardCharsets.UTF_8),
                    authenticatedSourceNodeId = "watch-node",
                ),
            )
            assertEquals(ProtocolRejectionReason.INVALID_NUMERIC_ENCODING, rejected.reason)
            assertEquals(ProtocolFixtures.commandId, rejected.routing.commandId)
        }
    }

    @Test
    fun `malformed routing unknown operation and oversized input are dropped without response`() {
        val valid = WearProtocolCodec.encode(ProtocolFixtures.command()).decodeToString()
        val malformedRouting = valid.replace(
            "\"commandId\":\"${ProtocolFixtures.commandId}\",",
            "",
        )
        val unknownOperation = valid.replace("complete_current_set", "future_operation")
        val unsupportedSchema = valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")

        listOf(malformedRouting, unknownOperation, unsupportedSchema).forEach { candidate ->
            assertEquals(
                PhoneDecodeResult.Dropped,
                WearProtocolCodec.decodeForPhone(
                    candidate.encodeToByteArray(),
                    authenticatedSourceNodeId = "watch-node",
                ),
            )
        }
        assertEquals(
            PhoneDecodeResult.Dropped,
            WearProtocolCodec.decodeForPhone(
                ByteArray(WearProtocol.MAX_ENVELOPE_BYTES + 1),
                authenticatedSourceNodeId = "watch-node",
            ),
        )
        assertEquals(
            PhoneDecodeResult.Dropped,
            WearProtocolCodec.decodeForPhone(
                WearProtocolCodec.encode(ProtocolFixtures.command()),
                authenticatedSourceNodeId = "",
            ),
        )
    }

    @Test
    fun `watch rejects unsupported versions unknown operations and partial authority`() {
        val valid = WearProtocolCodec.encode(
            ActiveWorkoutSnapshotResponse(
                WearProtocol.SCHEMA_VERSION,
                ProtocolFixtures.correlationId,
                ProtocolFixtures.snapshot(),
            ),
        ).decodeToString()
        val unsupported = valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        val unknown = valid.replace("active_workout_snapshot", "future_snapshot")
        val partialGranted = valid.replace(
            ",\"leaseRemainingAtPhoneSendMs\":120000",
            "",
        )

        assertMismatch(unsupported, DecodeFailure.UnsupportedSchemaVersion)
        assertMismatch(unknown, DecodeFailure.UnknownOperation)
        assertMismatch(partialGranted, DecodeFailure.Malformed)
    }

    @Test
    fun `unavailable authority carrying a lease fails closed`() {
        val unavailable = MutationAuthority.Unavailable(
            MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED,
        )
        val valid = WearProtocolCodec.encode(
            ActiveWorkoutSnapshotResponse(
                WearProtocol.SCHEMA_VERSION,
                ProtocolFixtures.correlationId,
                ProtocolFixtures.snapshot(ProtocolFixtures.activePayload(unavailable)),
            ),
        ).decodeToString()
        val mixed = valid.replace(
            "\"reason\":\"fresh_handshake_required\"",
            "\"reason\":\"fresh_handshake_required\",\"mutationLeaseId\":\"${ProtocolFixtures.leaseId}\"",
        )
        assertMismatch(mixed, DecodeFailure.Malformed)
    }

    @Test
    fun `closed outcome replacement pairings reject mutation authority on protocol rejection`() {
        val response = CompleteCurrentSetResponse(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = ProtocolFixtures.correlationId,
            commandId = ProtocolFixtures.commandId,
            outcome = CompleteCommandOutcome.ProtocolRejected(
                ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
            ),
            replacement = ProtocolFixtures.snapshot(),
        )
        assertFailsWith<IllegalArgumentException> { WearProtocolCodec.encode(response) }

        val readOnlyResponse = response.copy(
            replacement = ProtocolFixtures.snapshot(
                ProtocolFixtures.activePayload(
                    MutationAuthority.Unavailable(
                        MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED,
                    ),
                ),
            ),
        )
        val decoded = assertIs<WatchDecodeResult.Success>(
            WearProtocolCodec.decodeForWatch(WearProtocolCodec.encode(readOnlyResponse)),
        )
        assertEquals(readOnlyResponse, decoded.envelope)
    }

    @Test
    fun `encoded boundary admits 16384 bytes and replaces 16385 byte snapshot`() {
        val exact = ByteArray(WearProtocol.MAX_ENVELOPE_BYTES) { 0x2a }
        val oversized = ByteArray(WearProtocol.MAX_ENVELOPE_BYTES + 1) { 0x2b }
        val fallback = ByteArray(1_023) { 0x2c }

        assertContentEquals(exact, EncodedEnvelopeGate.requestOrNull(exact))
        assertNull(EncodedEnvelopeGate.requestOrNull(oversized))
        assertContentEquals(exact, EncodedEnvelopeGate.snapshotOrFallback(exact, fallback))
        assertContentEquals(fallback, EncodedEnvelopeGate.snapshotOrFallback(oversized, fallback))
    }

    private fun assertMismatch(encoded: String, expected: DecodeFailure) {
        val result = assertIs<WatchDecodeResult.ProtocolMismatch>(
            WearProtocolCodec.decodeForWatch(encoded.encodeToByteArray()),
        )
        assertEquals(expected, result.failure)
    }
}
