// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.stslex.workeeper.wear.state.WatchProcessState

/** Cache-first glance surface. Privacy-gated transport wiring is intentionally absent. */
class WorkoutTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = Futures.immediateFuture(
        WorkoutTileRenderer(this).render(WatchProcessState.currentSurface()),
    )
}
