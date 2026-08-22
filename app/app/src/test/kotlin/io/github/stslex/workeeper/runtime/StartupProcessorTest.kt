// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.feature.recovery.boot.RecoveryBootstrap
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator.PreflightOutcome
import io.github.stslex.workeeper.feature.recovery.domain.StartupCheck
import io.github.stslex.workeeper.feature.recovery.domain.StartupMigrationCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.StartupMigrationFailureReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Ordering, short-circuit, guard, and failure-policy pins for the extracted [StartupProcessor]
 * (Phase 5, spec §8.3 / §12) — each test names the pre-extraction guarantee it preserves.
 */
internal class StartupProcessorTest {

    private val restoreCoordinator = mockk<RestoreRecoveryCoordinator>()
    private val migrationCoordinator = mockk<StartupMigrationCoordinator> {
        coEvery { checkAndRouteOrProceed() } returns StartupCheck.Proceed
        every { lastDecision } returns null
    }
    private val imageStorage = mockk<ImageStorage> {
        coEvery { cleanupTempFiles() } returns Unit
    }
    private val recoveryBootstrap = mockk<RecoveryBootstrap>()
    private val graph = mockk<AppGraph> {
        every { restoreRecoveryCoordinator } returns this@StartupProcessorTest.restoreCoordinator
        every { startupMigrationCoordinator } returns this@StartupProcessorTest.migrationCoordinator
        every { imageStorage } returns this@StartupProcessorTest.imageStorage
        every { recoveryBootstrap } returns this@StartupProcessorTest.recoveryBootstrap
    }
    private val appDatabase = mockk<AppDatabase>()
    private val lifetime = AppScopeLifetime()

    private var plannerRuns = 0
    private var plannerError: Throwable? = null
    private var lowRam = false

    private fun processor() = StartupProcessor(
        isLowRamDevice = { lowRam },
        warmPlanner = {
            plannerError?.let { throw it }
            plannerRuns++
        },
        // Unconfined so fire-and-forget chores execute inline and the test can assert them
        // synchronously; production keeps Dispatchers.IO via the default.
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun coldStart(): StartupOutcome =
        processor().coldStart(graph = graph, appDatabase = appDatabase, lifetime = lifetime)

    @Test
    fun `scenario 1 rollback short-circuits everything - RestartRequired, no scenario 2, no chores`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.RestoreRolledBack

        val outcome = coldStart()

        assertEquals(StartupOutcome.RestartRequired, outcome)
        // Pre-extraction, the non-returning restartApp() prevented all of these from running.
        coVerify(exactly = 0) { migrationCoordinator.checkAndRouteOrProceed() }
        coVerify(exactly = 0) { imageStorage.cleanupTempFiles() }
        assertEquals(0, plannerRuns)
        coVerify(exactly = 0) { graph.recoveryBootstrap }
    }

    @Test
    fun `scenario 1 success skips scenario 2 but still arms the chores and the observer`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.RestoreSucceeded

        val outcome = coldStart()

        assertEquals(StartupOutcome.Proceed, outcome)
        coVerify(exactly = 0) { migrationCoordinator.checkAndRouteOrProceed() }
        coVerify(exactly = 1) { imageStorage.cleanupTempFiles() }
        // Measured pre-extraction edge (spec §2): lastDecision is null on this path, so the
        // planner guard passes and the warm-up runs against the freshly-restored file.
        assertEquals(1, plannerRuns)
        coVerify(exactly = 1) { graph.recoveryBootstrap }
    }

    @Test
    fun `scenario 1 no-op runs scenario 2, strictly after scenario 1`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp

        val outcome = coldStart()

        assertEquals(StartupOutcome.Proceed, outcome)
        coVerifyOrder {
            restoreCoordinator.handlePostRestoreLaunch()
            migrationCoordinator.checkAndRouteOrProceed()
        }
    }

    @Test
    fun `route-to-recovery decision skips the planner but keeps cleanup and observer arming`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp
        every { migrationCoordinator.lastDecision } returns
            StartupCheck.RouteToRecovery(StartupMigrationFailureReason.APP_DOWNGRADE)

        val outcome = coldStart()

        assertEquals(StartupOutcome.RouteToRecovery, outcome)
        // ANALYZE opens the database — the one thing a RouteToRecovery start must not do.
        assertEquals(0, plannerRuns)
        coVerify(exactly = 1) { imageStorage.cleanupTempFiles() }
        coVerify(exactly = 1) { graph.recoveryBootstrap }
    }

    @Test
    fun `low-ram device skips the planner but keeps cleanup`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp
        lowRam = true

        val outcome = coldStart()

        assertEquals(StartupOutcome.Proceed, outcome)
        assertEquals(0, plannerRuns)
        coVerify(exactly = 1) { imageStorage.cleanupTempFiles() }
    }

    @Test
    fun `suspend preflight - scenario 1 rollback short-circuits, no scenario 2, no chores`() =
        kotlinx.coroutines.test.runTest {
            coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.RestoreRolledBack

            val outcome = processor().preflightAndArm(graph, appDatabase, lifetime)

            assertEquals(StartupOutcome.RestartRequired, outcome)
            coVerify(exactly = 0) { migrationCoordinator.checkAndRouteOrProceed() }
            coVerify(exactly = 0) { imageStorage.cleanupTempFiles() }
            coVerify(exactly = 0) { graph.recoveryBootstrap }
        }

    @Test
    fun `suspend preflight - no-op runs scenario 2 strictly after scenario 1, then arms`() =
        kotlinx.coroutines.test.runTest {
            coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp

            val outcome = processor().preflightAndArm(graph, appDatabase, lifetime)

            assertEquals(StartupOutcome.Proceed, outcome)
            coVerifyOrder {
                restoreCoordinator.handlePostRestoreLaunch()
                migrationCoordinator.checkAndRouteOrProceed()
            }
            coVerify(exactly = 1) { imageStorage.cleanupTempFiles() }
            coVerify(exactly = 1) { graph.recoveryBootstrap }
        }

    @Test
    fun `suspend preflight - restore success skips scenario 2 but still arms`() =
        kotlinx.coroutines.test.runTest {
            coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.RestoreSucceeded

            val outcome = processor().preflightAndArm(graph, appDatabase, lifetime)

            assertEquals(StartupOutcome.Proceed, outcome)
            coVerify(exactly = 0) { migrationCoordinator.checkAndRouteOrProceed() }
            coVerify(exactly = 1) { graph.recoveryBootstrap }
        }

    @Test
    fun `planner failure is caught - startup completes with Proceed`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp
        plannerError = IllegalStateException("corrupt file")

        val outcome = coldStart()

        // Caught rather than propagated: a corrupt db must not take down the launch that most
        // needs to reach recovery.
        assertEquals(StartupOutcome.Proceed, outcome)
        coVerify(exactly = 1) { graph.recoveryBootstrap }
    }
}
