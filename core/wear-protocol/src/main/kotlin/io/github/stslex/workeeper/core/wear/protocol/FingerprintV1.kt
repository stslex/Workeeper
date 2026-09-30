// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("MagicNumber") // Normative FingerprintV1 purpose, tag, and framing bytes.

package io.github.stslex.workeeper.core.wear.protocol

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class FingerprintPurpose(val wireByte: Byte, val fieldCount: Int) {
    STABLE_INTENT(wireByte = 0x01, fieldCount = 12),
    DELIVERY_ATTEMPT(wireByte = 0x02, fieldCount = 14),
}

data class FingerprintCommand(
    val sourceNodeId: String,
    val schemaVersion: Int,
    val commandId: CanonicalUuid,
    val databaseEpoch: CanonicalUuid,
    val sessionUuid: CanonicalUuid,
    val sessionRevision: Long,
    val performedExerciseUuid: CanonicalUuid,
    val setPosition: Int,
    val reps: Int,
    val weightHundredthsKg: Int?,
    val exerciseType: ExerciseTypeWire,
    val setType: SetTypeWire,
    val mutationLeaseId: CanonicalUuid,
    val mutationLeaseGeneration: Long,
)

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
            require(bytes.size == ENCODED_SIZE) { "FingerprintV1 must retain its full digest" }
            val version = ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)
            if (version != FingerprintV1.ENCODING_VERSION) {
                throw UnsupportedFingerprintVersionException(version)
            }
            return FingerprintValue(bytes.copyOf())
        }

        internal fun trusted(bytes: ByteArray): FingerprintValue = FingerprintValue(bytes.copyOf())
    }
}

object FingerprintV1 {

    const val ENCODING_VERSION: Int = 1

    private val MAGIC = byteArrayOf(0x57, 0x4b, 0x57, 0x46)

    fun preimage(command: FingerprintCommand, purpose: FingerprintPurpose): ByteArray {
        val nodeBytes = requireNotNull(strictUtf8OrNull(command.sourceNodeId)) {
            "Source node ID must be valid Unicode"
        }
        require(nodeBytes.isNotEmpty()) { "Source node ID must not be empty" }

        val fields = buildList {
            add(Field(0x01, nodeBytes))
            add(Field(0x02, intBytes(command.schemaVersion)))
            add(Field(0x03, command.commandId.toNetworkBytes()))
            add(Field(0x04, command.databaseEpoch.toNetworkBytes()))
            add(Field(0x05, command.sessionUuid.toNetworkBytes()))
            add(Field(0x06, longBytes(command.sessionRevision)))
            add(Field(0x07, command.performedExerciseUuid.toNetworkBytes()))
            add(Field(0x08, intBytes(command.setPosition)))
            add(Field(0x09, intBytes(command.reps)))
            add(Field(0x0a, nullableIntBytes(command.weightHundredthsKg)))
            add(Field(0x0b, byteArrayOf(command.exerciseType.fingerprintByte)))
            add(Field(0x0c, byteArrayOf(command.setType.fingerprintByte)))
            if (purpose == FingerprintPurpose.DELIVERY_ATTEMPT) {
                add(Field(0x0d, command.mutationLeaseId.toNetworkBytes()))
                add(Field(0x0e, longBytes(command.mutationLeaseGeneration)))
            }
        }
        require(fields.size == purpose.fieldCount)
        require(fields.zipWithNext().all { (left, right) -> left.tag < right.tag })

        return ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeShort(ENCODING_VERSION)
                data.writeByte(purpose.wireByte.toInt())
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
        val encoded = ByteArray(FingerprintValue.ENCODED_SIZE)
        encoded[0] = 0x00
        encoded[1] = ENCODING_VERSION.toByte()
        digest.copyInto(encoded, destinationOffset = 2)
        return FingerprintValue.trusted(encoded)
    }

    /** Strict parser used by cross-runtime fixtures; fingerprints are never accepted from ad-hoc text. */
    fun isCanonicalPreimage(bytes: ByteArray, purpose: FingerprintPurpose): Boolean = runCatching {
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (input.remaining() < HEADER_BYTES) return false
        if (ByteArray(MAGIC.size).also(input::get).contentEquals(MAGIC).not()) return false
        if ((input.short.toInt() and 0xffff) != ENCODING_VERSION) return false
        if (input.get() != purpose.wireByte) return false
        if ((input.short.toInt() and 0xffff) != purpose.fieldCount) return false

        repeat(purpose.fieldCount) { index ->
            if (input.remaining() < FIELD_HEADER_BYTES) return false
            val expectedTag = index + 1
            if ((input.get().toInt() and 0xff) != expectedTag) return false
            val length = input.int
            if (length < 0 || length > input.remaining()) return false
            val value = ByteArray(length).also(input::get)
            if (!validField(expectedTag, value)) return false
        }
        !input.hasRemaining()
    }.getOrDefault(false)

    private fun validField(tag: Int, value: ByteArray): Boolean = when (tag) {
        0x01 -> value.isNotEmpty() && runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
        }.isSuccess
        0x02, 0x08, 0x09 -> value.size == Int.SIZE_BYTES
        0x03, 0x04, 0x05, 0x07, 0x0d -> value.size == UUID_BYTES
        0x06, 0x0e -> value.size == Long.SIZE_BYTES
        0x0a -> value.contentEquals(byteArrayOf(0x00)) ||
            (value.size == 1 + Int.SIZE_BYTES && value.first() == 0x01.toByte())
        0x0b -> value.size == 1 && value.first() in setOf(0x01.toByte(), 0x02.toByte())
        0x0c -> value.size == 1 && value.first() in
            setOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte())
        else -> false
    }

    private fun intBytes(value: Int): ByteArray = ByteArrayOutputStream(Int.SIZE_BYTES).use { output ->
        DataOutputStream(output).use { it.writeInt(value) }
        output.toByteArray()
    }

    private fun longBytes(value: Long): ByteArray = ByteArrayOutputStream(Long.SIZE_BYTES).use { output ->
        DataOutputStream(output).use { it.writeLong(value) }
        output.toByteArray()
    }

    private fun nullableIntBytes(value: Int?): ByteArray = if (value == null) {
        byteArrayOf(0x00)
    } else {
        byteArrayOf(0x01) + intBytes(value)
    }

    private data class Field(val tag: Int, val bytes: ByteArray)

    private const val HEADER_BYTES = 9
    private const val FIELD_HEADER_BYTES = 5
    private const val UUID_BYTES = 16
}
