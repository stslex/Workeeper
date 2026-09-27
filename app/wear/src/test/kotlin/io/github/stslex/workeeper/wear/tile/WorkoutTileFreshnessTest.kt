// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import io.github.stslex.workeeper.wear.ui.surface
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** The Tile asks the platform to come back at the owner's next boundary; nothing else drives its freshness. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkoutTileFreshnessTest {
    private fun renderer() = WorkoutTileRenderer(RuntimeEnvironment.getApplication())

    @Test
    fun freshnessIsTheRemainingMutationWindowAndZeroWithoutABoundary() {
        val env = RuntimeTestEnvironment()
        val renderer = renderer()
        assertNull(env.owner.remainingUntilNextBoundaryMs(), "LOADING has no boundary")
        val loading = renderer.render(WearSurfaceMapper.map(env.owner.snapshot.value), 192)
        assertEquals(0L, loading.freshnessIntervalMillis)

        assertTrue(env.accept())
        val remaining = env.owner.remainingUntilNextBoundaryMs()
        assertEquals(WearProtocol.MAX_MUTATION_WINDOW_MS, remaining, "ACTIVE with window W -> freshness W")
        val active = renderer.render(WearSurfaceMapper.map(env.owner.snapshot.value), 192, requireNotNull(remaining))
        assertEquals(WearProtocol.MAX_MUTATION_WINDOW_MS, active.freshnessIntervalMillis)
        assertNull(
            requireNotNull(active.tileTimeline).timelineEntries.single().validity,
            "no wall-clock validity: Phase 1 section 5.1 never lets wall clock decide freshness",
        )

        env.now = 121_000L
        env.owner.onWake()
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, env.owner.surface.value.kind)
        assertEquals(5_000L, env.owner.remainingUntilNextBoundaryMs(), "after expiry the ongoing deadline is next")
    }

    @Test
    fun aStaleTileRequestCausesAtMostOneFollowUpUpdateRequest() = runTest(UnconfinedTestDispatcher()) {
        val env = RuntimeTestEnvironment()
        var requests = 0
        WatchTileCoordinator(env.owner.snapshot, backgroundScope, renderer()::refreshKey, { requests++ }) {
            throw AssertionError(it)
        }
        assertTrue(env.accept())
        assertEquals(2, requests)

        env.now = 121_000L
        env.owner.onWake()
        assertEquals(3, requests, "the request at the freshness boundary changes the rendered Tile once")
        env.owner.onWake()
        assertEquals(3, requests, "the follow-up request renders the same content and asks for nothing")
    }
}
