// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.tiles.TileBuilders
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.runtime.OVERDUE_BOUNDARY_FRESHNESS_MS
import io.github.stslex.workeeper.wear.runtime.RECOVERY_RETRY_FRESHNESS_MS
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import io.github.stslex.workeeper.wear.runtime.WatchRuntime
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import io.github.stslex.workeeper.wear.runtime.tileFreshnessIntervalMs
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Locale

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
        val loading = renderer.renderFrame(env.owner, Locale.US, 192)
        assertEquals(0L, loading.freshnessIntervalMillis, "LOADING has no boundary")

        assertTrue(env.accept())
        val active = renderer.renderFrame(env.owner, Locale.US, 192)
        assertEquals(
            WearProtocol.MAX_MUTATION_WINDOW_MS,
            active.freshnessIntervalMillis,
            "ACTIVE with window W -> freshness W",
        )
        assertNull(
            requireNotNull(active.tileTimeline).timelineEntries.single().validity,
            "no wall-clock validity: Phase 1 section 5.1 never lets wall clock decide freshness",
        )

        env.now = 121_000L
        val expired = env.owner.tileFrame(Locale.US)
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, WearSurfaceMapper.map(expired.snapshot).kind)
        assertEquals(5_000L, expired.freshnessIntervalMs, "after expiry the ongoing deadline is next")
    }

    @Test
    fun aBoundaryThatPassesAfterPublicationIsNotEncodedAsNoRefresh() {
        val env = RuntimeTestEnvironment()
        assertTrue(env.accept())
        // Publication ends by replacing the one-shot deadline; the clock passes the authority boundary
        // there, inside the Tile's owner operation, after the snapshot is published and before the
        // freshness is read. Before the single operation this was the gap between onWake() and the
        // separate freshness read.
        env.scheduler.afterReplace = { env.now = 1_000L + WearProtocol.MAX_MUTATION_WINDOW_MS + 1L }
        val frame = env.owner.tileFrame(Locale.US)
        assertEquals(WearSurfaceKind.ACTIVE, WearSurfaceMapper.map(frame.snapshot).kind, "published pre-boundary")
        assertEquals(
            OVERDUE_BOUNDARY_FRESHNESS_MS,
            frame.freshnessIntervalMs,
            "a boundary exists, so 0 (never refresh) is wrong; an overdue one asks for the earliest refresh",
        )
    }

    @Test
    fun zeroMeansNoBoundaryAndNothingElse() {
        assertEquals(0L, tileFreshnessIntervalMs(nextBoundaryMs = null, nowMs = 5L, recoveryRequired = false))
        assertEquals(7L, tileFreshnessIntervalMs(nextBoundaryMs = 12L, nowMs = 5L, recoveryRequired = false))
        assertEquals(OVERDUE_BOUNDARY_FRESHNESS_MS, tileFreshnessIntervalMs(5L, 5L, false), "due now")
        assertEquals(OVERDUE_BOUNDARY_FRESHNESS_MS, tileFreshnessIntervalMs(4L, 5L, false), "already passed")
        assertEquals(RECOVERY_RETRY_FRESHNESS_MS, tileFreshnessIntervalMs(null, 5L, true), "recovery is a boundary")
        assertEquals(RECOVERY_RETRY_FRESHNESS_MS, tileFreshnessIntervalMs(12L, 5L, true))
    }

    @Test
    fun aPendingRecoveryAsksForARetryRatherThanNoRefresh() {
        val env = RuntimeTestEnvironment()
        assertTrue(env.accept())
        env.failNextRead = true
        val restarted = env.newOwner()
        env.failNextRead = true
        val frame = restarted.tileFrame(Locale.US)
        assertTrue(frame.snapshot.recoveryRequired, "Anchor: the frame's own retry read failed, so recovery is pending")
        assertEquals(RECOVERY_RETRY_FRESHNESS_MS, frame.freshnessIntervalMs)
    }

    @Test
    fun theTileRendersTheSnapshotItsFreshnessWasReadWith() {
        val env = RuntimeTestEnvironment()
        assertTrue(env.accept())
        val renderer = renderer()
        val loadingSnapshot = WatchRuntimeSnapshot(locale = Locale.US)
        // The runtime's published flow has moved on (to LOADING) since the owner handed out the frame.
        val movedOn = object : WatchRuntime by env.owner {
            override val snapshot: StateFlow<WatchRuntimeSnapshot> = MutableStateFlow(loadingSnapshot)
        }
        val tile = renderer.renderFrame(movedOn, Locale.US, 192)

        val frameTexts = renderer.render(WearSurfaceMapper.map(env.owner.snapshot.value), 192).texts()
        val loadingTexts = renderer.render(WearSurfaceMapper.map(loadingSnapshot), 192).texts()
        assertNotEquals(loadingTexts, frameTexts, "Anchor: the two snapshots render differently")
        assertEquals(frameTexts, tile.texts(), "the Tile renders the frame's snapshot, not a later flow value")
        assertEquals(WearProtocol.MAX_MUTATION_WINDOW_MS, tile.freshnessIntervalMillis)
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

    private fun TileBuilders.Tile.texts(): List<String> =
        requireNotNull(tileTimeline).timelineEntries.single().layout!!.root!!.texts()

    private fun LayoutElementBuilders.LayoutElement.texts(): List<String> = when (this) {
        is LayoutElementBuilders.Text -> listOfNotNull(text?.value)
        is LayoutElementBuilders.Box -> contents.flatMap { it.texts() }
        is LayoutElementBuilders.Column -> contents.flatMap { it.texts() }
        else -> emptyList()
    }
}
