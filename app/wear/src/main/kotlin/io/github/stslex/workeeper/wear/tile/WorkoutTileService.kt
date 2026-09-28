// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeFactory
import io.github.stslex.workeeper.wear.runtime.runWearRuntimeUiEvent

/** Cache-first glance surface. Privacy-gated transport wiring is intentionally absent. */
class WorkoutTileService : TileService() {

    /**
     * Rendering is synchronous, so the future is already complete when it is returned.
     * `CallbackToFutureAdapter` rather than Guava's `Futures.immediateFuture`: only the
     * interface-only `listenablefuture` stub is on the release classpath, and reaching Guava
     * proper through the debug-only tiles-renderer is what kept this file from compiling in a
     * release variant at all.
     */
    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = CallbackToFutureAdapter.getFuture { completer ->
        val runtime = WatchRuntimeFactory.get(applicationContext)
        runWearRuntimeUiEvent {
            runtime.setLocale(resources.configuration.locales[0])
            runtime.onWake()
        }
        completer.set(WorkoutTileRenderer(this).render(runtime.surface.value))
        FUTURE_TAG
    }

    private companion object {
        const val FUTURE_TAG = "WorkoutTileService#onTileRequest"
    }
}
