// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.nio.charset.StandardCharsets

class EnvelopeTooLargeException(size: Int) :
    IllegalArgumentException("Protocol envelope is $size bytes; maximum is ${WearProtocol.MAX_ENVELOPE_BYTES}")

sealed interface DecodeFailure {
    data object Oversized : DecodeFailure
    data object Malformed : DecodeFailure
    data object UnsupportedSchemaVersion : DecodeFailure
    data object UnknownOperation : DecodeFailure
    data object InvalidPairing : DecodeFailure
}

sealed interface WatchDecodeResult {
    data class Success(val envelope: WearEnvelope) : WatchDecodeResult
    data class ProtocolMismatch(val failure: DecodeFailure) : WatchDecodeResult
}

data class CompleteCommandRouting(
    val schemaVersion: Int,
    val correlationId: CanonicalUuid,
    val commandId: CanonicalUuid,
    val databaseEpoch: CanonicalUuid,
    val sessionUuid: CanonicalUuid,
    val sessionRevision: Long,
    val mutationLeaseId: CanonicalUuid,
    val mutationLeaseGeneration: Long,
)

sealed interface PhoneDecodeResult {
    data class Success(val request: WearEnvelope) : PhoneDecodeResult
    data class CorrelatedProtocolRejection(
        val routing: CompleteCommandRouting,
        val reason: ProtocolRejectionReason,
    ) : PhoneDecodeResult

    data object Dropped : PhoneDecodeResult
}

object WearProtocolCodec {

    private val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        allowSpecialFloatingPointValues = false
        allowStructuredMapKeys = false
        prettyPrint = false
        useArrayPolymorphism = false
    }

    fun encode(envelope: WearEnvelope): ByteArray {
        require(envelope.schemaVersion == WearProtocol.SCHEMA_VERSION)
        if (envelope is CompleteCurrentSetResponse) {
            require(ProtocolPairingValidator.isValid(envelope.outcome, envelope.replacement.payload)) {
                "Command outcome/replacement pairing is not permitted"
            }
        }
        val bytes = json.encodeToString(WearEnvelope.serializer(), envelope)
            .toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > WearProtocol.MAX_ENVELOPE_BYTES) {
            throw EnvelopeTooLargeException(bytes.size)
        }
        return bytes
    }

    fun decodeForWatch(bytes: ByteArray): WatchDecodeResult {
        if (bytes.size > WearProtocol.MAX_ENVELOPE_BYTES) {
            return WatchDecodeResult.ProtocolMismatch(DecodeFailure.Oversized)
        }
        val root = parseObject(bytes)
            ?: return WatchDecodeResult.ProtocolMismatch(DecodeFailure.Malformed)
        val schema = root.strictInt("schemaVersion")
            ?: return WatchDecodeResult.ProtocolMismatch(DecodeFailure.Malformed)
        if (schema != WearProtocol.SCHEMA_VERSION) {
            return WatchDecodeResult.ProtocolMismatch(DecodeFailure.UnsupportedSchemaVersion)
        }
        val operation = root.strictString("operation")
            ?: return WatchDecodeResult.ProtocolMismatch(DecodeFailure.Malformed)
        if (operation != ACTIVE_SNAPSHOT_OPERATION && operation != COMPLETE_RESPONSE_OPERATION) {
            return WatchDecodeResult.ProtocolMismatch(DecodeFailure.UnknownOperation)
        }
        val decoded = runCatching { json.decodeFromJsonElement<WearEnvelope>(root) }
            .getOrElse { return WatchDecodeResult.ProtocolMismatch(DecodeFailure.Malformed) }
        if (decoded is CompleteCurrentSetResponse &&
            !ProtocolPairingValidator.isValid(decoded.outcome, decoded.replacement.payload)
        ) {
            return WatchDecodeResult.ProtocolMismatch(DecodeFailure.InvalidPairing)
        }
        return WatchDecodeResult.Success(decoded)
    }

    /**
     * Decodes requests at the phone boundary. Only a complete authenticated command routing tuple
     * is allowed to turn an invalid numeric body into a correlatable semantic rejection.
     */
    fun decodeForPhone(bytes: ByteArray, authenticatedSourceNodeId: String): PhoneDecodeResult {
        if (bytes.size > WearProtocol.MAX_ENVELOPE_BYTES || !validSourceNode(authenticatedSourceNodeId)) {
            return PhoneDecodeResult.Dropped
        }
        val root = parseObject(bytes) ?: return PhoneDecodeResult.Dropped
        val operation = root.strictString("operation") ?: return PhoneDecodeResult.Dropped
        return when (operation) {
            GET_OPERATION -> decodeGet(root)
            COMPLETE_OPERATION -> decodeComplete(root)
            else -> PhoneDecodeResult.Dropped
        }
    }

    private fun decodeGet(root: JsonObject): PhoneDecodeResult {
        if (root.strictInt("schemaVersion") != WearProtocol.SCHEMA_VERSION) {
            return PhoneDecodeResult.Dropped
        }
        val request = runCatching { json.decodeFromJsonElement<WearEnvelope>(root) }
            .getOrElse { return PhoneDecodeResult.Dropped }
        return if (request is GetActiveWorkoutRequest) {
            PhoneDecodeResult.Success(request)
        } else {
            PhoneDecodeResult.Dropped
        }
    }

    private fun decodeComplete(root: JsonObject): PhoneDecodeResult {
        val routing = decodeRouting(root) ?: return PhoneDecodeResult.Dropped
        val body = root["body"] as? JsonObject ?: return PhoneDecodeResult.Dropped
        if (!body.hasStrictInt("reps") || !body.hasStrictNullableInt("weightHundredthsKg")) {
            return PhoneDecodeResult.CorrelatedProtocolRejection(
                routing = routing,
                reason = ProtocolRejectionReason.INVALID_NUMERIC_ENCODING,
            )
        }
        val decoded = try {
            json.decodeFromJsonElement<WearEnvelope>(root)
        } catch (_: SerializationException) {
            return PhoneDecodeResult.Dropped
        } catch (_: IllegalArgumentException) {
            return PhoneDecodeResult.Dropped
        }
        return if (decoded is CompleteCurrentSetRequest) {
            PhoneDecodeResult.Success(decoded)
        } else {
            PhoneDecodeResult.Dropped
        }
    }

    private fun decodeRouting(root: JsonObject): CompleteCommandRouting? {
        val schema = root.strictInt("schemaVersion") ?: return null
        if (schema != WearProtocol.SCHEMA_VERSION) return null
        if (root.strictString("operation") != COMPLETE_OPERATION) return null
        return runCatching {
            CompleteCommandRouting(
                schemaVersion = schema,
                correlationId = CanonicalUuid.parse(root.strictString("correlationId") ?: return null),
                commandId = CanonicalUuid.parse(root.strictString("commandId") ?: return null),
                databaseEpoch = CanonicalUuid.parse(root.strictString("databaseEpoch") ?: return null),
                sessionUuid = CanonicalUuid.parse(root.strictString("sessionUuid") ?: return null),
                sessionRevision = root.strictLong("sessionRevision") ?: return null,
                mutationLeaseId = CanonicalUuid.parse(root.strictString("mutationLeaseId") ?: return null),
                mutationLeaseGeneration = root.strictLong("mutationLeaseGeneration") ?: return null,
            )
        }.getOrNull()
    }

    private fun parseObject(bytes: ByteArray): JsonObject? = runCatching {
        val text = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
        json.parseToJsonElement(text).jsonObject
    }.getOrNull()

    private fun validSourceNode(value: String): Boolean {
        val bytes = strictUtf8OrNull(value) ?: return false
        return bytes.isNotEmpty() && bytes.size <= MAX_SOURCE_NODE_ID_UTF8_BYTES
    }

    private fun JsonObject.strictString(name: String): String? {
        val primitive = get(name) as? JsonPrimitive ?: return null
        return primitive.takeIf(JsonPrimitive::isString)?.content
    }

    private fun JsonObject.strictInt(name: String): Int? {
        val primitive = get(name) as? JsonPrimitive ?: return null
        return primitive.takeUnless(JsonPrimitive::isString)
            ?.content
            ?.takeIf(::isCanonicalIntegerToken)
            ?.toIntOrNull()
    }

    private fun JsonObject.strictLong(name: String): Long? {
        val primitive = get(name) as? JsonPrimitive ?: return null
        return primitive.takeUnless(JsonPrimitive::isString)
            ?.content
            ?.takeIf(::isCanonicalIntegerToken)
            ?.toLongOrNull()
    }

    private fun JsonObject.hasStrictInt(name: String): Boolean = strictInt(name) != null

    private fun JsonObject.hasStrictNullableInt(name: String): Boolean {
        val element: JsonElement = get(name) ?: return false
        return element === JsonNull || (
            element is JsonPrimitive && !element.isString &&
                isCanonicalIntegerToken(element.content) && element.intOrNull != null
            )
    }

    private fun isCanonicalIntegerToken(token: String): Boolean = INTEGER_TOKEN.matches(token)

    private const val GET_OPERATION = "get_active_workout"
    private const val COMPLETE_OPERATION = "complete_current_set"
    private const val ACTIVE_SNAPSHOT_OPERATION = "active_workout_snapshot"
    private const val COMPLETE_RESPONSE_OPERATION = "complete_current_set_response"
    private const val MAX_SOURCE_NODE_ID_UTF8_BYTES = 1_024
    private val INTEGER_TOKEN = Regex("-?(0|[1-9][0-9]*)")
}

object ProtocolPairingValidator {

    fun isValid(outcome: CompleteCommandOutcome, payload: SnapshotPayload): Boolean = when (outcome) {
        is CompleteCommandOutcome.NoActiveSession -> payload is SnapshotPayload.NoSession
        is CompleteCommandOutcome.TargetChanged -> payload !is SnapshotPayload.NoSession
        is CompleteCommandOutcome.InvalidValues,
        is CompleteCommandOutcome.ImmutableTypeMismatch,
        -> payload.isUnavailableTarget()
        is CompleteCommandOutcome.ProtocolRejected -> payload.isReadOnly()
        is CompleteCommandOutcome.RetryableTemporaryFailure -> payload.isGrantedTarget()
        else -> true
    }

    private fun SnapshotPayload.isUnavailableTarget(): Boolean =
        this is SnapshotPayload.ActiveWithTarget && mutationAuthority is MutationAuthority.Unavailable

    private fun SnapshotPayload.isGrantedTarget(): Boolean =
        this is SnapshotPayload.ActiveWithTarget && mutationAuthority is MutationAuthority.Granted

    private fun SnapshotPayload.isReadOnly(): Boolean = when (this) {
        is SnapshotPayload.NoSession,
        is SnapshotPayload.PhoneActionRequired,
        is SnapshotPayload.WorkoutComplete,
        -> true
        is SnapshotPayload.ActiveWithTarget -> mutationAuthority is MutationAuthority.Unavailable
    }
}

object EncodedEnvelopeGate {

    fun requestOrNull(candidate: ByteArray): ByteArray? =
        candidate.takeIf { it.size <= WearProtocol.MAX_ENVELOPE_BYTES }?.copyOf()

    fun snapshotOrFallback(candidate: ByteArray, fallback: ByteArray): ByteArray {
        if (candidate.size <= WearProtocol.MAX_ENVELOPE_BYTES) return candidate.copyOf()
        require(fallback.size < WearProtocol.MAX_PAYLOAD_TOO_LARGE_FALLBACK_BYTES)
        require(fallback.size <= WearProtocol.MAX_ENVELOPE_BYTES)
        return fallback.copyOf()
    }
}
