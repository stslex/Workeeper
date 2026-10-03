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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The release boundary (wear-paired-transport.md §7.7, §10.1): the release runtime is the connected
 * owner-backed runtime, and no synthetic source or acceptance receiver is reachable. It builds here,
 * where Play services are absent: nothing reaches the link before a request origin
 * (`WatchTransportCoordinatorTest`), and `PlayServicesWearLink` creates its clients lazily, on its
 * first call (its GUARD; no test may name the Data Layer, wear-live-sync.md §8).
 * The Tile request below is an O2 origin, so it starts a request in the background; nothing here
 * asserts on the link.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class ReleaseRuntimeBoundaryTest {

    /**
     * wear-live-sync.md §10.1: the release factory's runtime hands the phone's change signal to the
     * coordinator (O6). Play services is absent here, so the handshake O6 starts cannot reach a phone
     * and the owner records that in its link status, which nothing else has touched yet: the factory's
     * runtime lives for the process, so this runs first, before the Tile test below starts an O2.
     */
    @Test
    @Order(1)
    fun releaseRuntimeForwardsThePhoneChangeSignal() {
        val runtime = WatchRuntimeFactory.get(RuntimeEnvironment.getApplication())
        assertEquals(LinkStatus.UNKNOWN, runtime.snapshot.value.link, "no origin has reached the link yet")

        runtime.onPhoneChanged()

        val deadline = System.nanoTime() + FORWARD_DEADLINE_NANOS
        while (runtime.snapshot.value.link == LinkStatus.UNKNOWN && System.nanoTime() < deadline) {
            Thread.sleep(FORWARD_POLL_MS)
        }
        assertNotEquals(LinkStatus.UNKNOWN, runtime.snapshot.value.link, "O6 started a handshake")
    }
    @Test
    fun releaseRejectsSyntheticEventsAndExcludesTheirSourceClass() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(WatchRuntimeFactory.handleDebugScenario(context, "active_boundary"))
        val runtime = WatchRuntimeFactory.get(context)
        assertInstanceOf(ConnectedWatchRuntime::class.java, runtime, "the release runtime is connected")
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
        assertEquals(0L, tile.freshnessIntervalMillis, "with an empty cache the release runtime has no boundary")
    }

    /** `TileService.onTileRequest` is protected; the test calls it the way the platform binder would. */
    private fun WorkoutTileService.requestTile(request: RequestBuilders.TileRequest): TileBuilders.Tile {
        val method = WorkoutTileService::class.java
            .getDeclaredMethod("onTileRequest", RequestBuilders.TileRequest::class.java)
            .apply { isAccessible = true }
        return (method.invoke(this, request) as ListenableFuture<*>).get(5, TimeUnit.SECONDS) as TileBuilders.Tile
    }

    private companion object {
        /** The coordinator's own request deadline is 10 s; a lookup that fails fails sooner. */
        const val FORWARD_DEADLINE_NANOS = 20_000_000_000L
        const val FORWARD_POLL_MS = 20L
    }
}
