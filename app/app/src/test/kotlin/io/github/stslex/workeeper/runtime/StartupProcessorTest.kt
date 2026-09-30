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

    private val restoreCoordinator = mockk<RestoreRecoveryCoordinator> {
        coEvery { sweepRecoveryGarbage() } returns Unit
        coEvery { publishPendingTerminalOutbox() } returns true
    }

    private var peeks = 0
    private var peekDecision: StartupCheck = StartupCheck.Proceed
    private var lastDecisionValue: StartupCheck? = null

    // The peek WRITES the decision, exactly as the real coordinator does: an outcome assertion
    // over a pre-stubbed `lastDecision` would stay green with the peek deleted.
    private val migrationCoordinator = mockk<StartupMigrationCoordinator> {
        coEvery { checkAndRouteOrProceed() } coAnswers {
            peeks++
            lastDecisionValue = peekDecision
            peekDecision
        }
        // Recording WRITES the decision, exactly as the real coordinator does — so an assertion
        // over `lastDecision` fails if the guard catches without recording.
        coEvery { recordLiveDatabaseOpenFailure(any()) } coAnswers {
            val recorded = StartupCheck.RouteToRecovery(
                StartupMigrationFailureReason.LIVE_DB_OPEN_FAILED,
            )
            lastDecisionValue = recorded
            recorded
        }
        every { lastDecision } answers { lastDecisionValue }
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
    private var wearStorageError: Throwable? = null
    private var lowRam = false
    private val wearEpochRotations = mutableListOf<Boolean>()

    private var seals = 0

    private fun processor() = StartupProcessor(
        isLowRamDevice = { lowRam },
        sealWorkerAdmission = { seals++ },
        warmPlanner = {
            plannerError?.let { throw it }
            plannerRuns++
        },
        prepareWearStorage = { _, rotate ->
            wearStorageError?.let { throw it }
            wearEpochRotations += rotate
        },
        // Unconfined so fire-and-forget chores execute inline; production keeps Dispatchers.IO.
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun coldStart(): StartupOutcome =
        processor().coldStart(graph = graph, appDatabase = appDatabase, lifetime = lifetime)

    @Test
    fun `scenario 1 rollback short-circuits everything - RestartRequired, no scenario 2, no chores`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.RestoreRolledBack

        val outcome = coldStart()

        assertEquals(StartupOutcome.RestartRequired, outcome)
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
        coVerify(exactly = 1) { restoreCoordinator.publishPendingTerminalOutbox() }
        coVerify(exactly = 1) { imageStorage.cleanupTempFiles() }
        // lastDecision is null on this path, so the planner guard passes (spec §2).
        assertEquals(1, plannerRuns)
        coVerify(exactly = 1) { graph.recoveryBootstrap }
        assertEquals(listOf(true), wearEpochRotations)
    }

    @Test
    fun `scenario 1 no-op runs scenario 2, strictly after scenario 1`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp

        val outcome = coldStart()

        assertEquals(StartupOutcome.Proceed, outcome)
        coVerifyOrder {
            restoreCoordinator.handlePostRestoreLaunch()
            restoreCoordinator.sweepRecoveryGarbage()
            migrationCoordinator.checkAndRouteOrProceed()
        }
        coVerify(exactly = 1) { restoreCoordinator.sweepRecoveryGarbage() }
        coVerify(exactly = 0) { restoreCoordinator.publishPendingTerminalOutbox() }
        assertEquals(listOf(false), wearEpochRotations)
    }

    @Test
    fun `route-to-recovery decision skips the planner but keeps cleanup and observer arming`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp
        peekDecision = StartupCheck.RouteToRecovery(StartupMigrationFailureReason.APP_DOWNGRADE)

        val outcome = coldStart()

        assertEquals(StartupOutcome.RouteToRecovery, outcome)
        // ANALYZE opens the database — the one thing a RouteToRecovery start must not do.
        assertEquals(0, plannerRuns)
        coVerify(exactly = 1) { imageStorage.cleanupTempFiles() }
        coVerify(exactly = 1) { graph.recoveryBootstrap }
        assertEquals(emptyList<Boolean>(), wearEpochRotations)
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
            coVerify(exactly = 0) { restoreCoordinator.publishPendingTerminalOutbox() }
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
            coVerifyOrder {
                graph.recoveryBootstrap
                restoreCoordinator.publishPendingTerminalOutbox()
            }
        }

    @Test
    fun `post-arming terminal failure routes cold start to recovery and seals workers`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns
            PreflightOutcome.RestoreSucceeded
        coEvery { restoreCoordinator.publishPendingTerminalOutbox() } returns false

        val outcome = coldStart()

        assertEquals(StartupOutcome.RouteToRecovery, outcome)
        assertEquals(1, seals)
        coVerify(exactly = 0) { graph.recoveryBootstrap }
        coVerify(exactly = 0) { imageStorage.cleanupTempFiles() }
        assertEquals(0, plannerRuns)
    }

    @Test
    fun `post-arming terminal failure refuses in-process candidate publication`() =
        kotlinx.coroutines.test.runTest {
            coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns
                PreflightOutcome.RestoreSucceeded
            coEvery { restoreCoordinator.publishPendingTerminalOutbox() } returns false

            val outcome = processor().preflightAndArm(graph, appDatabase, lifetime)

            assertEquals(StartupOutcome.FinalizationPending, outcome)
            coVerifyOrder {
                graph.recoveryBootstrap
                restoreCoordinator.publishPendingTerminalOutbox()
            }
        }

    @Test
    fun `RecoveryRequired routes to recovery and arms ZERO db-bound work`() {
        // Terminal recovery: the mutation's outcome is unknown, so this launch arms nothing.
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns
            PreflightOutcome.RecoveryRequired

        val outcome = coldStart()

        assertEquals(StartupOutcome.RouteToRecovery, outcome)
        coVerify(exactly = 0) { migrationCoordinator.checkAndRouteOrProceed() }
        coVerify(exactly = 0) { graph.recoveryBootstrap }
        coVerify(exactly = 0) { imageStorage.cleanupTempFiles() }
        assertEquals(0, plannerRuns, "ANALYZE would open the unknown database")
    }

    @Test
    fun `suspend preflight - RecoveryRequired routes to recovery and arms ZERO db-bound work`() =
        kotlinx.coroutines.test.runTest {
            coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns
                PreflightOutcome.RecoveryRequired

            val outcome = processor().preflightAndArm(graph, appDatabase, lifetime)

            assertEquals(StartupOutcome.RouteToRecovery, outcome)
            coVerify(exactly = 0) { migrationCoordinator.checkAndRouteOrProceed() }
            coVerify(exactly = 0) { graph.recoveryBootstrap }
            coVerify(exactly = 0) { imageStorage.cleanupTempFiles() }
            assertEquals(0, plannerRuns)
        }

    @Test
    fun `RecoveryCompleted peeks the live schema - the rollback replaced the file out of process`() {
        // The committed rollback ran in an EARLIER process and validated nothing on the way in,
        // so this launch has proven nothing about the file it is about to open.
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns
            PreflightOutcome.RecoveryCompleted

        val outcome = coldStart()

        assertEquals(StartupOutcome.Proceed, outcome)
        assertEquals(1, peeks, "the scenario-2 peek must run on a RecoveryCompleted launch")
        coVerify(exactly = 1) { graph.recoveryBootstrap }
        assertEquals(listOf(true), wearEpochRotations)
    }

    @Test
    fun `a RecoveryCompleted launch over an unopenable file routes to recovery`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns
            PreflightOutcome.RecoveryCompleted
        peekDecision = StartupCheck.RouteToRecovery(StartupMigrationFailureReason.APP_DOWNGRADE)

        val outcome = coldStart()

        assertEquals(StartupOutcome.RouteToRecovery, outcome)
        assertEquals(0, plannerRuns, "ANALYZE would open the file the peek just rejected")
        assertEquals(emptyList<Boolean>(), wearEpochRotations)
    }

    @Test
    fun `suspend preflight - RecoveryCompleted peeks the live schema too`() =
        kotlinx.coroutines.test.runTest {
            coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns
                PreflightOutcome.RecoveryCompleted

            val outcome = processor().preflightAndArm(graph, appDatabase, lifetime)

            assertEquals(StartupOutcome.Proceed, outcome)
            assertEquals(1, peeks)
            assertEquals(listOf(true), wearEpochRotations)
        }

    @Test
    fun `a terminal recovery route SEALS worker admission - both scenarios`() {
        // Startup arms nothing, but the process keeps running: a persisted BackupWorker would
        // otherwise bind a lease over the file this launch declared unprovable.
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns
            PreflightOutcome.RecoveryRequired
        assertEquals(StartupOutcome.RouteToRecovery, coldStart())
        assertEquals(1, seals, "scenario 1 seals")

        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp
        peekDecision = StartupCheck.RouteToRecovery(StartupMigrationFailureReason.APP_DOWNGRADE)
        assertEquals(StartupOutcome.RouteToRecovery, coldStart())
        assertEquals(2, seals, "scenario 2 seals too")
    }

    @Test
    fun `an ordinary launch never seals worker admission`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp

        assertEquals(StartupOutcome.Proceed, coldStart())

        assertEquals(0, seals, "auto-backup must keep running on a healthy launch")
    }

    @Test
    fun `the CANDIDATE preflight never seals - its abort leaves a healthy generation serving`() =
        kotlinx.coroutines.test.runTest {
            // A candidate's RouteToRecovery aborts the transition; generation N keeps serving and
            // its auto-backup must not be killed by the aborted successor's verdict.
            coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns
                PreflightOutcome.RecoveryRequired

            val outcome = processor().preflightAndArm(graph, appDatabase, lifetime)

            assertEquals(StartupOutcome.RouteToRecovery, outcome)
            assertEquals(0, seals)
        }

    @Test
    fun `a throwing first Room open is caught AND recorded where MainActivity reads it`() {
        // `hasMigrationPath` answers "registered", never "succeeds", so a registered migration
        // that throws is inside the class of failures the peek returns Proceed for.
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp
        val thrown = IllegalStateException("registered migration threw on first open")
        wearStorageError = thrown

        val outcome = coldStart()

        // Nothing propagated: reaching this line at all is half the assertion.
        assertEquals(StartupOutcome.RouteToRecovery, outcome)
        // The other half. MainActivity routes on lastDecision and on nothing else, so a guard
        // that only caught would leave a Proceed verdict over an unopenable database.
        assertEquals(
            StartupCheck.RouteToRecovery(StartupMigrationFailureReason.LIVE_DB_OPEN_FAILED),
            migrationCoordinator.lastDecision,
        )
        coVerify(exactly = 1) { migrationCoordinator.recordLiveDatabaseOpenFailure(thrown) }
        assertEquals(0, plannerRuns, "ANALYZE would reopen the file that just threw")
        assertEquals(1, seals, "an unprovable live file must refuse DB-bound worker admission")
    }

    @Test
    fun `a throwing first Room open on the CANDIDATE path routes without sealing`() =
        kotlinx.coroutines.test.runTest {
            coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp
            wearStorageError = IllegalStateException("registered migration threw on first open")

            val outcome = processor().preflightAndArm(graph, appDatabase, lifetime)

            assertEquals(StartupOutcome.RouteToRecovery, outcome)
            assertEquals(
                StartupCheck.RouteToRecovery(StartupMigrationFailureReason.LIVE_DB_OPEN_FAILED),
                migrationCoordinator.lastDecision,
            )
            assertEquals(0, seals, "generation N keeps serving; its auto-backup must survive")
        }

    @Test
    fun `planner failure is caught - startup completes with Proceed`() {
        coEvery { restoreCoordinator.handlePostRestoreLaunch() } returns PreflightOutcome.NoOp
        plannerError = IllegalStateException("corrupt file")

        val outcome = coldStart()

        // Caught, not propagated: a corrupt db must not take down the launch that needs recovery.
        assertEquals(StartupOutcome.Proceed, outcome)
        coVerify(exactly = 1) { graph.recoveryBootstrap }
    }
}
