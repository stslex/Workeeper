// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.cache

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
}
