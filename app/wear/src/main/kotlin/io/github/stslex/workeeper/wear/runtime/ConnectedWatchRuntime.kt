// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.wear.transport.WatchTransportCoordinator
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * The release runtime (wear-paired-transport.md §7.1): the owner keeps every reducer, cache and
 * ongoing rule; this only hands the owner's issued requests and the origin triggers to the
 * transport. Debug variants never construct it, so no synthetic source reaches a transport.
 */
internal class ConnectedWatchRuntime(
    private val owner: WatchRuntimeOwner,
    private val transport: WatchTransportCoordinator,
) : WatchRuntime {

    override val snapshot: StateFlow<WatchRuntimeSnapshot> = owner.snapshot

    override fun onWake(): WatchRuntimeSnapshot = owner.onWake()

    override fun setLocale(locale: Locale) = owner.setLocale(locale)

    override fun onAction(action: ControllerAction): WatchActionResult = owner.onAction(action).also { result ->
        when (result) {
            is WatchActionResult.CommandIssued -> transport.submitCommand(result.token, result.fingerprint)
            WatchActionResult.RefreshRequested -> transport.requestUserRefresh()
            WatchActionResult.Updated, WatchActionResult.Rejected -> Unit
        }
    }

    /** The frame renders first; the O2 trigger only posts a possible refresh (§7.4). */
    override fun tileFrame(locale: Locale): WatchTileFrame = owner.tileFrame(locale).also {
        transport.onTileRendered()
    }

    override fun onControllerInteractive(interactive: Boolean) = transport.setInteractive(interactive)

    /** O6 (wear-live-sync.md §7.2): the coordinator decides when it is asked for. */
    override fun onPhoneChanged() = transport.onPhoneChanged()
}
