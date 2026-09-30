// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.cache

import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchSnapshotCacheTest {

    @Test
    fun `same-boot process restart exposes canonical snapshot as display-only`() {
        val storage = FakeAtomicStorage()
        cache(storage = storage, now = 10_000, bootCount = 4).replace(
            encodedSnapshot = CacheTestFixtures.encodedSnapshot(),
            receivedAtElapsedRealtimeMs = 10_000,
            bootCount = 4,
            effectiveMutationWindowMs = WearProtocol.MAX_MUTATION_WINDOW_MS,
            ongoingStopAtElapsedRealtimeMs = 400_000,
            connection = CachedConnection.CONNECTED,
        )

        val restarted = cache(storage = storage, now = 10_001, bootCount = 4)
        val result = assertIs<CacheReadResult.DisplayOnly>(restarted.read())
        val active = assertIs<SnapshotPayload.ActiveWithTarget>(result.snapshot.payload)
        assertIs<MutationAuthority.Unavailable>(active.mutationAuthority)
        assertEquals(WearProtocol.MAX_MUTATION_WINDOW_MS, result.persistedEffectiveMutationWindowMs)
        assertEquals(false, result.mutationWindowElapsed)
    }

    @Test
    fun `TTL boundary deletes before protocol decode and deletion failure never exposes payload`() {
        val storage = FakeAtomicStorage()
        cache(storage = storage, now = 0, bootCount = 2).replace(
            encodedSnapshot = CacheTestFixtures.encodedSnapshot(),
            receivedAtElapsedRealtimeMs = 0,
            bootCount = 2,
            effectiveMutationWindowMs = null,
            ongoingStopAtElapsedRealtimeMs = null,
            connection = CachedConnection.UNKNOWN,
        )
        var decodeCalls = 0
        storage.failDelete = true
        val result = cache(
            storage = storage,
            now = WearProtocol.DISPLAY_CACHE_TTL_MS,
            bootCount = 2,
            decoder = CachedSnapshotDecoder {
                decodeCalls += 1
                CacheTestFixtures.snapshot()
            },
        ).read()

        assertEquals(CacheReadResult.Absent(CacheAbsentReason.EXPIRED), result)
        assertEquals(0, decodeCalls)
        assertEquals(1, storage.deleteCalls)
        assertTrue(storage.bytes != null)
    }

    @Test
    fun `one millisecond before TTL remains readable but mutation stays unavailable`() {
        val storage = FakeAtomicStorage()
        cache(storage = storage, now = 0, bootCount = 2).replace(
            encodedSnapshot = CacheTestFixtures.encodedSnapshot(),
            receivedAtElapsedRealtimeMs = 0,
            bootCount = 2,
            effectiveMutationWindowMs = 1,
            ongoingStopAtElapsedRealtimeMs = null,
            connection = CachedConnection.CONNECTED_BUT_SILENT,
        )
        val result = assertIs<CacheReadResult.DisplayOnly>(
            cache(
                storage = storage,
                now = WearProtocol.DISPLAY_CACHE_TTL_MS - 1,
                bootCount = 2,
            ).read(),
        )
        assertTrue(result.mutationWindowElapsed)
        assertIs<MutationAuthority.Unavailable>(
            assertIs<SnapshotPayload.ActiveWithTarget>(result.snapshot.payload).mutationAuthority,
        )
    }

    @Test
    fun `boot mismatch missing boot and impossible elapsed baseline skip payload decoding`() {
        listOf(
            Triple<Int?, Long, CacheAbsentReason>(4, 10_001L, CacheAbsentReason.BOOT_MISMATCH),
            Triple(null, 10_001L, CacheAbsentReason.BOOT_MISMATCH),
            Triple(3, 9_999L, CacheAbsentReason.IMPOSSIBLE_MONOTONIC_BASELINE),
        ).forEach { (boot, now, reason) ->
            val storage = storageWithRecord(receivedAt = 10_000, bootCount = 3)
            var decodeCalls = 0
            val result = cache(
                storage = storage,
                now = now,
                bootCount = boot,
                decoder = CachedSnapshotDecoder {
                    decodeCalls += 1
                    CacheTestFixtures.snapshot()
                },
            ).read()
            assertEquals(CacheReadResult.Absent(reason), result)
            assertEquals(0, decodeCalls)
            assertEquals(1, storage.deleteCalls)
        }
    }

    @Test
    fun `corrupt digest is deleted before protocol decode`() {
        val storage = storageWithRecord(receivedAt = 1_000, bootCount = 1)
        storage.bytes = requireNotNull(storage.bytes).also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        }
        var decodeCalls = 0
        val result = cache(
            storage = storage,
            now = 1_001,
            bootCount = 1,
            decoder = CachedSnapshotDecoder {
                decodeCalls += 1
                CacheTestFixtures.snapshot()
            },
        ).read()
        assertEquals(CacheReadResult.Absent(CacheAbsentReason.CORRUPT), result)
        assertEquals(0, decodeCalls)
        assertEquals(1, storage.deleteCalls)
    }

    @Test
    fun `no-session tombstone replaces active bytes before publication`() {
        val storage = FakeAtomicStorage()
        val cache = cache(storage = storage, now = 2_000, bootCount = 1)
        cache.replace(
            encodedSnapshot = CacheTestFixtures.encodedSnapshot(),
            receivedAtElapsedRealtimeMs = 2_000,
            bootCount = 1,
            effectiveMutationWindowMs = 100,
            ongoingStopAtElapsedRealtimeMs = 2_500,
            connection = CachedConnection.CONNECTED,
        )
        cache.replaceWithNoSessionTombstone(
            receivedAtElapsedRealtimeMs = 2_001,
            bootCount = 1,
            connection = CachedConnection.CONNECTED,
        )

        assertEquals(
            CacheReadResult.NoSession,
            cache(storage = storage, now = 2_002, bootCount = 1).read(),
        )
        assertNull(CacheFraming.decode(requireNotNull(storage.bytes))?.payload)
    }

    @Test
    fun `ongoing expiry cancels before decode and a future deadline does not recreate it`() {
        val storage = FakeAtomicStorage()
        cache(storage = storage, now = 1_000, bootCount = 1).replace(
            encodedSnapshot = CacheTestFixtures.encodedSnapshot(),
            receivedAtElapsedRealtimeMs = 1_000,
            bootCount = 1,
            effectiveMutationWindowMs = 100,
            ongoingStopAtElapsedRealtimeMs = 1_200,
            connection = CachedConnection.CONNECTED,
        )
        var sequence = ""
        val expired = cache(
            storage = storage,
            now = 1_200,
            bootCount = 1,
            decoder = CachedSnapshotDecoder {
                sequence += "decode"
                CacheTestFixtures.snapshot()
            },
            onExpired = {
                sequence += "cancel-"
            },
        )
        assertIs<CacheReadResult.DisplayOnly>(expired.read())
        assertEquals("cancel-decode", sequence)

        sequence = ""
        val beforeDeadline = cache(
            storage = storage,
            now = 1_199,
            bootCount = 1,
            decoder = CachedSnapshotDecoder {
                sequence += "decode"
                CacheTestFixtures.snapshot()
            },
            onExpired = { sequence += "cancel-" },
        )
        assertIs<CacheReadResult.DisplayOnly>(beforeDeadline.read())
        assertEquals("decode", sequence)
    }

    @Test
    fun `all atomic replacement crash cuts expose complete old or complete new record`() {
        CrashCut.entries.forEach { cut ->
            val storage = FakeAtomicStorage()
            val cache = cache(storage = storage, now = 1_000, bootCount = 1)
            cache.replace(
                encodedSnapshot = CacheTestFixtures.encodedSnapshot(revision = 5),
                receivedAtElapsedRealtimeMs = 1_000,
                bootCount = 1,
                effectiveMutationWindowMs = null,
                ongoingStopAtElapsedRealtimeMs = null,
                connection = CachedConnection.CONNECTED,
            )
            val oldPublished = cache.publishedForTest()
            storage.crashCut = cut
            assertFailsWith<IOException> {
                cache.replace(
                    encodedSnapshot = CacheTestFixtures.encodedSnapshot(revision = 6),
                    receivedAtElapsedRealtimeMs = 1_001,
                    bootCount = 1,
                    effectiveMutationWindowMs = null,
                    ongoingStopAtElapsedRealtimeMs = null,
                    connection = CachedConnection.CONNECTED,
                )
            }
            assertEquals(oldPublished, cache.publishedForTest())

            storage.crashCut = null
            val diskResult = assertIs<CacheReadResult.DisplayOnly>(
                cache(storage = storage, now = 1_002, bootCount = 1).read(),
            )
            val active = assertIs<SnapshotPayload.ActiveWithTarget>(diskResult.snapshot.payload)
            val expectedRevision = if (cut == CrashCut.AFTER_ATOMIC_PUBLISH) 6L else 5L
            assertEquals(expectedRevision, active.sessionRevision)
        }
    }

    private fun storageWithRecord(receivedAt: Long, bootCount: Int): FakeAtomicStorage =
        FakeAtomicStorage().also { storage ->
            cache(storage = storage, now = receivedAt, bootCount = bootCount).replace(
                encodedSnapshot = CacheTestFixtures.encodedSnapshot(),
                receivedAtElapsedRealtimeMs = receivedAt,
                bootCount = bootCount,
                effectiveMutationWindowMs = null,
                ongoingStopAtElapsedRealtimeMs = null,
                connection = CachedConnection.UNKNOWN,
            )
        }

    private fun cache(
        storage: FakeAtomicStorage,
        now: Long,
        bootCount: Int?,
        decoder: CachedSnapshotDecoder? = null,
        onExpired: () -> Unit = {},
    ): WatchSnapshotCache {
        val clock = ElapsedRealtimeClock { now }
        val bootProvider = BootCountProvider { bootCount }
        val expiryHandler = OngoingExpiryHandler(onExpired)
        return if (decoder == null) {
            WatchSnapshotCache(
                storage = storage,
                clock = clock,
                bootCountProvider = bootProvider,
                ongoingExpiryHandler = expiryHandler,
            )
        } else {
            WatchSnapshotCache(
                storage = storage,
                clock = clock,
                bootCountProvider = bootProvider,
                ongoingExpiryHandler = expiryHandler,
                decoder = decoder,
            )
        }
    }
}
