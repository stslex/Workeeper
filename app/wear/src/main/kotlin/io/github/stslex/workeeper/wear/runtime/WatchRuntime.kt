// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.wear.state.RequestToken
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

internal interface WatchRuntime {
    val snapshot: StateFlow<WatchRuntimeSnapshot>

    fun onWake(): WatchRuntimeSnapshot
    fun onAction(action: ControllerAction): WatchActionResult
    fun setLocale(locale: Locale)

    /**
     * Wakes the runtime for one Tile request in [locale] and returns, from a single serialized
     * operation with expiry checked inside it, the snapshot to render and its freshness interval.
     * The Tile renders exactly [WatchTileFrame.snapshot]: a separate [snapshot] read could pair a
     * newer snapshot with an older interval.
     */
    fun tileFrame(locale: Locale): WatchTileFrame

    /**
     * The controller became interactive (resumed and not ambient) or stopped being so. Only the
     * connected release runtime acts on it (wear-paired-transport.md §7.4 O1); `onWake` is not
     * an interaction signal.
     */
    fun onControllerInteractive(interactive: Boolean) = Unit

    /**
     * The phone signalled that its active workout may have changed (wear-live-sync.md §7.1, O6).
     * Only the connected release runtime acts on it; debug runtimes keep this no-op, so no synthetic
     * suite reacts to a signal.
     */
    fun onPhoneChanged() = Unit
}

/**
 * [freshnessIntervalMs] is elapsed time until the owner's next authority, ongoing or display-cache
 * boundary; see [tileFreshnessIntervalMs] for the values that are not a plain remainder.
 */
internal data class WatchTileFrame(val snapshot: WatchRuntimeSnapshot, val freshnessIntervalMs: Long)

/**
 * The Tile freshness interval at [nowMs]. 0 tells the platform never to refresh on its own, so it is
 * returned when there is no boundary and in no other case. A boundary that has already passed asks
 * for [OVERDUE_BOUNDARY_FRESHNESS_MS], a refresh as soon as the platform allows; that request's
 * expiry check moves past the boundary, so the floor cannot repeat. A pending recovery asks for
 * [RECOVERY_RETRY_FRESHNESS_MS] so the next Tile request retries it.
 */
internal fun tileFreshnessIntervalMs(nextBoundaryMs: Long?, nowMs: Long, recoveryRequired: Boolean): Long = when {
    recoveryRequired -> RECOVERY_RETRY_FRESHNESS_MS
    nextBoundaryMs == null -> 0L
    else -> (nextBoundaryMs - nowMs).coerceAtLeast(OVERDUE_BOUNDARY_FRESHNESS_MS)
}

/** The smallest positive interval: 0 is reserved for "no boundary". */
internal const val OVERDUE_BOUNDARY_FRESHNESS_MS: Long = 1L

/**
 * One minute, the platform's refresh throttle (wear-style-mvi.md, System Tile). Asking sooner buys
 * nothing where the platform throttles; where it does not, a storage failure that persists costs at
 * most one retried cache read per minute instead of a refresh loop.
 */
internal const val RECOVERY_RETRY_FRESHNESS_MS: Long = 60_000L

internal sealed interface WatchActionResult {
    data object Updated : WatchActionResult
    data object Rejected : WatchActionResult
    data object RefreshRequested : WatchActionResult
    data class CommandIssued(val token: RequestToken, val fingerprint: FingerprintCommand) : WatchActionResult
}

internal fun interface RuntimeIdSource {
    fun nextId(): CanonicalUuid
}

/**
 * [sourceNodeId] feeds only the local command fingerprint and never travels (Phase 1 F9). In release
 * it is the watch's own Data Layer node id, resolved by the first request and null until then
 * (wear-paired-transport.md §7.6).
 */
internal class RuntimeIdentity(private val nodeId: () -> String?, val ids: RuntimeIdSource) {

    constructor(sourceNodeId: String, ids: RuntimeIdSource) : this({ sourceNodeId }, ids)

    val sourceNodeId: String? get() = nodeId()
}

/** Replace one absolute monotonic deadline; an implementation must never poll or hold a wake lock. */
internal fun interface RuntimeDeadlineScheduler {
    fun replace(deadlineElapsedRealtimeMs: Long?, callback: () -> Unit)
}
