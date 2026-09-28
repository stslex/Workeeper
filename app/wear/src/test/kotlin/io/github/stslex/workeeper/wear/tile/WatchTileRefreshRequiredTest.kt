// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import androidx.wear.protolayout.LayoutElementBuilders
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.WatchWorkoutReducer
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
internal class WatchTileRefreshRequiredTest {
    @Test
    fun rejectedRefreshChangesTheSystemTileKeyWithoutChangingDisplay() = runTest(UnconfinedTestDispatcher()) {
        val reducer = freshReducer()
        val snapshots = MutableStateFlow(WatchRuntimeSnapshot(workout = reducer.state))
        var requests = 0
        WatchTileCoordinator(snapshots, backgroundScope, { requests++ }, { throw AssertionError(it) })
        assertEquals(1, requests)
        val display = reducer.state.display
        rejectRefresh(reducer)
        assertEquals(display, reducer.state.display)
        snapshots.value = WatchRuntimeSnapshot(workout = reducer.state)
        assertEquals(2, requests, "A rejected refresh must invalidate the unchanged active Tile")
    }

    @Test
    fun rejectedRefreshRendersAVisibleReasonInsteadOfFreshProgress() {
        val reducer = freshReducer()
        rejectRefresh(reducer)
        val context = RuntimeEnvironment.getApplication()
        val tile = WorkoutTileRenderer(context).render(WearSurfaceMapper.map(reducer.state), 192)
        val root = requireNotNull(tile.tileTimeline).timelineEntries.single().layout!!.root!!
        assertTrue(
            context.getString(R.string.refresh_required) in root.texts(),
            "Rejected refresh must show the refresh-required reason on the system Tile",
        )
    }

    private fun freshReducer() = WatchWorkoutReducer().apply {
        val correlation = ReducerTestFixtures.id(901)
        issueHandshake(correlation, 1_000)
        assertTrue(receiveSnapshot(correlation, ReducerTestFixtures.active(revision = 3), 1_000).accepted)
    }

    private fun rejectRefresh(reducer: WatchWorkoutReducer) {
        assertFalse(reducer.receiveUnsolicited(ReducerTestFixtures.active(revision = 2, leaseGeneration = 3)).accepted)
        assertTrue(reducer.state.refreshRequired)
    }

    private fun LayoutElementBuilders.LayoutElement.texts(): List<String> = when (this) {
        is LayoutElementBuilders.Text -> listOfNotNull(text?.value)
        is LayoutElementBuilders.Box -> contents.flatMap { it.texts() }
        is LayoutElementBuilders.Column -> contents.flatMap { it.texts() }
        else -> emptyList()
    }
}
