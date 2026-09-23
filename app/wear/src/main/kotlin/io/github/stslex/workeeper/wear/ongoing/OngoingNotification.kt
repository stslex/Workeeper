// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import io.github.stslex.workeeper.core.wear.protocol.SnapshotData

internal interface OngoingNotification {
    fun permissionGranted(): Boolean

    /** Read the absolute monotonic deadline stored with the existing system notification. */
    fun existingDeadlineMs(): Long?

    /** Post with a system-owned timeout and return only after the platform accepts it. */
    fun post(request: OngoingNotificationRequest): OngoingPostResult

    /** Never create a missing notification or extend its existing absolute deadline. */
    fun updateIfPresent(request: OngoingNotificationRequest): OngoingPostResult

    fun cancel()
}

internal data class OngoingNotificationRequest(
    val snapshot: SnapshotData,
    val stopAtElapsedRealtimeMs: Long,
    val timeoutAfterMs: Long,
)

internal sealed interface OngoingPostResult {
    data class Posted(val stopAtElapsedRealtimeMs: Long) : OngoingPostResult
    data object PermissionDenied : OngoingPostResult
    data object Missing : OngoingPostResult
}

/** No production default: the reconnect window requires the physical-watch probe in Phase 1 §8. */
internal data class OngoingPolicy(val reconnectWindowMs: Long) {
    init {
        require(reconnectWindowMs > 0L)
    }
}

internal sealed interface OngoingStatus {
    data object Inactive : OngoingStatus
    data object PermissionDenied : OngoingStatus
    data class Scheduled(val stopAtElapsedRealtimeMs: Long) : OngoingStatus
}
