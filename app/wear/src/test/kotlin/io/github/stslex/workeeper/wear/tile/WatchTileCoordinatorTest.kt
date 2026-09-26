// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import io.github.stslex.workeeper.wear.runtime.ControllerAction
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
internal class WatchTileCoordinatorTest {
    @Test
    fun persistedRefreshAndExpiryUpdateTheTileWithoutAScreen() = runTest(UnconfinedTestDispatcher()) {
        val env = RuntimeTestEnvironment()
        var requests = 0
        WatchTileCoordinator(env.owner.snapshot, backgroundScope, {
            requests++
            env.trace += "tile"
        }, { throw AssertionError(it) })
        assertEquals(1, requests)
        assertTrue(env.accept())
        assertEquals(2, requests, "An accepted snapshot must invalidate the system Tile")
        assertTrue(env.trace.indexOfFirst { it.startsWith("cache:") } < env.trace.lastIndexOf("tile"))
        env.owner.onWake()
        env.owner.onAction(ControllerAction.SetReps(12))
        env.owner.onWake()
        assertEquals(2, requests, "Tile requests and draft edits must not trigger update loops")
        env.owner.disconnected()
        assertEquals(3, requests)
        assertTrue(env.accept(ReducerTestFixtures.active(leaseGeneration = 2, leaseId = ReducerTestFixtures.lease2)))
        assertEquals(4, requests, "Reconnect refresh must replace a stale system Tile")
        env.now = 121_000L
        env.owner.onWake()
        assertEquals(5, requests, "Authority expiry must change the cached Tile status")
        env.owner.setLocale(Locale.forLanguageTag("ru"))
        assertEquals(6, requests)
        assertTrue(env.accept(ReducerTestFixtures.noSession()))
        assertEquals(7, requests)
    }

    @Test
    fun platformRequestFailureDoesNotStopLaterUpdates() = runTest(UnconfinedTestDispatcher()) {
        val env = RuntimeTestEnvironment()
        var requests = 0
        var failures = 0
        WatchTileCoordinator(env.owner.snapshot, backgroundScope, {
            requests++
            if (requests == 1) error("Synthetic platform failure")
        }, { failures++ })
        assertTrue(env.accept())
        assertEquals(2, requests)
        assertEquals(1, failures)
        assertTrue(
            env.owner.snapshot.value.workout.display is io.github.stslex.workeeper.wear.state.WatchDisplayState.Active,
        )
    }

    @Test
    fun cancellationStopsTileObservationWithoutReportingFailure() = runTest(UnconfinedTestDispatcher()) {
        val env = RuntimeTestEnvironment()
        val reported = mutableListOf<Throwable>()
        var requests = 0
        WatchTileCoordinator(env.owner.snapshot, backgroundScope, {
            requests++
            throw CancellationException("Synthetic Tile cancellation")
        }, { reported += it })
        assertTrue(reported.isEmpty(), "Tile cancellation must not become a recoverable platform failure")
        assertTrue(env.accept())
        assertEquals(1, requests, "Cancelled Tile observation must stop collecting snapshots")
    }

    @Test
    fun fatalPlatformFailureReachesTheProcessExceptionHandler() = runTest(UnconfinedTestDispatcher()) {
        val env = RuntimeTestEnvironment()
        val reported = mutableListOf<Throwable>()
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, failure -> uncaught += failure },
        )
        try {
            val fatal = LinkageError("Synthetic Tile linkage failure")
            WatchTileCoordinator(env.owner.snapshot, scope, { throw fatal }, { reported += it })
            assertSame(fatal, uncaught.singleOrNull(), "Fatal Tile failure must reach the process exception handler")
            assertTrue(reported.isEmpty(), "Fatal Tile failure must not be reported as recoverable")
        } finally {
            scope.cancel()
        }
    }
}
