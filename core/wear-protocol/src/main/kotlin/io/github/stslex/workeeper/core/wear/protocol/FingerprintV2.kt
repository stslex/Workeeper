// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class FingerprintPurpose(val wireByte: Byte) {
    STABLE_INTENT(wireByte = 0x01),
    DELIVERY_ATTEMPT(wireByte = 0x02),
}

/** The command kind a FingerprintV2 header names (wear-phase-2-exercise-list.md §5.6, P2). */
enum class FingerprintKind(val wireByte: Byte, private val stableFields: Int, private val attemptFields: Int) {
    COMPLETE_SET(wireByte = 0x01, COMPLETE_SET_STABLE_FIELDS, COMPLETE_SET_ATTEMPT_FIELDS),
    SET_EXERCISE_SKIPPED(wireByte = 0x02, SKIPPED_STABLE_FIELDS, SKIPPED_ATTEMPT_FIELDS),
    ;

    fun fieldCount(purpose: FingerprintPurpose): Int = when (purpose) {
        FingerprintPurpose.STABLE_INTENT -> stableFields
        FingerprintPurpose.DELIVERY_ATTEMPT -> attemptFields
    }

    companion object {
        fun of(body: WatchCommandBody): FingerprintKind = when (body) {
            is WatchCommandBody.CompleteSet -> COMPLETE_SET
            is WatchCommandBody.SetExerciseSkipped -> SET_EXERCISE_SKIPPED
        }
    }
}

/** A watch command's fingerprint input: the request without its correlation id. */
data class FingerprintCommand(
    val sourceNodeId: String,
    val schemaVersion: Int,
    val commandId: CanonicalUuid,
    val databaseEpoch: CanonicalUuid,
    val sessionUuid: CanonicalUuid,
    val sessionRevision: Long,
    val body: WatchCommandBody,
    val mutationLeaseId: CanonicalUuid,
    val mutationLeaseGeneration: Long,
) {
    val kind: FingerprintKind get() = FingerprintKind.of(body)
}

class UnsupportedFingerprintVersionException(version: Int) :
    IllegalArgumentException("Unsupported fingerprint encoding version: $version")

class FingerprintValue private constructor(private val bytes: ByteArray) {

    val encoded: ByteArray
        get() = bytes.copyOf()

    fun constantTimeEquals(other: FingerprintValue): Boolean =
        MessageDigest.isEqual(bytes, other.bytes)

    override fun equals(other: Any?): Boolean =
        other is FingerprintValue && constantTimeEquals(other)

    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {

        const val ENCODED_SIZE: Int = 34

        fun parse(bytes: ByteArray): FingerprintValue {
            require(bytes.size == ENCODED_SIZE) { "A fingerprint must retain its full digest" }
            val version = ByteBuffer.wrap(bytes).short.toInt() and UNSIGNED_SHORT_MASK
            if (version != FingerprintV2.ENCODING_VERSION) {
                throw UnsupportedFingerprintVersionException(version)
            }
            return FingerprintValue(bytes.copyOf())
        }

        internal fun trusted(bytes: ByteArray): FingerprintValue = FingerprintValue(bytes.copyOf())
    }
}

/**
 * The command fingerprint (wear-phase-2-exercise-list.md §5.6): magic, encoding version, purpose and
 * command kind, then the fields in ascending tag order. `complete_set` keeps FingerprintV1's tags.
 */
object FingerprintV2 {

    const val ENCODING_VERSION: Int = 2

    private val MAGIC = MAGIC_TEXT.toByteArray(StandardCharsets.US_ASCII)

    fun preimage(command: FingerprintCommand, purpose: FingerprintPurpose): ByteArray {
        val nodeBytes = requireNotNull(strictUtf8OrNull(command.sourceNodeId)) {
            "Source node ID must be valid Unicode"
        }
        require(nodeBytes.isNotEmpty()) { "Source node ID must not be empty" }
        val attempt = purpose == FingerprintPurpose.DELIVERY_ATTEMPT

        val fields = buildList {
            add(Field(TAG_SOURCE_NODE, nodeBytes))
            add(Field(TAG_SCHEMA_VERSION, intBytes(command.schemaVersion)))
            add(Field(TAG_COMMAND_ID, command.commandId.toNetworkBytes()))
            add(Field(TAG_DATABASE_EPOCH, command.databaseEpoch.toNetworkBytes()))
            add(Field(TAG_SESSION, command.sessionUuid.toNetworkBytes()))
            add(Field(TAG_REVISION, longBytes(command.sessionRevision)))
            add(Field(TAG_EXERCISE, command.body.performedExerciseUuid.toNetworkBytes()))
            when (val body = command.body) {
                is WatchCommandBody.CompleteSet -> {
                    add(Field(TAG_SET_POSITION, intBytes(body.setPosition)))
                    add(Field(TAG_REPS, intBytes(body.reps)))
                    add(Field(TAG_WEIGHT, nullableIntBytes(body.weightHundredthsKg)))
                    add(Field(TAG_EXERCISE_TYPE, byteArrayOf(body.exerciseType.fingerprintByte)))
                    add(Field(TAG_SET_TYPE, byteArrayOf(body.setType.fingerprintByte)))
                    if (attempt) {
                        add(Field(TAG_SET_LEASE_ID, command.mutationLeaseId.toNetworkBytes()))
                        add(Field(TAG_SET_LEASE_GENERATION, longBytes(command.mutationLeaseGeneration)))
                    }
                }
                is WatchCommandBody.SetExerciseSkipped -> {
                    add(Field(TAG_SKIPPED, byteArrayOf(if (body.skipped) SKIP else RETURN)))
                    if (attempt) {
                        add(Field(TAG_SKIP_LEASE_ID, command.mutationLeaseId.toNetworkBytes()))
                        add(Field(TAG_SKIP_LEASE_GENERATION, longBytes(command.mutationLeaseGeneration)))
                    }
                }
            }
        }
        require(fields.size == command.kind.fieldCount(purpose))
        require(fields.zipWithNext().all { (left, right) -> left.tag < right.tag })

        return ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeShort(ENCODING_VERSION)
                data.writeByte(purpose.wireByte.toInt())
                data.writeByte(command.kind.wireByte.toInt())
                data.writeShort(fields.size)
                fields.forEach { field ->
                    data.writeByte(field.tag)
                    data.writeInt(field.bytes.size)
                    data.write(field.bytes)
                }
            }
            output.toByteArray()
        }
    }

    fun fingerprint(command: FingerprintCommand, purpose: FingerprintPurpose): FingerprintValue {
        val digest = MessageDigest.getInstance("SHA-256").digest(preimage(command, purpose))
        val encoded = ByteBuffer.allocate(FingerprintValue.ENCODED_SIZE)
            .putShort(ENCODING_VERSION.toShort())
            .put(digest)
            .array()
        return FingerprintValue.trusted(encoded)
    }

    /** Strict parser used by cross-runtime fixtures; fingerprints are never accepted from ad-hoc text. */
    fun isCanonicalPreimage(bytes: ByteArray, purpose: FingerprintPurpose, kind: FingerprintKind): Boolean =
        runCatching {
            val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            if (input.remaining() < HEADER_BYTES) return false
            if (ByteArray(MAGIC.size).also(input::get).contentEquals(MAGIC).not()) return false
            if ((input.short.toInt() and UNSIGNED_SHORT_MASK) != ENCODING_VERSION) return false
            if (input.get() != purpose.wireByte) return false
            if (input.get() != kind.wireByte) return false
            val count = kind.fieldCount(purpose)
            if ((input.short.toInt() and UNSIGNED_SHORT_MASK) != count) return false

            repeat(count) { index ->
                if (input.remaining() < FIELD_HEADER_BYTES) return false
                val expectedTag = index + 1
                if ((input.get().toInt() and UNSIGNED_BYTE_MASK) != expectedTag) return false
                val length = input.int
                if (length < 0 || length > input.remaining()) return false
                val value = ByteArray(length).also(input::get)
                if (!validField(kind, expectedTag, value)) return false
            }
            !input.hasRemaining()
        }.getOrDefault(false)

    private fun validField(kind: FingerprintKind, tag: Int, value: ByteArray): Boolean = when (tag) {
        TAG_SOURCE_NODE -> value.isNotEmpty() && runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
        }.isSuccess
        TAG_SCHEMA_VERSION -> value.size == Int.SIZE_BYTES
        TAG_COMMAND_ID, TAG_DATABASE_EPOCH, TAG_SESSION, TAG_EXERCISE -> value.size == UUID_BYTES
        TAG_REVISION -> value.size == Long.SIZE_BYTES
        else -> when (kind) {
            FingerprintKind.COMPLETE_SET -> validCompleteSetField(tag, value)
            FingerprintKind.SET_EXERCISE_SKIPPED -> validSkippedField(tag, value)
        }
    }

    private fun validCompleteSetField(tag: Int, value: ByteArray): Boolean = when (tag) {
        TAG_SET_POSITION, TAG_REPS -> value.size == Int.SIZE_BYTES
        TAG_WEIGHT -> value.contentEquals(byteArrayOf(ABSENT)) ||
            (value.size == 1 + Int.SIZE_BYTES && value.first() == PRESENT)
        TAG_EXERCISE_TYPE -> value.size == 1 && value.first() in ExerciseTypeWire.entries.map { it.fingerprintByte }
        TAG_SET_TYPE -> value.size == 1 && value.first() in SetTypeWire.entries.map { it.fingerprintByte }
        TAG_SET_LEASE_ID -> value.size == UUID_BYTES
        TAG_SET_LEASE_GENERATION -> value.size == Long.SIZE_BYTES
        else -> false
    }

    private fun validSkippedField(tag: Int, value: ByteArray): Boolean = when (tag) {
        TAG_SKIPPED -> value.size == 1 && (value.first() == SKIP || value.first() == RETURN)
        TAG_SKIP_LEASE_ID -> value.size == UUID_BYTES
        TAG_SKIP_LEASE_GENERATION -> value.size == Long.SIZE_BYTES
        else -> false
    }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()

    private fun longBytes(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()

    private fun nullableIntBytes(value: Int?): ByteArray = if (value == null) {
        byteArrayOf(ABSENT)
    } else {
        byteArrayOf(PRESENT) + intBytes(value)
    }

    private data class Field(val tag: Int, val bytes: ByteArray)
}

// The normative bytes of §5.6, named.
private const val MAGIC_TEXT = "WKWF"
private const val HEADER_BYTES = 10
private const val FIELD_HEADER_BYTES = 5
private const val UUID_BYTES = 16
private const val UNSIGNED_BYTE_MASK = 0xff
private const val UNSIGNED_SHORT_MASK = 0xffff
private const val ABSENT: Byte = 0x00
private const val PRESENT: Byte = 0x01
private const val RETURN: Byte = 0x00
private const val SKIP: Byte = 0x01

private const val COMPLETE_SET_STABLE_FIELDS = 12
private const val COMPLETE_SET_ATTEMPT_FIELDS = 14
private const val SKIPPED_STABLE_FIELDS = 8
private const val SKIPPED_ATTEMPT_FIELDS = 10

// Tags shared by both kinds.
private const val TAG_SOURCE_NODE = 0x01
private const val TAG_SCHEMA_VERSION = 0x02
private const val TAG_COMMAND_ID = 0x03
private const val TAG_DATABASE_EPOCH = 0x04
private const val TAG_SESSION = 0x05
private const val TAG_REVISION = 0x06
private const val TAG_EXERCISE = 0x07

// complete_set: FingerprintV1's tags.
private const val TAG_SET_POSITION = 0x08
private const val TAG_REPS = 0x09
private const val TAG_WEIGHT = 0x0a
private const val TAG_EXERCISE_TYPE = 0x0b
private const val TAG_SET_TYPE = 0x0c
private const val TAG_SET_LEASE_ID = 0x0d
private const val TAG_SET_LEASE_GENERATION = 0x0e

// set_exercise_skipped.
private const val TAG_SKIPPED = 0x08
private const val TAG_SKIP_LEASE_ID = 0x09
private const val TAG_SKIP_LEASE_GENERATION = 0x0a
