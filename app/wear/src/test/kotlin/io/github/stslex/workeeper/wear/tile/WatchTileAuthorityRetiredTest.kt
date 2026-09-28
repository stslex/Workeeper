// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import androidx.wear.protolayout.LayoutElementBuilders
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** A handshake retires authority without moving the display; the rendered Tile changes but no refresh is requested. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
internal class WatchTileAuthorityRetiredTest {
    @Test
    fun retiredAuthorityChangesTheRenderedTileAndRequestsAnUpdate() = runTest(UnconfinedTestDispatcher()) {
        val env = RuntimeTestEnvironment()
        val context = RuntimeEnvironment.getApplication()
        val renderer = WorkoutTileRenderer(context)
        fun rendered(): List<String> = renderer.render(WearSurfaceMapper.map(env.owner.snapshot.value), 192).texts()
        var requests = 0
        WatchTileCoordinator(env.owner.snapshot, backgroundScope, renderer::refreshKey, { requests++ }) {
            throw AssertionError(it)
        }
        assertEquals(1, requests)
        val loading = rendered()

        assertTrue(env.accept())
        val fresh = rendered()
        assertNotEquals(loading, fresh)
        assertEquals(2, requests, "Anchor: an accepted snapshot changes the display and requests one update")
        assertTrue(context.getString(R.string.set_progress, 1, 2) in fresh, "Anchor: fresh progress is rendered")

        val display = env.owner.snapshot.value.workout.display
        env.owner.issueHandshake()
        assertEquals(display, env.owner.snapshot.value.workout.display, "A handshake does not move the display")
        assertEquals(LocalMutationAuthority.Retired, env.owner.snapshot.value.workout.authority)
        val retired = rendered()
        assertTrue(
            context.getString(R.string.refresh_required) in retired,
            "Retired authority renders refresh required",
        )
        assertNotEquals(fresh, retired, "The rendered Tile content changed")
        assertEquals(3, requests, "A rendered-content change must request a system Tile update")
    }

    private fun androidx.wear.tiles.TileBuilders.Tile.texts(): List<String> =
        requireNotNull(tileTimeline).timelineEntries.single().layout!!.root!!.texts()

    private fun LayoutElementBuilders.LayoutElement.texts(): List<String> = when (this) {
        is LayoutElementBuilders.Text -> listOfNotNull(text?.value)
        is LayoutElementBuilders.Box -> contents.flatMap { it.texts() }
        is LayoutElementBuilders.Column -> contents.flatMap { it.texts() }
        else -> emptyList()
    }
}
