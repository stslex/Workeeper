// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.cache

import io.github.stslex.workeeper.core.wear.protocol.OutlineSelection
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CacheFramingTest {

    @Test
    fun `round trips one complete payload clock and lifecycle record`() {
        val record = CacheRecord(
            receivedAtElapsedRealtimeMs = 1_000,
            bootCount = 12,
            effectiveMutationWindowMs = 90_000,
            ongoingStopAtElapsedRealtimeMs = 301_000,
            connection = CachedConnection.CONNECTED,
            payload = CacheTestFixtures.encodedSnapshot(),
        )
        assertEquals(record, CacheFraming.decode(CacheFraming.encode(record)))
    }

    @Test
    fun `round trips payload-free no-session tombstone`() {
        val record = CacheRecord(
            receivedAtElapsedRealtimeMs = 2_000,
            bootCount = 13,
            effectiveMutationWindowMs = null,
            ongoingStopAtElapsedRealtimeMs = null,
            connection = CachedConnection.DISCONNECTED,
            payload = null,
        )
        assertEquals(record, CacheFraming.decode(CacheFraming.encode(record)))
    }

    @Test
    fun `a version 2 frame round trips no selection, a selection with a position and one without`() {
        listOf(
            null,
            OutlineSelection(CacheTestFixtures.exerciseUuid, 3),
            OutlineSelection(CacheTestFixtures.exerciseUuid, null),
        ).forEach { selection ->
            val record = record(selection = selection)
            assertEquals(record, CacheFraming.decode(CacheFraming.encode(record)), "$selection")
        }
    }

    @Test
    fun `a version 1 frame is corrupt, even around a version 2 payload`() {
        val encoded = CacheFraming.encode(record(selection = null))
        assertEquals(2, encoded.versionField())

        assertNull(CacheFraming.decode(encoded.copyOf().also { it[VERSION_OFFSET + 1] = 1 }))
    }

    @Test
    fun `an unknown flag, a short selection or an invalid selection field is corrupt`() {
        val plain = CacheFraming.encode(record(selection = null))
        assertNull(CacheFraming.decode(plain.copyOf().also { it[FLAGS_OFFSET] = (it[FLAGS_OFFSET] + 0x10).toByte() }))

        val tombstone = CacheFraming.encode(
            record(selection = null).copy(payload = null, effectiveMutationWindowMs = null),
        )
        val shortSelection = tombstone.copyOf().also { it[FLAGS_OFFSET] = (it[FLAGS_OFFSET] + 0x08).toByte() }
        assertNull(CacheFraming.decode(shortSelection), "the selection field eats the length and digest")

        val selected = CacheFraming.encode(record(selection = OutlineSelection(CacheTestFixtures.exerciseUuid, 3)))
        val presence = SELECTION_OFFSET + UUID_BYTES
        assertNull(CacheFraming.decode(selected.copyOf().also { it[presence] = 2 }), "an unknown presence byte")
        assertNull(
            CacheFraming.decode(selected.copyOf().also { it[presence + 1] = 0x80.toByte() }),
            "a negative position",
        )
    }

    @Test
    fun `the largest frame decodes and one byte more is rejected`() {
        val largest = CacheRecord(
            receivedAtElapsedRealtimeMs = 1,
            bootCount = 1,
            effectiveMutationWindowMs = WearProtocol.MAX_MUTATION_WINDOW_MS,
            ongoingStopAtElapsedRealtimeMs = 2,
            connection = CachedConnection.CONNECTED,
            payload = ByteArray(WearProtocol.MAX_ENVELOPE_BYTES) { 0x7b },
            selection = OutlineSelection(CacheTestFixtures.exerciseUuid, Int.MAX_VALUE),
        )
        val encoded = CacheFraming.encode(largest)

        assertEquals(largest, CacheFraming.decode(encoded))
        assertNull(CacheFraming.decode(encoded + 0x00.toByte()))
    }

    @Test
    fun `rejects truncation trailing bytes digest mismatch and oversized payload metadata`() {
        val encoded = CacheFraming.encode(
            CacheRecord(
                receivedAtElapsedRealtimeMs = 1,
                bootCount = 1,
                effectiveMutationWindowMs = null,
                ongoingStopAtElapsedRealtimeMs = null,
                connection = CachedConnection.UNKNOWN,
                payload = CacheTestFixtures.encodedSnapshot(),
            ),
        )
        assertNull(CacheFraming.decode(encoded.copyOf(encoded.size - 1)))
        assertNull(CacheFraming.decode(encoded + 0x00.toByte()))
        assertNull(CacheFraming.decode(encoded.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }))

        val payloadLengthOffset = 4 + Short.SIZE_BYTES + 1 + 1 + Long.SIZE_BYTES + Int.SIZE_BYTES
        val invalidLength = encoded.copyOf().also { bytes ->
            val oversized = WearProtocol.MAX_ENVELOPE_BYTES + 1
            bytes[payloadLengthOffset] = (oversized ushr 24).toByte()
            bytes[payloadLengthOffset + 1] = (oversized ushr 16).toByte()
            bytes[payloadLengthOffset + 2] = (oversized ushr 8).toByte()
            bytes[payloadLengthOffset + 3] = oversized.toByte()
        }
        assertNull(CacheFraming.decode(invalidLength))
    }

    /** A snapshot record with a window and no ongoing deadline: the selection follows the window. */
    private fun record(selection: OutlineSelection?) = CacheRecord(
        receivedAtElapsedRealtimeMs = 1_000,
        bootCount = 12,
        effectiveMutationWindowMs = 90_000,
        ongoingStopAtElapsedRealtimeMs = null,
        connection = CachedConnection.CONNECTED,
        payload = CacheTestFixtures.encodedSnapshot(),
        selection = selection,
    )

    private fun ByteArray.versionField(): Int =
        ((this[VERSION_OFFSET].toInt() and 0xff) shl 8) or (this[VERSION_OFFSET + 1].toInt() and 0xff)

    private companion object {
        const val VERSION_OFFSET = 4
        const val FLAGS_OFFSET = VERSION_OFFSET + Short.SIZE_BYTES
        const val SELECTION_OFFSET = FLAGS_OFFSET + 1 + 1 + Long.SIZE_BYTES + Int.SIZE_BYTES + Long.SIZE_BYTES
        const val UUID_BYTES = 16
    }
}
