// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import android.content.Context
import androidx.wear.tiles.TileService
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.wear.BuildConfig
import io.github.stslex.workeeper.wear.WearApplication
import io.github.stslex.workeeper.wear.runtime.WatchRuntime
import kotlinx.coroutines.Dispatchers

internal fun observeWorkoutTile(context: Context, runtime: WatchRuntime) {
    val application = context.applicationContext as WearApplication
    WatchTileCoordinator(
        snapshots = runtime.snapshot,
        scope = application.appScopeLifetime.childScope(Dispatchers.Main.immediate),
        refreshKey = WorkoutTileRenderer(application)::refreshKey,
        requestUpdate = {
            TileService.getUpdater(application).requestUpdate(WorkoutTileService::class.java)
            if (BuildConfig.DEBUG) Log.tag(TILE_LOG_TAG).d("requestUpdate")
        },
        reportFailure = { Log.tag(TILE_LOG_TAG).w("Tile update request failed", it) },
    )
}

internal const val TILE_LOG_TAG = "WorkeeperTile"
