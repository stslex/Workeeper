// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WearProtocolCodecTest {

    @Test
    fun `round trips every v2 envelope body and payload and re-encodes to the same bytes`() {
        val requests = listOf(
            GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, ProtocolFixtures.correlationId),
            ProtocolFixtures.command(),
            ProtocolFixtures.command(ProtocolFixtures.commandBody(weightHundredthsKg = null)),
            ProtocolFixtures.command(ProtocolFixtures.skipBody(skipped = true)),
            ProtocolFixtures.command(ProtocolFixtures.skipBody(skipped = false)),
        )
        val snapshots = listOf(
            ProtocolFixtures.snapshot(),
            ProtocolFixtures.snapshot(ProtocolFixtures.activePayload(ProtocolFixtures.unavailable)),
            ProtocolFixtures.snapshot(ProtocolFixtures.activePayload(exercises = emptyList())),
            ProtocolFixtures.snapshot(SnapshotPayload.NoSession),
            ProtocolFixtures.snapshot(SnapshotPayload.PayloadTooLarge(ProtocolFixtures.sessionUuid, 11)),
        )
        val responses = snapshots.map { snapshot ->
            ActiveWorkoutSnapshotResponse(WearProtocol.SCHEMA_VERSION, ProtocolFixtures.correlationId, snapshot)
        } + listOf(
            ProtocolFixtures.commandResponse(CommandOutcome.Applied, ProtocolFixtures.snapshot()),
            ProtocolFixtures.commandResponse(
                CommandOutcome.InvalidValues(NumericField.REPS, InvalidValueReason.BELOW_MINIMUM),
                ProtocolFixtures.snapshot(ProtocolFixtures.activePayload(ProtocolFixtures.unavailable)),
            ),
            ProtocolFixtures.commandResponse(
                CommandOutcome.TargetChanged,
                ProtocolFixtures.snapshot(SnapshotPayload.PayloadTooLarge(ProtocolFixtures.sessionUuid, 12)),
            ),
            ProtocolFixtures.commandResponse(
                CommandOutcome.NoActiveSession,
                ProtocolFixtures.snapshot(SnapshotPayload.NoSession),
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
    fun `the wire names of v2 are the specification's`() {
        val command = WearProtocolCodec.encode(ProtocolFixtures.command()).decodeToString()
        val skip = WearProtocolCodec.encode(ProtocolFixtures.command(ProtocolFixtures.skipBody())).decodeToString()
        val snapshot = WearProtocolCodec.encode(
            ActiveWorkoutSnapshotResponse(
                WearProtocol.SCHEMA_VERSION,
                ProtocolFixtures.correlationId,
                ProtocolFixtures.snapshot(),
            ),
        ).decodeToString()

        assertTrue("\"operation\":\"watch_command\"" in command, command)
        assertTrue("\"kind\":\"complete_set\"" in command, command)
        assertTrue("\"kind\":\"set_exercise_skipped\"" in skip && "\"skipped\":true" in skip, skip)
        val names = listOf(
            "\"state\":\"active\"",
            "\"kind\":\"open\"",
            "\"kind\":\"done\"",
            "\"kind\":\"blocked\"",
            "\"skipped\":true",
        )
        names.forEach { name -> assertTrue(name in snapshot, "$name in $snapshot") }
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
        val unknownOperation = valid.replace("watch_command", "future_operation")
        // wear-phase-2-exercise-list.md §5.1 (P3): another schema version is answered, not dropped.
        val unsupportedSchema = valid.replace("\"schemaVersion\":2", "\"schemaVersion\":3")

        listOf(malformedRouting, unknownOperation).forEach { candidate ->
            assertEquals(
                PhoneDecodeResult.Dropped,
                WearProtocolCodec.decodeForPhone(
                    candidate.encodeToByteArray(),
                    authenticatedSourceNodeId = "watch-node",
                ),
            )
        }
        assertEquals(
            PhoneDecodeResult.UnsupportedVersion,
            WearProtocolCodec.decodeForPhone(
                unsupportedSchema.encodeToByteArray(),
                authenticatedSourceNodeId = "watch-node",
            ),
        )
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
    fun `strict decoding rejects unknown keys and wrong discriminators on both sides`() {
        val command = WearProtocolCodec.encode(ProtocolFixtures.command()).decodeToString()
        val skip = WearProtocolCodec.encode(ProtocolFixtures.command(ProtocolFixtures.skipBody())).decodeToString()
        listOf(
            command.replace("\"setPosition\":1", "\"setPosition\":1,\"extra\":0"),
            command.replace("\"kind\":\"complete_set\"", "\"kind\":\"complete_sets\""),
            skip.replace("\"skipped\":true", "\"skipped\":true,\"setPosition\":1"),
            skip.replace("\"kind\":\"set_exercise_skipped\"", "\"kind\":\"skip\""),
        ).forEach { candidate ->
            assertEquals(
                PhoneDecodeResult.Dropped,
                phoneDecode(candidate),
                candidate,
            )
        }
        // A skip body relabelled complete_set has no integer reps: the correlated rejection v1 gives
        // a body without reps, not a drop.
        val relabelled = assertIs<PhoneDecodeResult.CorrelatedProtocolRejection>(
            phoneDecode(skip.replace("\"kind\":\"set_exercise_skipped\"", "\"kind\":\"complete_set\"")),
        )
        assertEquals(ProtocolRejectionReason.INVALID_NUMERIC_ENCODING, relabelled.reason)
        assertEquals(ProtocolFixtures.commandId, relabelled.routing.commandId)
        val snapshot = WearProtocolCodec.encode(
            ActiveWorkoutSnapshotResponse(
                WearProtocol.SCHEMA_VERSION,
                ProtocolFixtures.correlationId,
                ProtocolFixtures.snapshot(),
            ),
        ).decodeToString()
        listOf(
            snapshot.replace("\"kind\":\"open\"", "\"kind\":\"opened\""),
            snapshot.replace("\"state\":\"active\"", "\"state\":\"active_with_target\""),
            snapshot.replace("\"skipped\":false", "\"skipped\":false,\"extra\":0"),
            snapshot.replace("\"reps\":8", "\"reps\":8.0"),
        ).forEach { candidate -> assertMismatch(candidate, DecodeFailure.Malformed) }
    }

    @Test
    fun `a set_exercise_skipped whose skipped is not a JSON boolean is dropped`() {
        val valid = WearProtocolCodec.encode(ProtocolFixtures.command(ProtocolFixtures.skipBody())).decodeToString()
        listOf("\"true\"", "1", "null", "\"yes\"").forEach { value ->
            assertEquals(
                PhoneDecodeResult.Dropped,
                WearProtocolCodec.decodeForPhone(
                    valid.replace("\"skipped\":true", "\"skipped\":$value").encodeToByteArray(),
                    authenticatedSourceNodeId = "watch-node",
                ),
                value,
            )
        }
        assertEquals(
            PhoneDecodeResult.Dropped,
            WearProtocolCodec.decodeForPhone(
                valid.replace(",\"skipped\":true", "").encodeToByteArray(),
                authenticatedSourceNodeId = "watch-node",
            ),
        )
    }

    @Test
    fun `every v1 operation name is unknown to v2`() {
        val v1Command = v1CommandJson(schemaVersion = WearProtocol.SCHEMA_VERSION)
        assertEquals(
            PhoneDecodeResult.Dropped,
            WearProtocolCodec.decodeForPhone(v1Command.encodeToByteArray(), authenticatedSourceNodeId = "watch-node"),
        )
        val response = WearProtocolCodec.encode(
            ProtocolFixtures.commandResponse(CommandOutcome.Applied, ProtocolFixtures.snapshot()),
        ).decodeToString()
        assertMismatch(
            response.replace("watch_command_response", "complete_current_set_response"),
            DecodeFailure.UnknownOperation,
        )
    }

    @Test
    fun `a schema-1 request decodes on the phone as UnsupportedVersion`() {
        val v1Get = "{\"operation\":\"get_active_workout\",\"schemaVersion\":1," +
            "\"correlationId\":\"${ProtocolFixtures.correlationId}\"}"
        listOf(v1Get, v1CommandJson(schemaVersion = 1)).forEach { request ->
            assertEquals(
                PhoneDecodeResult.UnsupportedVersion,
                WearProtocolCodec.decodeForPhone(request.encodeToByteArray(), authenticatedSourceNodeId = "watch-node"),
                request,
            )
        }
        listOf(
            v1Get.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
            v1Get.replace("\"operation\":\"get_active_workout\",", ""),
            v1Get.replace("\"get_active_workout\"", "7"),
        ).forEach { request ->
            assertEquals(
                PhoneDecodeResult.Dropped,
                WearProtocolCodec.decodeForPhone(request.encodeToByteArray(), authenticatedSourceNodeId = "watch-node"),
                request,
            )
        }
    }

    @Test
    fun `VERSION_ANSWER is the pinned bytes and a v1 watch reads it as a schema mismatch`() {
        val pinned = "{\"operation\":\"unsupported_version\",\"schemaVersion\":2}".toByteArray(StandardCharsets.UTF_8)
        assertContentEquals(pinned, WearProtocolCodec.VERSION_ANSWER)
        // The v1 watch's check (WearProtocolCodec.decodeForWatch at 1.53.x): an object with a
        // canonical integer schemaVersion other than 1 is UnsupportedSchemaVersion.
        val root = Json.parseToJsonElement(WearProtocolCodec.VERSION_ANSWER.decodeToString()).jsonObject
        val schema = root.getValue("schemaVersion").jsonPrimitive
        assertFalse(schema.isString)
        assertNotEquals(1, schema.int)
        assertIs<JsonPrimitive>(root.getValue("operation"))
        // A v2 watch never expects it.
        assertMismatch(WearProtocolCodec.VERSION_ANSWER.decodeToString(), DecodeFailure.UnknownOperation)
        WearProtocolCodec.VERSION_ANSWER[0] = 0
        assertContentEquals(pinned, WearProtocolCodec.VERSION_ANSWER, "every read is a fresh copy")
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
        val unsupported = valid.replace("\"schemaVersion\":2", "\"schemaVersion\":1")
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
        val response = WatchCommandResponse(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = ProtocolFixtures.correlationId,
            commandId = ProtocolFixtures.commandId,
            outcome = CommandOutcome.ProtocolRejected(
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

    /** The v1 `complete_current_set` request shape (1.53.x), at [schemaVersion]. */
    private fun v1CommandJson(schemaVersion: Int): String =
        "{\"operation\":\"complete_current_set\",\"schemaVersion\":$schemaVersion," +
            "\"correlationId\":\"${ProtocolFixtures.correlationId}\",\"commandId\":\"${ProtocolFixtures.commandId}\"," +
            "\"databaseEpoch\":\"${ProtocolFixtures.databaseEpoch}\"," +
            "\"sessionUuid\":\"${ProtocolFixtures.sessionUuid}\",\"sessionRevision\":11," +
            "\"mutationLeaseId\":\"${ProtocolFixtures.leaseId}\",\"mutationLeaseGeneration\":7," +
            "\"body\":{\"performedExerciseUuid\":\"${ProtocolFixtures.exerciseUuid}\",\"setPosition\":1,\"reps\":8," +
            "\"weightHundredthsKg\":10000,\"exerciseType\":\"weighted\",\"setType\":\"work\"}}"

    private fun phoneDecode(text: String): PhoneDecodeResult =
        WearProtocolCodec.decodeForPhone(text.encodeToByteArray(), authenticatedSourceNodeId = "watch-node")

    private fun assertMismatch(encoded: String, expected: DecodeFailure) {
        val result = assertIs<WatchDecodeResult.ProtocolMismatch>(
            WearProtocolCodec.decodeForWatch(encoded.encodeToByteArray()),
        )
        assertEquals(expected, result.failure)
    }
}
