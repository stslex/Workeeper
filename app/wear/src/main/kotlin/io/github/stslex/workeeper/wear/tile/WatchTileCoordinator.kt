// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Requests a system Tile update whenever the rendered Tile would change.
 *
 * GUARD: [refreshKey] is the rendered content ([WorkoutTileRenderer.refreshKey]), never a
 * projection of reducer fields. A projection has to list every field that can move a rendered
 * line, authority included, and a missed field is a Tile that keeps showing fresh progress.
 */
internal class WatchTileCoordinator(
    snapshots: StateFlow<WatchRuntimeSnapshot>,
    scope: CoroutineScope,
    refreshKey: (WatchRuntimeSnapshot) -> TileRefreshKey,
    requestUpdate: () -> Unit,
    reportFailure: (Throwable) -> Unit,
) {
    init {
        scope.launch {
            snapshots.map(refreshKey).distinctUntilChanged().collect {
                runCatching(requestUpdate).onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (failure !is Exception) throw failure
                    reportFailure(failure)
                }
            }
        }
    }
}

/** What the Tile renders: its text lines and the locale they were formatted for. */
internal data class TileRefreshKey(val lines: List<String>, val locale: Locale)
