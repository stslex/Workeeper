// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@Regression
class WorkoutTileRendererDeviceTest {

    @Test
    fun everyTileStateBuildsOneBoundedTimeline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val renderer = WorkoutTileRenderer(context)
        tileStates().forEach { state ->
            val tile = renderer.render(state)
            assertEquals(1, requireNotNull(tile.tileTimeline).timelineEntries.size)
            assertNotNull(tile.tileTimeline?.timelineEntries?.single()?.layout?.root)
        }
    }

    private fun tileStates(): List<WearSurfaceModel> {
        val active = requireNotNull(
            SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY),
        )
        return listOf(
            WearSurfaceModel(WearSurfaceKind.LOADING),
            WearSurfaceModel(WearSurfaceKind.NO_SESSION),
            active,
            WearSurfaceModel(WearSurfaceKind.PHONE_ACTION_NO_SETS, exerciseName = "Exercise"),
            WearSurfaceModel(
                WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
                exerciseName = "Exercise",
            ),
            WearSurfaceModel(WearSurfaceKind.PAYLOAD_TOO_LARGE),
            requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.COMPLETE)),
            active.copy(kind = WearSurfaceKind.REFRESH_REQUIRED),
            active.copy(kind = WearSurfaceKind.DISCONNECTED),
            WearSurfaceModel(WearSurfaceKind.RETRYABLE_ERROR),
            WearSurfaceModel(WearSurfaceKind.PROTOCOL_MISMATCH),
        )
    }
}
