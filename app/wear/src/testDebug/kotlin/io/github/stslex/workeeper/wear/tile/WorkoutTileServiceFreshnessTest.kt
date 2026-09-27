// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import android.Manifest
import android.app.NotificationManager
import android.provider.Settings
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.common.util.concurrent.ListenableFuture
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.runtime.DEBUG_UNCALIBRATED_RECONNECT_WINDOW_MS
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeFactory
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.TimeUnit

/** The real service on the debug runtime: freshness follows the owner boundary, expiry renders refresh required. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class WorkoutTileServiceFreshnessTest {
    private val application get() = RuntimeEnvironment.getApplication()

    @BeforeEach
    fun seed() {
        Settings.Global.putInt(application.contentResolver, Settings.Global.BOOT_COUNT, 1)
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = requireNotNull(application.getSystemService(NotificationManager::class.java))
        shadowOf(manager).setNotificationsEnabled(true)
        assertTrue(WatchRuntimeFactory.handleDebugScenario(application, "no_session"))
    }

    @AfterEach
    fun reset() {
        assertTrue(WatchRuntimeFactory.handleDebugScenario(application, "no_session"))
    }

    @Test
    fun expiredAuthorityRendersRefreshRequiredWithTheNextBoundaryAsFreshness() {
        assertTrue(WatchRuntimeFactory.handleDebugScenario(application, SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
        val fresh = requestTile()
        assertTrue(
            fresh.freshnessIntervalMillis in (WearProtocol.MAX_MUTATION_WINDOW_MS - 5_000L)..
                WearProtocol.MAX_MUTATION_WINDOW_MS,
            "fresh Tile asks to come back at the authority boundary: ${fresh.freshnessIntervalMillis}",
        )
        assertTrue(application.getString(R.string.refresh_required) !in fresh.texts())

        assertTrue(WatchRuntimeFactory.handleDebugScenario(application, "expire"))
        val stale = requestTile()
        assertTrue(application.getString(R.string.refresh_required) in stale.texts(), stale.texts().toString())
        assertTrue(
            stale.freshnessIntervalMillis in 1L..DEBUG_UNCALIBRATED_RECONNECT_WINDOW_MS,
            "after expiry the ongoing deadline is the next boundary: ${stale.freshnessIntervalMillis}",
        )
    }

    private fun requestTile(): TileBuilders.Tile {
        val service = Robolectric.setupService(WorkoutTileService::class.java)
        val device = DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(192)
            .setScreenHeightDp(192)
            .build()
        val request = RequestBuilders.TileRequest.Builder().setDeviceConfiguration(device).build()
        return service.requestTile(request)
    }

    /** `TileService.onTileRequest` is protected; the test calls it the way the platform binder would. */
    private fun WorkoutTileService.requestTile(request: RequestBuilders.TileRequest): TileBuilders.Tile {
        val method = WorkoutTileService::class.java
            .getDeclaredMethod("onTileRequest", RequestBuilders.TileRequest::class.java)
            .apply { isAccessible = true }
        return (method.invoke(this, request) as ListenableFuture<*>).get(5, TimeUnit.SECONDS) as TileBuilders.Tile
    }

    private fun TileBuilders.Tile.texts(): List<String> =
        requireNotNull(tileTimeline).timelineEntries.single().layout!!.root!!.texts()

    private fun LayoutElementBuilders.LayoutElement.texts(): List<String> = when (this) {
        is LayoutElementBuilders.Text -> listOfNotNull(text?.value)
        is LayoutElementBuilders.Box -> contents.flatMap { it.texts() }
        is LayoutElementBuilders.Column -> contents.flatMap { it.texts() }
        else -> emptyList()
    }
}
