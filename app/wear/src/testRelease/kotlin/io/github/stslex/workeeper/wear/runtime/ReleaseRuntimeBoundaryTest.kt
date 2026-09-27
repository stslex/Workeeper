// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.wear.ui.currentSurface
import io.github.stslex.workeeper.wear.ui.ongoingStatus
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.common.util.concurrent.ListenableFuture
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.tile.WorkoutTileService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Locale
import java.util.concurrent.TimeUnit

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class ReleaseRuntimeBoundaryTest {
    @Test
    fun releaseRejectsSyntheticEventsAndExcludesTheirSourceClass() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(WatchRuntimeFactory.handleDebugScenario(context, "active_boundary"))
        val runtime = WatchRuntimeFactory.get(context)
        assertFalse(runtime.currentSurface().completeEnabled)
        assertEquals(WatchActionResult.Rejected, runtime.onAction(ControllerAction.CompleteSet))
        assertEquals(OngoingStatus.Inactive, runtime.ongoingStatus.value)
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("io.github.stslex.workeeper.wear.runtime.DebugSnapshotDriver")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("io.github.stslex.workeeper.wear.runtime.AcceptanceScenarioReceiver")
        }
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            context.packageManager.getReceiverInfo(
                ComponentName(
                    context.packageName,
                    "io.github.stslex.workeeper.wear.runtime.AcceptanceScenarioReceiver",
                ),
                0,
            )
        }
    }

    @Test
    fun releaseTileDeclaresNoFreshnessInterval() {
        val context = RuntimeEnvironment.getApplication()
        val frame = WatchRuntimeFactory.get(context).tileFrame(Locale.US)
        assertEquals(0L, frame.freshnessIntervalMs, "the release runtime reports no boundary")
        val service = Robolectric.setupService(WorkoutTileService::class.java)
        val tile = service.requestTile(RequestBuilders.TileRequest.Builder().build())
        assertEquals(0L, tile.freshnessIntervalMillis, "the read-only release runtime has no boundary")
    }

    /** `TileService.onTileRequest` is protected; the test calls it the way the platform binder would. */
    private fun WorkoutTileService.requestTile(request: RequestBuilders.TileRequest): TileBuilders.Tile {
        val method = WorkoutTileService::class.java
            .getDeclaredMethod("onTileRequest", RequestBuilders.TileRequest::class.java)
            .apply { isAccessible = true }
        return (method.invoke(this, request) as ListenableFuture<*>).get(5, TimeUnit.SECONDS) as TileBuilders.Tile
    }
}
