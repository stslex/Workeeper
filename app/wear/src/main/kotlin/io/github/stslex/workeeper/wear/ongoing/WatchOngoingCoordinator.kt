// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.wear.cache.CacheReadResult
import io.github.stslex.workeeper.wear.cache.CachedConnection
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.cache.WatchSnapshotCache
import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.SnapshotReduction
import io.github.stslex.workeeper.wear.state.TargetKey
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchReducerState
import io.github.stslex.workeeper.wear.state.WorkoutSourceVersion
import io.github.stslex.workeeper.wear.state.safeMonotonicAdd
import io.github.stslex.workeeper.wear.state.sourceVersion
import io.github.stslex.workeeper.wear.state.targetKeyOrNull

/**
 * Single serialized cache/lifecycle owner. Publish the reducer's candidate state only after
 * these synchronous operations succeed; cache or notification failures must prevent publication.
 */
internal class WatchOngoingCoordinator(
    private val cache: WatchSnapshotCache,
    private val clock: ElapsedRealtimeClock,
    private val notification: OngoingNotification,
    private val policy: OngoingPolicy,
) {
    private var lifecycle: Lifecycle? = null
    private var cachedSnapshotReceivedAtMs: Long? = null
    private var denied = false

    fun status(): OngoingStatus {
        val current = lifecycle ?: return if (denied && !notification.permissionGranted()) {
            OngoingStatus.PermissionDenied
        } else {
            OngoingStatus.Inactive
        }
        return when {
            !notification.permissionGranted() -> OngoingStatus.PermissionDenied
            clock.nowMs() >= current.stopAtMs -> OngoingStatus.Inactive
            else -> notification.existingDeadlineMs()
                ?.takeIf { clock.nowMs() < it }
                ?.let { OngoingStatus.Scheduled(minOf(current.stopAtMs, it)) }
                ?: OngoingStatus.Inactive
        }
    }

    fun acceptSnapshot(
        response: ActiveWorkoutSnapshotResponse,
        reduction: SnapshotReduction,
        reducerState: WatchReducerState,
        receivedAtElapsedRealtimeMs: Long,
        bootCount: Int,
    ): OngoingStatus {
        if (!reduction.accepted) return status()
        val effectiveWindow = eligibleWindow(response.snapshot, reduction, reducerState, receivedAtElapsedRealtimeMs)
        val deadline = effectiveWindow?.let { window ->
            safeMonotonicAdd(receivedAtElapsedRealtimeMs, window)
                ?.let { freshUntil -> safeMonotonicAdd(freshUntil, policy.reconnectWindowMs) }
        }
        denied = effectiveWindow != null && !notification.permissionGranted()
        val publishDeadline = prepareFreshDeadline(response.snapshot, deadline?.takeUnless { denied })
        if (publishDeadline == null) {
            notification.cancel()
            lifecycle = null
        }
        persist(response, receivedAtElapsedRealtimeMs, bootCount, effectiveWindow, publishDeadline)
        if (publishDeadline != null) {
            val next = Lifecycle(response.snapshot, receivedAtElapsedRealtimeMs, publishDeadline)
            lifecycle = next
            val remaining = publishDeadline - clock.nowMs()
            if (remaining <= 0L) {
                stop()
            } else {
                finishPost(
                    notification.post(OngoingNotificationRequest(next.snapshot, publishDeadline, remaining)),
                    connection = null,
                )
            }
        }
        return status()
    }

    /** Validated cache restores display only; it never posts or installs mutation authority. */
    fun restore(): CacheReadResult {
        val result = cache.read()
        val restored = result as? CacheReadResult.DisplayOnly
        cachedSnapshotReceivedAtMs = restored?.receivedAtElapsedRealtimeMs
        val cacheDeadline = restored?.ongoingStopAtElapsedRealtimeMs
        val systemDeadline = notification.existingDeadlineMs()
        val deadline = if (cacheDeadline != null && systemDeadline != null) {
            minOf(cacheDeadline, systemDeadline)
        } else {
            null
        }
        lifecycle = if (restored?.snapshot?.payload is SnapshotPayload.ActiveWithTarget &&
            deadline != null && clock.nowMs() < deadline
        ) {
            Lifecycle(restored.snapshot, restored.receivedAtElapsedRealtimeMs, deadline)
        } else {
            null
        }
        denied = lifecycle != null && !notification.permissionGranted()
        when {
            denied -> stop(permissionDenied = true)
            lifecycle == null -> stop()
            else -> cache.shortenOngoingDeadline(deadline)
        }
        return if (result is CacheReadResult.DisplayOnly) {
            result.copy(ongoingStopAtElapsedRealtimeMs = lifecycle?.stopAtMs)
        } else {
            result
        }
    }

    fun disconnected(disconnectedAtElapsedRealtimeMs: Long): OngoingStatus {
        require(disconnectedAtElapsedRealtimeMs <= clock.nowMs())
        val receivedAtMs = cachedSnapshotReceivedAtMs ?: return status()
        if (disconnectedAtElapsedRealtimeMs < receivedAtMs) return status()
        val current = lifecycle
        if (current == null) {
            cache.shortenOngoingDeadline(null, CachedConnection.DISCONNECTED)
            return status()
        }
        val disconnectDeadline = safeMonotonicAdd(disconnectedAtElapsedRealtimeMs, policy.reconnectWindowMs)
            ?: current.stopAtMs
        val shortened = current.copy(stopAtMs = minOf(current.stopAtMs, disconnectDeadline))
        // Keep the conservative deadline in memory even if durable publication then fails.
        lifecycle = shortened
        update(CachedConnection.DISCONNECTED)
        return status()
    }

    /** Event-driven refresh; the platform timeout also expires while this process is absent. */
    fun refresh(): OngoingStatus {
        update(connection = null)
        return status()
    }

    fun displayChanged(display: WatchDisplayState): OngoingStatus {
        if (display !is WatchDisplayState.Active) stop()
        return status()
    }

    private fun prepareFreshDeadline(snapshot: SnapshotData, deadline: Long?): Long? {
        if (deadline == null) return null
        val existing = notification.existingDeadlineMs() ?: return deadline
        if (deadline >= existing) return deadline
        val remaining = deadline - clock.nowMs()
        if (remaining <= 0L) return null
        // A shorter fresh grant must bound the old notification before its new cache record is published.
        val request = OngoingNotificationRequest(snapshot, deadline, remaining)
        return when (val result = notification.updateIfPresent(request)) {
            is OngoingPostResult.Posted -> {
                check(result.stopAtElapsedRealtimeMs <= deadline)
                result.stopAtElapsedRealtimeMs.takeIf { clock.nowMs() < it }
            }
            OngoingPostResult.PermissionDenied -> {
                denied = true
                null
            }
            OngoingPostResult.Missing -> null
        }
    }

    private fun update(connection: CachedConnection?) {
        val current = lifecycle ?: return
        val remaining = current.stopAtMs - clock.nowMs()
        when {
            remaining <= 0L -> stop(connection = connection)
            !notification.permissionGranted() -> stop(permissionDenied = true, connection = connection)
            else -> {
                val request = OngoingNotificationRequest(current.snapshot, current.stopAtMs, remaining)
                finishPost(notification.updateIfPresent(request), connection)
            }
        }
    }

    private fun finishPost(result: OngoingPostResult, connection: CachedConnection?) {
        when (result) {
            is OngoingPostResult.Posted -> {
                val current = requireNotNull(lifecycle)
                check(result.stopAtElapsedRealtimeMs <= current.stopAtMs)
                val confirmed = current.copy(stopAtMs = result.stopAtElapsedRealtimeMs)
                lifecycle = confirmed
                denied = false
                // Shorten the system timeout before publishing the matching cache header.
                cache.shortenOngoingDeadline(confirmed.stopAtMs, connection)
            }
            OngoingPostResult.PermissionDenied -> stop(permissionDenied = true, connection = connection)
            OngoingPostResult.Missing -> stop(connection = connection)
        }
    }

    private fun stop(permissionDenied: Boolean = false, connection: CachedConnection? = null) {
        notification.cancel()
        lifecycle = null
        denied = permissionDenied
        cache.shortenOngoingDeadline(stopAtElapsedRealtimeMs = null, connection = connection)
    }

    private fun eligibleWindow(
        snapshot: SnapshotData,
        reduction: SnapshotReduction,
        state: WatchReducerState,
        receivedAtMs: Long,
    ): Long? {
        val window = reduction.effectiveMutationWindowMs?.takeIf { it in 1L..WearProtocol.MAX_MUTATION_WINDOW_MS }
            ?: return null
        val freshUntil = safeMonotonicAdd(receivedAtMs, window) ?: return null
        if (!hasFreshAuthority(snapshot, state, freshUntil)) return null
        return window.takeIf { clock.nowMs() in receivedAtMs until freshUntil }
    }

    private fun hasFreshAuthority(snapshot: SnapshotData, state: WatchReducerState, freshUntil: Long): Boolean {
        val active = snapshot.payload as? SnapshotPayload.ActiveWithTarget ?: return false
        val grant = active.mutationAuthority as? MutationAuthority.Granted ?: return false
        val display = state.display as? WatchDisplayState.Active ?: return false
        if (display.snapshot != snapshot || display.freshness != ActiveFreshness.FRESH) return false
        val expected = LeaseSignature(
            grant.mutationLeaseId,
            grant.mutationLeaseGeneration,
            freshUntil,
            snapshot.sourceVersion(),
            snapshot.targetKeyOrNull(),
        )
        return state.authority.leaseSignature() == expected
    }

    private fun LocalMutationAuthority.leaseSignature(): LeaseSignature? = when (this) {
        is LocalMutationAuthority.Available ->
            LeaseSignature(leaseId, leaseGeneration, effectiveDeadlineMs, source, target)
        is LocalMutationAuthority.AttemptBound ->
            LeaseSignature(leaseId, leaseGeneration, effectiveDeadlineMs, source, target)
        LocalMutationAuthority.Retired -> null
    }

    private data class LeaseSignature(
        val leaseId: CanonicalUuid,
        val generation: Long,
        val deadlineMs: Long,
        val source: WorkoutSourceVersion,
        val target: TargetKey?,
    )

    private fun persist(
        response: ActiveWorkoutSnapshotResponse,
        receivedAtMs: Long,
        bootCount: Int,
        windowMs: Long?,
        deadlineMs: Long?,
    ) {
        if (response.snapshot.payload is SnapshotPayload.NoSession) {
            cache.replaceWithNoSessionTombstone(receivedAtMs, bootCount, CachedConnection.CONNECTED)
        } else {
            cache.replace(
                encodedSnapshot = WearProtocolCodec.encode(response),
                receivedAtElapsedRealtimeMs = receivedAtMs,
                bootCount = bootCount,
                effectiveMutationWindowMs = windowMs,
                ongoingStopAtElapsedRealtimeMs = deadlineMs,
                connection = CachedConnection.CONNECTED,
            )
        }
        cachedSnapshotReceivedAtMs = receivedAtMs
    }

    private data class Lifecycle(
        val snapshot: SnapshotData,
        val receivedAtMs: Long,
        val stopAtMs: Long,
    )
}
