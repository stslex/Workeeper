// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.ByteBuffer
import java.util.UUID

private val UUID_TEXT = Regex(
    pattern = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
)

/** A parsed RFC-4122 128-bit value whose wire spelling is always canonical lower-case text. */
@JvmInline
@Serializable(with = CanonicalUuidSerializer::class)
value class CanonicalUuid private constructor(val value: String) {

    fun toJavaUuid(): UUID = UUID.fromString(value)

    fun toNetworkBytes(): ByteArray = ByteBuffer.allocate(UUID_BYTES)
        .putLong(toJavaUuid().mostSignificantBits)
        .putLong(toJavaUuid().leastSignificantBits)
        .array()

    override fun toString(): String = value

    companion object {

        private const val UUID_BYTES = 16

        fun parse(text: String): CanonicalUuid {
            require(UUID_TEXT.matches(text)) { "UUID must use the full RFC-4122 text shape" }
            return CanonicalUuid(UUID.fromString(text).toString())
        }

        fun from(uuid: UUID): CanonicalUuid = CanonicalUuid(uuid.toString())

        fun random(): CanonicalUuid = from(UUID.randomUUID())
    }
}

object CanonicalUuidSerializer : KSerializer<CanonicalUuid> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "CanonicalUuid",
        kind = PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: CanonicalUuid) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): CanonicalUuid =
        CanonicalUuid.parse(decoder.decodeString())
}
