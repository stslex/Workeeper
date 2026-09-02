// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.cache

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchDecodeResult
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import java.io.IOException

internal fun interface ElapsedRealtimeClock {
    fun nowMs(): Long
}

internal fun interface BootCountProvider {
    fun currentBootCount(): Int?
}

internal fun interface OngoingExpiryHandler {
    fun cancelExpiredOngoingSurface()
}

internal fun interface CachedSnapshotDecoder {
    fun decode(bytes: ByteArray): SnapshotData?
}

internal sealed interface CacheReadResult {
    data class Absent(val reason: CacheAbsentReason) : CacheReadResult
    data object NoSession : CacheReadResult
    data class DisplayOnly(
        val snapshot: SnapshotData,
        val connection: CachedConnection,
        val receivedAtElapsedRealtimeMs: Long,
        val persistedEffectiveMutationWindowMs: Long?,
        val ongoingStopAtElapsedRealtimeMs: Long?,
        val mutationWindowElapsed: Boolean,
    ) : CacheReadResult
}

internal enum class CacheAbsentReason {
    EMPTY,
    IO_FAILURE,
    CORRUPT,
    EXPIRED,
    BOOT_MISMATCH,
    IMPOSSIBLE_MONOTONIC_BASELINE,
}

internal class WatchSnapshotCache(
    private val storage: AtomicRecordStorage,
    private val clock: ElapsedRealtimeClock,
    private val bootCountProvider: BootCountProvider,
    private val ongoingExpiryHandler: OngoingExpiryHandler,
    private val decoder: CachedSnapshotDecoder = CachedSnapshotDecoder { bytes ->
        val result = WearProtocolCodec.decodeForWatch(bytes)
        (result as? WatchDecodeResult.Success)
            ?.envelope
            ?.let { it as? ActiveWorkoutSnapshotResponse }
            ?.snapshot
    },
) {

    private var publishedRecord: CacheRecord? = null

    fun replace(
        encodedSnapshot: ByteArray,
        receivedAtElapsedRealtimeMs: Long,
        bootCount: Int,
        effectiveMutationWindowMs: Long?,
        ongoingStopAtElapsedRealtimeMs: Long?,
        connection: CachedConnection,
    ) {
        publish(
            CacheRecord(
                receivedAtElapsedRealtimeMs = receivedAtElapsedRealtimeMs,
                bootCount = bootCount,
                effectiveMutationWindowMs = effectiveMutationWindowMs,
                ongoingStopAtElapsedRealtimeMs = ongoingStopAtElapsedRealtimeMs,
                connection = connection,
                payload = encodedSnapshot.copyOf(),
            ),
        )
    }

    fun replaceWithNoSessionTombstone(
        receivedAtElapsedRealtimeMs: Long,
        bootCount: Int,
        connection: CachedConnection,
    ) {
        publish(
            CacheRecord(
                receivedAtElapsedRealtimeMs = receivedAtElapsedRealtimeMs,
                bootCount = bootCount,
                effectiveMutationWindowMs = null,
                ongoingStopAtElapsedRealtimeMs = null,
                connection = connection,
                payload = null,
            ),
        )
    }

    fun read(): CacheReadResult {
        val raw = try {
            storage.read()
        } catch (_: IOException) {
            return CacheReadResult.Absent(CacheAbsentReason.IO_FAILURE)
        } ?: return CacheReadResult.Absent(CacheAbsentReason.EMPTY)
        val record = CacheFraming.decode(raw)
            ?: return invalidate(CacheAbsentReason.CORRUPT)
        val currentBootCount = bootCountProvider.currentBootCount()
            ?: return invalidate(CacheAbsentReason.BOOT_MISMATCH)
        if (record.bootCount != currentBootCount) {
            return invalidate(CacheAbsentReason.BOOT_MISMATCH)
        }
        val now = clock.nowMs()
        if (now < record.receivedAtElapsedRealtimeMs) {
            return invalidate(CacheAbsentReason.IMPOSSIBLE_MONOTONIC_BASELINE)
        }
        if (now - record.receivedAtElapsedRealtimeMs >= WearProtocol.DISPLAY_CACHE_TTL_MS) {
            return invalidate(CacheAbsentReason.EXPIRED)
        }
        if (record.ongoingStopAtElapsedRealtimeMs?.let { now >= it } == true) {
            ongoingExpiryHandler.cancelExpiredOngoingSurface()
        }
        if (record.isNoSessionTombstone) {
            publishedRecord = record
            return CacheReadResult.NoSession
        }
        val snapshot = decoder.decode(requireNotNull(record.payload))
            ?: return invalidate(CacheAbsentReason.CORRUPT)
        publishedRecord = record
        val mutationWindowElapsed = record.effectiveMutationWindowMs
            ?.let { window -> now - record.receivedAtElapsedRealtimeMs >= window }
            ?: true
        return CacheReadResult.DisplayOnly(
            snapshot = snapshot.withoutMutationAuthority(),
            connection = record.connection,
            receivedAtElapsedRealtimeMs = record.receivedAtElapsedRealtimeMs,
            persistedEffectiveMutationWindowMs = record.effectiveMutationWindowMs,
            ongoingStopAtElapsedRealtimeMs = record.ongoingStopAtElapsedRealtimeMs,
            mutationWindowElapsed = mutationWindowElapsed,
        )
    }

    private fun SnapshotData.withoutMutationAuthority(): SnapshotData {
        val active = payload as? SnapshotPayload.ActiveWithTarget ?: return this
        return copy(
            payload = active.copy(
                mutationAuthority = MutationAuthority.Unavailable(
                    MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED,
                ),
            ),
        )
    }

    internal fun publishedForTest(): CacheRecord? = publishedRecord

    private fun publish(record: CacheRecord) {
        val encoded = CacheFraming.encode(record)
        storage.replace(encoded)
        publishedRecord = record
    }

    private fun invalidate(reason: CacheAbsentReason): CacheReadResult.Absent {
        runCatching(storage::delete)
        publishedRecord = null
        return CacheReadResult.Absent(reason)
    }
}
