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

data class CommandRouting(
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
        val routing: CommandRouting,
        val reason: ProtocolRejectionReason,
    ) : PhoneDecodeResult

    /** A request of another schema version: answered with [WearProtocolCodec.VERSION_ANSWER] (P3). */
    data object UnsupportedVersion : PhoneDecodeResult

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

    /**
     * The phone's whole answer to a request of another schema version (wear-phase-2-exercise-list.md
     * §5.1, P3): a v1 watch decodes it as an unsupported schema and asks for an update.
     */
    val VERSION_ANSWER: ByteArray
        get() = VERSION_ANSWER_JSON.toByteArray(StandardCharsets.UTF_8)

    fun encode(envelope: WearEnvelope): ByteArray {
        require(envelope.schemaVersion == WearProtocol.SCHEMA_VERSION)
        if (envelope is WatchCommandResponse) {
            require(ProtocolPairingValidator.isValid(envelope.outcome, envelope.replacement.payload)) {
                "Command outcome/replacement pairing is not permitted"
            }
        }
        val bytes = serialize(envelope)
        if (bytes.size > WearProtocol.MAX_ENVELOPE_BYTES) {
            throw EnvelopeTooLargeException(bytes.size)
        }
        return bytes
    }

    /** The envelope's bytes with no pairing or size check: the budget measures sizes with it (§5.7). */
    internal fun serialize(envelope: WearEnvelope): ByteArray =
        json.encodeToString(WearEnvelope.serializer(), envelope).toByteArray(StandardCharsets.UTF_8)

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
        if (operation != ACTIVE_SNAPSHOT_OPERATION && operation != COMMAND_RESPONSE_OPERATION) {
            return WatchDecodeResult.ProtocolMismatch(DecodeFailure.UnknownOperation)
        }
        val decoded = runCatching { json.decodeFromJsonElement<WearEnvelope>(root) }
            .getOrElse { return WatchDecodeResult.ProtocolMismatch(DecodeFailure.Malformed) }
        if (decoded is WatchCommandResponse &&
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
        val schema = root.strictInt("schemaVersion") ?: return PhoneDecodeResult.Dropped
        if (schema != WearProtocol.SCHEMA_VERSION) return PhoneDecodeResult.UnsupportedVersion
        return when (operation) {
            GET_OPERATION -> decodeGet(root)
            COMMAND_OPERATION -> decodeCommand(root)
            else -> PhoneDecodeResult.Dropped
        }
    }

    private fun decodeGet(root: JsonObject): PhoneDecodeResult {
        val request = runCatching { json.decodeFromJsonElement<WearEnvelope>(root) }
            .getOrElse { return PhoneDecodeResult.Dropped }
        return if (request is GetActiveWorkoutRequest) {
            PhoneDecodeResult.Success(request)
        } else {
            PhoneDecodeResult.Dropped
        }
    }

    private fun decodeCommand(root: JsonObject): PhoneDecodeResult {
        val routing = decodeRouting(root) ?: return PhoneDecodeResult.Dropped
        val body = root["body"] as? JsonObject ?: return PhoneDecodeResult.Dropped
        when (body.strictString("kind")) {
            COMPLETE_SET_KIND -> if (!body.hasStrictInt("reps") || !body.hasStrictNullableInt("weightHundredthsKg")) {
                return PhoneDecodeResult.CorrelatedProtocolRejection(
                    routing = routing,
                    reason = ProtocolRejectionReason.INVALID_NUMERIC_ENCODING,
                )
            }
            SET_EXERCISE_SKIPPED_KIND -> if (!body.hasStrictBoolean("skipped")) return PhoneDecodeResult.Dropped
            else -> return PhoneDecodeResult.Dropped
        }
        val decoded = try {
            json.decodeFromJsonElement<WearEnvelope>(root)
        } catch (_: SerializationException) {
            return PhoneDecodeResult.Dropped
        } catch (_: IllegalArgumentException) {
            return PhoneDecodeResult.Dropped
        }
        return if (decoded is WatchCommandRequest) {
            PhoneDecodeResult.Success(decoded)
        } else {
            PhoneDecodeResult.Dropped
        }
    }

    private fun decodeRouting(root: JsonObject): CommandRouting? {
        val schema = root.strictInt("schemaVersion") ?: return null
        if (schema != WearProtocol.SCHEMA_VERSION) return null
        if (root.strictString("operation") != COMMAND_OPERATION) return null
        return runCatching {
            CommandRouting(
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

    private const val GET_OPERATION = "get_active_workout"
    private const val COMMAND_OPERATION = "watch_command"
    private const val ACTIVE_SNAPSHOT_OPERATION = "active_workout_snapshot"
    private const val COMMAND_RESPONSE_OPERATION = "watch_command_response"
    private const val COMPLETE_SET_KIND = "complete_set"
    private const val SET_EXERCISE_SKIPPED_KIND = "set_exercise_skipped"
    private const val VERSION_ANSWER_JSON =
        "{\"operation\":\"unsupported_version\",\"schemaVersion\":${WearProtocol.SCHEMA_VERSION}}"
    private const val MAX_SOURCE_NODE_ID_UTF8_BYTES = 1_024
}

/** A JSON `true` or `false` literal: a quoted "true" is a string, not a boolean. */
private fun JsonObject.hasStrictBoolean(name: String): Boolean {
    val primitive = get(name) as? JsonPrimitive ?: return false
    return !primitive.isString && (primitive.content == "true" || primitive.content == "false")
}

private fun isCanonicalIntegerToken(token: String): Boolean = INTEGER_TOKEN.matches(token)

private val INTEGER_TOKEN = Regex("-?(0|[1-9][0-9]*)")

/** wear-phase-2-exercise-list.md §5.5: the replacements each outcome may carry, on both sides. */
object ProtocolPairingValidator {

    fun isValid(outcome: CommandOutcome, payload: SnapshotPayload): Boolean = when (outcome) {
        is CommandOutcome.NoActiveSession -> payload is SnapshotPayload.NoSession
        is CommandOutcome.TargetChanged -> payload !is SnapshotPayload.NoSession
        is CommandOutcome.InvalidValues,
        is CommandOutcome.ImmutableTypeMismatch,
        -> payload.isUnavailableActive()
        is CommandOutcome.ProtocolRejected -> payload.isReadOnly()
        is CommandOutcome.RetryableTemporaryFailure -> payload.isGrantedActive()
        else -> true
    }

    private fun SnapshotPayload.isUnavailableActive(): Boolean =
        this is SnapshotPayload.Active && mutationAuthority is MutationAuthority.Unavailable

    private fun SnapshotPayload.isGrantedActive(): Boolean =
        this is SnapshotPayload.Active && mutationAuthority is MutationAuthority.Granted

    private fun SnapshotPayload.isReadOnly(): Boolean = when (this) {
        is SnapshotPayload.NoSession,
        is SnapshotPayload.PayloadTooLarge,
        -> true
        is SnapshotPayload.Active -> mutationAuthority is MutationAuthority.Unavailable
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
