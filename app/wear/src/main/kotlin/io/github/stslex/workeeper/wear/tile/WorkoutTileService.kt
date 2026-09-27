// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.wear.BuildConfig
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeFactory
import io.github.stslex.workeeper.wear.runtime.runWearRuntimeUiEvent
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper

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
        if (BuildConfig.DEBUG) Log.tag(TILE_LOG_TAG).d("onTileRequest")
        val runtime = WatchRuntimeFactory.get(applicationContext)
        runWearRuntimeUiEvent {
            runtime.setLocale(resources.configuration.locales[0])
            runtime.onWake()
        }
        val device = requestParams.deviceConfiguration
        val screenDiameter = minOf(device.screenWidthDp, device.screenHeightDp).takeIf { it > 0 }
            ?: minOf(resources.configuration.screenWidthDp, resources.configuration.screenHeightDp)
        // The owner's next authority/ongoing/cache boundary is when the rendered content can change.
        val freshness = runWearRuntimeUiEvent { runtime.remainingUntilNextBoundaryMs() }.getOrNull() ?: 0L
        completer.set(
            WorkoutTileRenderer(this).render(WearSurfaceMapper.map(runtime.snapshot.value), screenDiameter, freshness),
        )
        FUTURE_TAG
    }

    private companion object {
        const val FUTURE_TAG = "WorkoutTileService#onTileRequest"
    }
}
