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
}

internal sealed interface WatchActionResult {
    data object Updated : WatchActionResult
    data object Rejected : WatchActionResult
    data object RefreshRequested : WatchActionResult
    data class CommandIssued(val token: RequestToken, val fingerprint: FingerprintCommand) : WatchActionResult
}

internal fun interface RuntimeIdSource {
    fun nextId(): CanonicalUuid
}

internal data class RuntimeIdentity(val sourceNodeId: String, val ids: RuntimeIdSource)

/** Replace one absolute monotonic deadline; an implementation must never poll or hold a wake lock. */
internal fun interface RuntimeDeadlineScheduler {
    fun replace(deadlineElapsedRealtimeMs: Long?, callback: () -> Unit)
}
