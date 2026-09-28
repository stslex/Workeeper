// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchInteractionEligibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

internal class WatchTileCoordinator(
    snapshots: StateFlow<WatchRuntimeSnapshot>,
    scope: CoroutineScope,
    requestUpdate: () -> Unit,
    reportFailure: (Throwable) -> Unit,
) {
    init {
        scope.launch {
            snapshots.map(TileRefreshKey::from).distinctUntilChanged().collect {
                runCatching(requestUpdate).onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (failure !is Exception) throw failure
                    reportFailure(failure)
                }
            }
        }
    }
}

private data class TileRefreshKey(
    val display: WatchDisplayState,
    val noSession: Boolean,
    val recoveryRequired: Boolean,
    val readOnly: Boolean,
    val refreshRequired: Boolean,
    val retry: Boolean,
    val locale: Locale,
) {
    companion object {
        fun from(snapshot: WatchRuntimeSnapshot): TileRefreshKey = TileRefreshKey(
            display = snapshot.workout.display,
            noSession = snapshot.noSession,
            recoveryRequired = snapshot.recoveryRequired,
            readOnly = snapshot.readOnly,
            refreshRequired = snapshot.workout.refreshRequired,
            retry = WatchInteractionEligibility.from(snapshot.workout).retry,
            locale = snapshot.locale,
        )
    }
}
