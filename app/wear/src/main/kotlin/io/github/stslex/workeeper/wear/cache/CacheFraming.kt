// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("MagicNumber") // Fixed cache framing flags and binary field widths.

package io.github.stslex.workeeper.wear.cache

import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal data class CacheRecord(
    val receivedAtElapsedRealtimeMs: Long,
    val bootCount: Int,
    val effectiveMutationWindowMs: Long?,
    val ongoingStopAtElapsedRealtimeMs: Long?,
    val connection: CachedConnection,
    val payload: ByteArray?,
) {

    val isNoSessionTombstone: Boolean
        get() = payload == null

    init {
        require(receivedAtElapsedRealtimeMs >= 0L)
        require(bootCount >= 0)
        require(
            effectiveMutationWindowMs == null ||
                effectiveMutationWindowMs in 1L..WearProtocol.MAX_MUTATION_WINDOW_MS,
        )
        require(ongoingStopAtElapsedRealtimeMs == null || effectiveMutationWindowMs != null)
        require(
            ongoingStopAtElapsedRealtimeMs == null ||
                ongoingStopAtElapsedRealtimeMs >= receivedAtElapsedRealtimeMs,
        )
        require(payload == null || payload.isNotEmpty())
        require(payload == null || payload.size <= WearProtocol.MAX_ENVELOPE_BYTES)
        require(payload != null || effectiveMutationWindowMs == null)
        require(payload != null || ongoingStopAtElapsedRealtimeMs == null)
    }

    override fun equals(other: Any?): Boolean = other is CacheRecord &&
        receivedAtElapsedRealtimeMs == other.receivedAtElapsedRealtimeMs &&
        bootCount == other.bootCount &&
        effectiveMutationWindowMs == other.effectiveMutationWindowMs &&
        ongoingStopAtElapsedRealtimeMs == other.ongoingStopAtElapsedRealtimeMs &&
        connection == other.connection &&
        payload.contentEqualsNullable(other.payload)

    override fun hashCode(): Int {
        var result = receivedAtElapsedRealtimeMs.hashCode()
        result = 31 * result + bootCount
        result = 31 * result + (effectiveMutationWindowMs?.hashCode() ?: 0)
        result = 31 * result + (ongoingStopAtElapsedRealtimeMs?.hashCode() ?: 0)
        result = 31 * result + connection.hashCode()
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        return result
    }
}

internal enum class CachedConnection(val wireByte: Byte) {
    UNKNOWN(0x00),
    CONNECTED(0x01),
    DISCONNECTED(0x02),
    CONNECTED_BUT_SILENT(0x03),
    ;

    companion object {
        fun fromWire(value: Byte): CachedConnection? = entries.firstOrNull { it.wireByte == value }
    }
}

internal object CacheFraming {

    private val magic = byteArrayOf(0x57, 0x4b, 0x43, 0x48) // WKCH

    fun encode(record: CacheRecord): ByteArray {
        val payload = record.payload ?: ByteArray(0)
        val flags = (if (record.effectiveMutationWindowMs != null) FLAG_EFFECTIVE_WINDOW else 0) or
            (if (record.ongoingStopAtElapsedRealtimeMs != null) FLAG_ONGOING_DEADLINE else 0) or
            (if (record.isNoSessionTombstone) FLAG_NO_SESSION else 0)
        return ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(magic)
                data.writeShort(CACHE_SCHEMA_VERSION)
                data.writeByte(flags)
                data.writeByte(record.connection.wireByte.toInt())
                data.writeLong(record.receivedAtElapsedRealtimeMs)
                data.writeInt(record.bootCount)
                record.effectiveMutationWindowMs?.let(data::writeLong)
                record.ongoingStopAtElapsedRealtimeMs?.let(data::writeLong)
                data.writeInt(payload.size)
                data.write(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(payload))
                data.write(payload)
            }
            output.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): CacheRecord? = runCatching {
        if (bytes.size !in MIN_FRAME_BYTES..MAX_FRAME_BYTES) return null
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (!ByteArray(magic.size).also(input::get).contentEquals(magic)) return null
        if ((input.short.toInt() and UNSIGNED_SHORT_MASK) != CACHE_SCHEMA_VERSION) return null
        val flags = input.get().toInt() and UNSIGNED_BYTE_MASK
        if (flags and ALLOWED_FLAGS != flags) return null
        val connection = CachedConnection.fromWire(input.get()) ?: return null
        val receivedAt = input.long
        val bootCount = input.int
        val effectiveWindow = if (flags and FLAG_EFFECTIVE_WINDOW != 0) input.safeLong() else null
        val ongoingDeadline = if (flags and FLAG_ONGOING_DEADLINE != 0) input.safeLong() else null
        if (input.remaining() < PAYLOAD_LENGTH_BYTES + DIGEST_BYTES) return null
        val payloadLength = input.int
        if (payloadLength !in 0..WearProtocol.MAX_ENVELOPE_BYTES) return null
        if (input.remaining() != DIGEST_BYTES + payloadLength) return null
        val expectedDigest = ByteArray(DIGEST_BYTES).also(input::get)
        val payload = ByteArray(payloadLength).also(input::get)
        if (!MessageDigest.isEqual(
                expectedDigest,
                MessageDigest.getInstance(DIGEST_ALGORITHM).digest(payload),
            )
        ) {
            return null
        }
        val tombstone = flags and FLAG_NO_SESSION != 0
        if (tombstone != (payloadLength == 0)) return null
        CacheRecord(
            receivedAtElapsedRealtimeMs = receivedAt,
            bootCount = bootCount,
            effectiveMutationWindowMs = effectiveWindow,
            ongoingStopAtElapsedRealtimeMs = ongoingDeadline,
            connection = connection,
            payload = payload.takeUnless { tombstone },
        )
    }.getOrNull()

    private fun ByteBuffer.safeLong(): Long? = takeIf { remaining() >= Long.SIZE_BYTES }?.long

    private const val CACHE_SCHEMA_VERSION = 1
    private const val FLAG_EFFECTIVE_WINDOW = 0x01
    private const val FLAG_ONGOING_DEADLINE = 0x02
    private const val FLAG_NO_SESSION = 0x04
    private const val ALLOWED_FLAGS = FLAG_EFFECTIVE_WINDOW or FLAG_ONGOING_DEADLINE or FLAG_NO_SESSION
    private const val UNSIGNED_BYTE_MASK = 0xff
    private const val UNSIGNED_SHORT_MASK = 0xffff
    private const val PAYLOAD_LENGTH_BYTES = Int.SIZE_BYTES
    private const val DIGEST_BYTES = 32
    private const val DIGEST_ALGORITHM = "SHA-256"
    private const val MIN_FRAME_BYTES = 4 + Short.SIZE_BYTES + 1 + 1 + Long.SIZE_BYTES +
        Int.SIZE_BYTES + PAYLOAD_LENGTH_BYTES + DIGEST_BYTES
    private const val MAX_FRAME_BYTES = MIN_FRAME_BYTES + (2 * Long.SIZE_BYTES) +
        WearProtocol.MAX_ENVELOPE_BYTES
}

internal fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> contentEquals(other)
}
