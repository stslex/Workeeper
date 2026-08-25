// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndo
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndoTransition
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.LegacyRestoreOwners
import io.github.stslex.workeeper.core.data.backup.api.restore.LegacyRestoreState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreGarbageCollectionReport
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreRecoveryReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException

internal class RestoreRecoveryCoordinatorTest {

    private val appReinitializer = mockk<AppReinitializer>(relaxed = true)
    private val platformInfo = mockk<PlatformInfoProvider>(relaxed = true)
    private val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val databaseReplacement = mockk<DatabaseReplacement>(relaxed = true)
    private val restoreStateRepository = mockk<RestoreStateRepository>(relaxed = true)
    private val appDialogPublisher = mockk<AppDialogPublisher>(relaxed = true)
    private val reporter = mockk<RestoreRecoveryReporter>(relaxed = true)

    private lateinit var coordinator: RestoreRecoveryCoordinator

    @BeforeEach
    fun setUp() {
        every { platformInfo.appVersionName() } returns "1.2.3"
        coEvery { restoreStateRepository.readProtocol() } returns current()
        coEvery { restoreStateRepository.beginAttempt(any()) } returns true
        coEvery { restoreStateRepository.beginCompensation(any(), any()) } returns true
        coEvery { restoreStateRepository.recordAttemptCommitted(any()) } returns true
        coEvery { restoreStateRepository.discardPreparedAttempt(any()) } returns true
        coEvery { restoreStateRepository.finalizeAttempt(any(), any(), any()) } returns true
        coEvery { restoreStateRepository.acknowledgeTerminal(any()) } returns true
        coEvery { restoreStateRepository.installLegacyState(any(), any(), any()) } returns true
        coEvery { snapshotProvider.currentSchemaVersion() } returns APP_DATABASE_VERSION
        coEvery { snapshotProvider.inspectLiveDatabaseWithoutRoom() } returns
            BackupResult.Success(APP_DATABASE_VERSION)
        coEvery { snapshotProvider.validateUndo(any()) } returns BackupResult.Success(Unit)
        every { snapshotProvider.getUndoFile(any()) } returns File("durable-undo.db")
        every { snapshotProvider.getRestoreSourceFile(any()) } returns File("staged-restore.db")
        coEvery { snapshotProvider.deleteUndo(any()) } returns true
        coEvery { snapshotProvider.deleteRestoreSource(any()) } returns true
        coEvery { snapshotProvider.deleteLegacyPreRestore() } returns true
        coEvery { snapshotProvider.deleteRecoveryExport() } returns true
        coEvery { snapshotProvider.preserveDbBeforeMigration() } returns
            BackupResult.Success(File("recovery_export.db"))
        coEvery { snapshotProvider.sweepRecoveryFiles(any()) } returns
            RestoreGarbageCollectionReport(emptyList(), emptyList())
        coEvery { databaseReplacement.rollbackFromUndo(any(), any()) } returns
            DatabaseReplacementResult.Committed()
        coordinator = makeCoordinator()
    }

    @Test
    fun `empty reconciled state is a no-op and never swaps the healthy live db`() = runTest {
        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        coVerify(exactly = 0) { snapshotProvider.preserveDbBeforeMigration() }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        assertFalse(coordinator.recoverySurfaceRequired)
    }

    @Test
    fun `corrupt same-install protocol routes to recovery without a swap`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } returns
            RestoreProtocolRead.Corrupt(EPOCH, "bad owner state")

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
        coVerify(exactly = 1) { snapshotProvider.preserveDbBeforeMigration() }
        assertTrue(coordinator.recoverySurfaceRequired)
    }

    @Test
    fun `protocol read failure routes to recovery instead of crashing startup`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } throws IOException("state read failed")

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
        coVerify(exactly = 1) { snapshotProvider.preserveDbBeforeMigration() }
        assertTrue(coordinator.recoverySurfaceRequired)
    }

    @Test
    fun `pending terminal publishes idempotently before ordinary no-op cleanup`() = runTest {
        val terminal = RestoreTerminal.RestoreSucceeded(
            owner = RESTORE_OWNER,
            restoredAtEpochMs = 99L,
            previousVersionAvailable = true,
        )
        coEvery { restoreStateRepository.readProtocol() } returns
            current(
                activeUndo = ActiveUndo(UNDO_N, originalDataDateEpochMs = 1L),
                terminalOutbox = terminal,
            )

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
        coVerify(exactly = 1) {
            appDialogPublisher.publish(
                AppDialog.RestoreSuccess(
                    restoredAtEpochMs = 99L,
                    previousVersionAvailable = true,
                    terminalOwner = RESTORE_OWNER,
                ),
            )
        }
        coVerify(exactly = 1) { restoreStateRepository.acknowledgeTerminal(RESTORE_OWNER) }
    }

    @Test
    fun `restore success outbox with no promised pointer requires recovery`() = runTest {
        val terminal = RestoreTerminal.RestoreSucceeded(
            owner = RESTORE_OWNER,
            restoredAtEpochMs = 99L,
            previousVersionAvailable = true,
        )
        coEvery { restoreStateRepository.readProtocol() } returns
            current(activeUndo = null, terminalOutbox = terminal)

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        coVerify(exactly = 0) { restoreStateRepository.acknowledgeTerminal(any()) }
    }

    @Test
    fun `restore success outbox cannot advertise a different active owner`() = runTest {
        val terminal = RestoreTerminal.RestoreSucceeded(
            owner = RESTORE_OWNER,
            restoredAtEpochMs = 99L,
            previousVersionAvailable = true,
        )
        coEvery { restoreStateRepository.readProtocol() } returns current(
            activeUndo = ActiveUndo(UNDO_P, originalDataDateEpochMs = 1L),
            terminalOutbox = terminal,
        )

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `terminal publication failure remains pending with admission closed`() = runTest {
        val terminal = RestoreTerminal.UndoSucceeded(ROLLBACK_OWNER)
        coEvery { restoreStateRepository.readProtocol() } returns
            current(terminalOutbox = terminal)
        coEvery { appDialogPublisher.publish(any()) } throws IOException("dialog store failed")

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.FinalizationPending, outcome)
        assertTrue(coordinator.recoverySurfaceRequired)
        coVerify(exactly = 0) { restoreStateRepository.acknowledgeTerminal(any()) }
    }

    @Test
    fun `terminal acknowledgement failure remains replayable after durable dialog publish`() =
        runTest {
            val terminal = RestoreTerminal.UndoSucceeded(ROLLBACK_OWNER)
            coEvery { restoreStateRepository.readProtocol() } returns
                current(terminalOutbox = terminal)
            coEvery { restoreStateRepository.acknowledgeTerminal(ROLLBACK_OWNER) } throws
                IOException("restore-state acknowledgement failed")

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
            coVerify(exactly = 1) {
                appDialogPublisher.publish(
                    AppDialog.UndoRestoreSuccess(terminalOwner = ROLLBACK_OWNER),
                )
            }
            coVerify(exactly = 1) {
                restoreStateRepository.acknowledgeTerminal(ROLLBACK_OWNER)
            }
            assertFalse(coordinator.recoverySurfaceRequired)
        }

    @Test
    fun `post-arming handoff publishes and acknowledges the persisted success outbox`() = runTest {
        val terminal = RestoreTerminal.RestoreSucceeded(
            owner = RESTORE_OWNER,
            restoredAtEpochMs = 99L,
            previousVersionAvailable = true,
        )
        coEvery { restoreStateRepository.readProtocol() } returns current(
            activeUndo = ActiveUndo(UNDO_N, originalDataDateEpochMs = 1L),
            terminalOutbox = terminal,
        )

        assertTrue(coordinator.publishPendingTerminalOutbox())

        coVerify(exactly = 1) {
            appDialogPublisher.publish(
                AppDialog.RestoreSuccess(
                    restoredAtEpochMs = 99L,
                    previousVersionAvailable = true,
                    terminalOwner = RESTORE_OWNER,
                ),
            )
        }
        coVerify(exactly = 1) { restoreStateRepository.acknowledgeTerminal(RESTORE_OWNER) }
    }

    @Test
    fun `post-arming publication failure caches pending recovery state`() = runTest {
        val terminal = RestoreTerminal.RestoreSucceeded(
            owner = RESTORE_OWNER,
            restoredAtEpochMs = 99L,
            previousVersionAvailable = true,
        )
        coEvery { restoreStateRepository.readProtocol() } returns current(
            activeUndo = ActiveUndo(UNDO_N, originalDataDateEpochMs = 1L),
            terminalOutbox = terminal,
        )
        coEvery { appDialogPublisher.publish(any()) } throws IOException("dialog store failed")

        assertFalse(coordinator.publishPendingTerminalOutbox())

        assertEquals(
            RestoreRecoveryCoordinator.PreflightOutcome.FinalizationPending,
            coordinator.lastPreflightOutcome,
        )
        assertTrue(coordinator.recoverySurfaceRequired)
        coVerify(exactly = 1) { snapshotProvider.preserveDbBeforeMigration() }
        coVerify(exactly = 0) { restoreStateRepository.acknowledgeTerminal(any()) }
    }

    @Test
    fun `post-arming handoff refuses missing finalized success terminal`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } returns current(
            activeUndo = ActiveUndo(UNDO_N, originalDataDateEpochMs = 1L),
        )

        assertFalse(coordinator.publishPendingTerminalOutbox())

        assertEquals(
            RestoreRecoveryCoordinator.PreflightOutcome.FinalizationPending,
            coordinator.lastPreflightOutcome,
        )
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `post-arming protocol read failure remains pending without publication`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } throws IOException("state read failed")

        assertFalse(coordinator.publishPendingTerminalOutbox())

        assertEquals(
            RestoreRecoveryCoordinator.PreflightOutcome.FinalizationPending,
            coordinator.lastPreflightOutcome,
        )
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `post-arming owner mismatch remains pending without publication`() = runTest {
        val terminal = RestoreTerminal.RestoreSucceeded(
            owner = RESTORE_OWNER,
            restoredAtEpochMs = 99L,
            previousVersionAvailable = true,
        )
        coEvery { restoreStateRepository.readProtocol() } returns current(
            activeUndo = ActiveUndo(UNDO_P, originalDataDateEpochMs = 1L),
            terminalOutbox = terminal,
        )

        assertFalse(coordinator.publishPendingTerminalOutbox())

        assertEquals(
            RestoreRecoveryCoordinator.PreflightOutcome.FinalizationPending,
            coordinator.lastPreflightOutcome,
        )
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `verified restore atomically activates exact N and leaves success pending for arming`() =
        runTest {
            val attempt = restoreAttempt(phase = RestoreAttempt.Phase.Committed)
            coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
            val transition = slot<ActiveUndoTransition>()
            val terminal = slot<RestoreTerminal>()
            coEvery {
                restoreStateRepository.finalizeAttempt(
                    attemptId = RESTORE_OWNER,
                    activeUndoTransition = capture(transition),
                    terminal = capture(terminal),
                )
            } returns true

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded, outcome)
            assertEquals(
                ActiveUndoTransition.Replace(
                    ActiveUndo(UNDO_N, CONTEXT.startedAtEpochMs),
                ),
                transition.captured,
            )
            val success = terminal.captured as RestoreTerminal.RestoreSucceeded
            assertEquals(RESTORE_OWNER, success.owner)
            assertTrue(success.previousVersionAvailable)
            coVerify(exactly = 1) {
                restoreStateRepository.finalizeAttempt(RESTORE_OWNER, any(), any())
            }
            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
            coVerify(exactly = 0) { restoreStateRepository.acknowledgeTerminal(any()) }
        }

    @Test
    fun `committed restore with missing owned staged source requires recovery`() = runTest {
        val attempt = restoreAttempt(phase = RestoreAttempt.Phase.Committed)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        every { snapshotProvider.getRestoreSourceFile(attempt.sourceRef!!) } returns null

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        coVerify(exactly = 0) { restoreStateRepository.finalizeAttempt(any(), any(), any()) }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `verified restore with missing N clears old P and succeeds with undo unavailable`() =
        runTest {
            val attempt = restoreAttempt(phase = RestoreAttempt.Phase.Committed)
            coEvery { restoreStateRepository.readProtocol() } returns
                current(
                    attempt = attempt,
                    activeUndo = ActiveUndo(UNDO_P, originalDataDateEpochMs = 1L),
                )
            every { snapshotProvider.getUndoFile(UNDO_N) } returns null
            val transition = slot<ActiveUndoTransition>()
            val terminal = slot<RestoreTerminal>()
            coEvery {
                restoreStateRepository.finalizeAttempt(
                    RESTORE_OWNER,
                    capture(transition),
                    capture(terminal),
                )
            } returns true

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded, outcome)
            assertEquals(ActiveUndoTransition.Replace(activeUndo = null), transition.captured)
            assertFalse((terminal.captured as RestoreTerminal.RestoreSucceeded).previousVersionAvailable)
            coVerify(exactly = 0) { snapshotProvider.validateUndo(UNDO_N) }
        }

    @Test
    fun `verified restore replaces missing old P with exact N`() = runTest {
        val attempt = restoreAttempt(phase = RestoreAttempt.Phase.Committed)
        coEvery { restoreStateRepository.readProtocol() } returns current(
            attempt = attempt,
            activeUndo = ActiveUndo(UNDO_P, originalDataDateEpochMs = 1L),
        )
        every { snapshotProvider.getUndoFile(UNDO_P) } returns null
        val transition = slot<ActiveUndoTransition>()
        coEvery {
            restoreStateRepository.finalizeAttempt(
                RESTORE_OWNER,
                capture(transition),
                any(),
            )
        } returns true

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded, outcome)
        assertEquals(
            ActiveUndoTransition.Replace(ActiveUndo(UNDO_N, CONTEXT.startedAtEpochMs)),
            transition.captured,
        )
        coVerify(exactly = 0) { snapshotProvider.validateUndo(UNDO_P) }
    }

    @Test
    fun `restore finalization write failure cannot report or publish clean success`() = runTest {
        val attempt = restoreAttempt(phase = RestoreAttempt.Phase.Committed)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        coEvery { restoreStateRepository.finalizeAttempt(any(), any(), any()) } returns false

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.FinalizationPending, outcome)
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        coVerify(exactly = 0) { snapshotProvider.deleteUndo(any()) }
        coVerify(exactly = 1) { snapshotProvider.preserveDbBeforeMigration() }
    }

    @Test
    fun `thrown restore finalization write also remains pending`() = runTest {
        val attempt = restoreAttempt(phase = RestoreAttempt.Phase.Committed)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        coEvery { restoreStateRepository.finalizeAttempt(any(), any(), any()) } throws
            IOException("DataStore full")

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.FinalizationPending, outcome)
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `Prepared restore compensates from exact N under a fresh rollback owner`() = runTest {
        val attempt = restoreAttempt(phase = RestoreAttempt.Phase.Prepared)
        coEvery { restoreStateRepository.readProtocol() } returns
            current(
                attempt = attempt,
                activeUndo = ActiveUndo(UNDO_P, originalDataDateEpochMs = 1L),
            )
        val claimed = slot<RestoreAttempt.Rollback>()
        coEvery {
            restoreStateRepository.beginCompensation(RESTORE_OWNER, capture(claimed))
        } returns true
        coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, any()) } coAnswers {
            val effects = secondArg<DatabaseReplacementEffects>()
            effects.onBeforeMutation(UNDO_N, restoreSourceRef = null)
            effects.onMutationCommitted()
            DatabaseReplacementResult.Committed()
        }

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
        assertNotEquals(RESTORE_OWNER, claimed.captured.id)
        assertEquals(UNDO_N, claimed.captured.sourceRef)
        assertEquals(
            RestoreAttempt.RollbackOrigin.ScenarioOneRecovery,
            claimed.captured.origin,
        )
        coVerify(exactly = 1) {
            restoreStateRepository.recordAttemptCommitted(claimed.captured.id)
        }
        verify(exactly = 1) {
            reporter.recordRestoreTimeFailure(any(), CONTEXT, "1.2.3")
        }
    }

    @Test
    fun `Committed restore whose live verification fails compensates from exact N`() = runTest {
        val attempt = restoreAttempt(phase = RestoreAttempt.Phase.Committed)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        val verificationFailure = IOException("cannot verify restored generation")
        coEvery { snapshotProvider.currentSchemaVersion() } throws verificationFailure
        coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, any()) } returns
            DatabaseReplacementResult.Committed()

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
        coVerify(exactly = 1) { databaseReplacement.rollbackFromUndo(UNDO_N, any()) }
        verify(exactly = 1) {
            reporter.recordRestoreTimeFailure(verificationFailure, CONTEXT, "1.2.3")
        }
    }

    @Test
    fun `Committed restore without context cannot verify success and compensates exact N`() =
        runTest {
            val attempt = RestoreAttempt.Restore(
                id = RESTORE_OWNER,
                phase = RestoreAttempt.Phase.Committed,
                context = null,
                undoRef = UNDO_N,
                sourceRef = RestoreSourceRef(RESTORE_OWNER),
            )
            coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
            coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
            coVerify(exactly = 1) { databaseReplacement.rollbackFromUndo(UNDO_N, any()) }
        }

    @Test
    fun `same-epoch Prepared restore with missing N remains RecoveryRequired`() = runTest {
        val attempt = restoreAttempt(phase = RestoreAttempt.Phase.Prepared)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        every { snapshotProvider.getUndoFile(UNDO_N) } returns null
        coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, any()) } returns
            DatabaseReplacementResult.RejectedBeforeMutation(
                BackupError.CorruptedBackup("same-install undo missing"),
            )

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 1) { databaseReplacement.rollbackFromUndo(UNDO_N, any()) }
        coVerify(exactly = 0) { restoreStateRepository.finalizeAttempt(any(), any(), any()) }
    }

    @Test
    fun `legacy-shaped attempt with no C routes healthy live db to InterruptedRestore`() =
        runTest {
            val attempt = restoreAttempt(
                phase = RestoreAttempt.Phase.Prepared,
                undoRef = null,
                sourceRef = null,
            )
            coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.InterruptedRestore, outcome)
            coVerify(exactly = 1) { snapshotProvider.inspectLiveDatabaseWithoutRoom() }
            coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
            coVerify(exactly = 1) { snapshotProvider.preserveDbBeforeMigration() }
        }

    @Test
    fun `legacy-shaped attempt with no C and unhealthy live db requires recovery`() = runTest {
        val attempt = restoreAttempt(
            phase = RestoreAttempt.Phase.Prepared,
            undoRef = null,
            sourceRef = null,
        )
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        coEvery { snapshotProvider.inspectLiveDatabaseWithoutRoom() } returns
            BackupResult.Failure(BackupError.CorruptedBackup("bad live db"))

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
    }

    @Test
    fun `committed user rollback finalizes ClearIf exact N before deleting it`() = runTest {
        val attempt = rollbackAttempt(
            sourceRef = UNDO_N,
            origin = RestoreAttempt.RollbackOrigin.UserUndo,
            phase = RestoreAttempt.Phase.Committed,
        )
        coEvery { restoreStateRepository.readProtocol() } returns
            current(attempt = attempt, activeUndo = ActiveUndo(UNDO_N, 1L))
        val transition = slot<ActiveUndoTransition>()
        val terminal = slot<RestoreTerminal>()
        coEvery {
            restoreStateRepository.finalizeAttempt(
                ROLLBACK_OWNER,
                capture(transition),
                capture(terminal),
            )
        } returns true

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, outcome)
        assertEquals(ActiveUndoTransition.ClearIf(UNDO_N), transition.captured)
        assertEquals(RestoreTerminal.UndoSucceeded(ROLLBACK_OWNER), terminal.captured)
        coVerifyOrder {
            restoreStateRepository.finalizeAttempt(ROLLBACK_OWNER, any(), any())
            appDialogPublisher.publish(AppDialog.UndoRestoreSuccess(terminalOwner = ROLLBACK_OWNER))
            snapshotProvider.deleteUndo(UNDO_N)
        }
    }

    @Test
    fun `committed compensation from N clears only N and preserves unrelated active P`() =
        runTest {
            val attempt = rollbackAttempt(
                sourceRef = UNDO_N,
                origin = RestoreAttempt.RollbackOrigin.ScenarioOneRecovery,
                phase = RestoreAttempt.Phase.Committed,
            )
            coEvery { restoreStateRepository.readProtocol() } returns
                current(attempt = attempt, activeUndo = ActiveUndo(UNDO_P, 7L))
            val transition = slot<ActiveUndoTransition>()
            coEvery {
                restoreStateRepository.finalizeAttempt(
                    ROLLBACK_OWNER,
                    capture(transition),
                    any(),
                )
            } returns true

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, outcome)
            assertEquals(ActiveUndoTransition.ClearIf(UNDO_N), transition.captured)
            assertNotEquals(UNDO_P, (transition.captured as ActiveUndoTransition.ClearIf).appliedRef)
            coVerify(exactly = 1) { snapshotProvider.deleteUndo(UNDO_N) }
            coVerify(exactly = 0) { snapshotProvider.deleteUndo(UNDO_P) }
        }

    @Test
    fun `committed compensation cannot resolve while unrelated active P is missing`() = runTest {
        val attempt = rollbackAttempt(
            sourceRef = UNDO_N,
            origin = RestoreAttempt.RollbackOrigin.ScenarioOneRecovery,
            phase = RestoreAttempt.Phase.Committed,
        )
        coEvery { restoreStateRepository.readProtocol() } returns
            current(attempt = attempt, activeUndo = ActiveUndo(UNDO_P, 7L))
        every { snapshotProvider.getUndoFile(UNDO_P) } returns null

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        coVerify(exactly = 0) { restoreStateRepository.finalizeAttempt(any(), any(), any()) }
        coVerify(exactly = 0) { snapshotProvider.deleteUndo(any()) }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `committed compensation cannot resolve while unrelated active P is corrupt`() = runTest {
        val attempt = rollbackAttempt(
            sourceRef = UNDO_N,
            origin = RestoreAttempt.RollbackOrigin.ScenarioOneRecovery,
            phase = RestoreAttempt.Phase.Committed,
        )
        coEvery { restoreStateRepository.readProtocol() } returns
            current(attempt = attempt, activeUndo = ActiveUndo(UNDO_P, 7L))
        coEvery { snapshotProvider.validateUndo(UNDO_P) } returns
            BackupResult.Failure(BackupError.CorruptedBackup("bad active P"))

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        coVerify(exactly = 0) { restoreStateRepository.finalizeAttempt(any(), any(), any()) }
        coVerify(exactly = 0) { snapshotProvider.deleteUndo(any()) }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `committed user rollback finalizes from descriptor after exact active source is gone`() =
        runTest {
            val attempt = rollbackAttempt(
                sourceRef = UNDO_N,
                origin = RestoreAttempt.RollbackOrigin.UserUndo,
                phase = RestoreAttempt.Phase.Committed,
            )
            coEvery { restoreStateRepository.readProtocol() } returns
                current(attempt = attempt, activeUndo = ActiveUndo(UNDO_N, 1L))
            every { snapshotProvider.getUndoFile(UNDO_N) } returns null
            val transition = slot<ActiveUndoTransition>()
            coEvery {
                restoreStateRepository.finalizeAttempt(
                    ROLLBACK_OWNER,
                    capture(transition),
                    any(),
                )
            } returns true

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, outcome)
            assertEquals(ActiveUndoTransition.ClearIf(UNDO_N), transition.captured)
            coVerify(exactly = 0) { snapshotProvider.validateUndo(UNDO_N) }
            coVerify(exactly = 1) {
                restoreStateRepository.finalizeAttempt(ROLLBACK_OWNER, any(), any())
            }
        }

    @Test
    fun `rollback delete failure after durable finalization is deferred to persisted-state sweep`() =
        runTest {
            val attempt = rollbackAttempt(
                sourceRef = UNDO_N,
                origin = RestoreAttempt.RollbackOrigin.UserUndo,
                phase = RestoreAttempt.Phase.Committed,
            )
            coEvery { restoreStateRepository.readProtocol() } returns
                current(attempt = attempt, activeUndo = ActiveUndo(UNDO_N, 1L))
            coEvery { snapshotProvider.deleteUndo(UNDO_N) } throws IOException("delete failed")

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, outcome)
            coVerifyOrder {
                restoreStateRepository.finalizeAttempt(ROLLBACK_OWNER, any(), any())
                appDialogPublisher.publish(any())
                snapshotProvider.deleteUndo(UNDO_N)
            }

            val resolved = current()
            coEvery { restoreStateRepository.readProtocol() } returns resolved
            coordinator.sweepRecoveryGarbage()

            coVerify(exactly = 1) { snapshotProvider.sweepRecoveryFiles(resolved.state) }
        }

    @Test
    fun `rollback finalization failure preserves exact source and publishes nothing`() = runTest {
        val attempt = rollbackAttempt(phase = RestoreAttempt.Phase.Committed)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        coEvery { restoreStateRepository.finalizeAttempt(any(), any(), any()) } returns false

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.FinalizationPending, outcome)
        coVerify(exactly = 0) { snapshotProvider.deleteUndo(UNDO_N) }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `committed rollback publication failure stays pending after durable finalization`() =
        runTest {
            val attempt = rollbackAttempt(phase = RestoreAttempt.Phase.Committed)
            coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
            coEvery { appDialogPublisher.publish(any()) } throws IOException("dialog store failed")

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.FinalizationPending, outcome)
            coVerify(exactly = 1) {
                restoreStateRepository.finalizeAttempt(ROLLBACK_OWNER, any(), any())
            }
            coVerify(exactly = 1) { snapshotProvider.deleteUndo(UNDO_N) }
            assertTrue(coordinator.recoverySurfaceRequired)
        }

    @Test
    fun `committed rollback whose live generation cannot verify remains unresolved`() = runTest {
        val attempt = rollbackAttempt(phase = RestoreAttempt.Phase.Committed)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        coEvery { snapshotProvider.currentSchemaVersion() } throws IOException("cannot open")

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { restoreStateRepository.finalizeAttempt(any(), any(), any()) }
        coVerify(exactly = 0) { snapshotProvider.deleteUndo(any()) }
    }

    @Test
    fun `Prepared rollback reclaims its exact source and owner`() = runTest {
        val attempt = rollbackAttempt(phase = RestoreAttempt.Phase.Prepared)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, any()) } coAnswers {
            val effects = secondArg<DatabaseReplacementEffects>()
            assertEquals(ROLLBACK_OWNER, effects.attemptId)
            effects.onBeforeMutation(UNDO_N, restoreSourceRef = null)
            effects.onMutationCommitted()
            DatabaseReplacementResult.Committed()
        }

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
        coVerify(exactly = 1) { restoreStateRepository.beginAttempt(attempt) }
        coVerify(exactly = 1) { restoreStateRepository.recordAttemptCommitted(ROLLBACK_OWNER) }
    }

    @Test
    fun `Committed runtime result with effects failure is never a clean recovery`() = runTest {
        val attempt = rollbackAttempt(phase = RestoreAttempt.Phase.Prepared)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, any()) } returns
            DatabaseReplacementResult.Committed(
                effectsError = BackupError.Io(IOException("journal commit failed")),
            )

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
    }

    @Test
    fun `every non-commit recovery rollback result requires terminal recovery`() = runTest {
        val attempt = rollbackAttempt(phase = RestoreAttempt.Phase.Prepared)
        coEvery { restoreStateRepository.readProtocol() } returns current(attempt = attempt)
        val failure = BackupError.Io(IOException("replacement failed"))
        val nonCommitResults = listOf(
            DatabaseReplacementResult.RejectedBeforeMutation(failure),
            DatabaseReplacementResult.RecoveredByRollback(failure),
            DatabaseReplacementResult.FailedAfterMutation(failure),
            DatabaseReplacementResult.FatalNoGeneration(),
        )

        nonCommitResults.forEach { result ->
            coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, any()) } returns result

            assertEquals(
                RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired,
                coordinator.handlePostRestoreLaunch(),
            )
        }

        coVerify(exactly = nonCommitResults.size) {
            databaseReplacement.rollbackFromUndo(UNDO_N, any())
        }
    }

    @Test
    fun `undo of current N journals exact UserUndo owner and committed phase`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } returns
            current(activeUndo = ActiveUndo(UNDO_N, 1L))
        val claimed = slot<RestoreAttempt>()
        coEvery { restoreStateRepository.beginAttempt(capture(claimed)) } returns true
        coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, any()) } coAnswers {
            val effects = secondArg<DatabaseReplacementEffects>()
            effects.onBeforeMutation(UNDO_N, restoreSourceRef = null)
            effects.onMutationCommitted()
            DatabaseReplacementResult.Committed()
        }

        val outcome = coordinator.performUndoRestore(UNDO_N)

        assertEquals(UndoRestoreOutcome.Succeeded, outcome)
        val rollback = claimed.captured as RestoreAttempt.Rollback
        assertEquals(UNDO_N, rollback.sourceRef)
        assertEquals(RestoreAttempt.RollbackOrigin.UserUndo, rollback.origin)
        assertEquals(RestoreAttempt.Phase.Prepared, rollback.phase)
        coVerify(exactly = 1) { restoreStateRepository.recordAttemptCommitted(rollback.id) }
    }

    @Test
    fun `undo confirmation for P cannot apply when active owner is N`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } returns
            current(activeUndo = ActiveUndo(UNDO_N, 1L))

        val outcome = coordinator.performUndoRestore(UNDO_P)

        assertEquals(UndoRestoreOutcome.NotCurrent, outcome)
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
    }

    @Test
    fun `same-install active N with missing file routes undo to recovery`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } returns
            current(activeUndo = ActiveUndo(UNDO_N, 1L))
        every { snapshotProvider.getUndoFile(UNDO_N) } returns null

        val outcome = coordinator.performUndoRestore(UNDO_N)

        assertEquals(UndoRestoreOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
    }

    @Test
    fun `same-install active N with missing file routes startup to recovery before outbox publish`() =
        runTest {
            val terminal = RestoreTerminal.RestoreSucceeded(
                owner = RESTORE_OWNER,
                restoredAtEpochMs = 99L,
                previousVersionAvailable = true,
            )
            coEvery { restoreStateRepository.readProtocol() } returns current(
                activeUndo = ActiveUndo(UNDO_N, 1L),
                terminalOutbox = terminal,
            )
            every { snapshotProvider.getUndoFile(UNDO_N) } returns null

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
            coVerify(exactly = 0) { restoreStateRepository.acknowledgeTerminal(any()) }
        }

    @Test
    fun `corrupt exact undo rejection abandons only Prepared rollback and requires recovery`() =
        runTest {
            coEvery { restoreStateRepository.readProtocol() } returns
                current(activeUndo = ActiveUndo(UNDO_N, 1L))
            val effectsSlot = slot<DatabaseReplacementEffects>()
            coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, capture(effectsSlot)) } coAnswers {
                val error = BackupError.CorruptedBackup("bad undo")
                effectsSlot.captured.onRejectedBeforeMutation(error)
                DatabaseReplacementResult.RejectedBeforeMutation(error)
            }

            val outcome = coordinator.performUndoRestore(UNDO_N)

            assertEquals(UndoRestoreOutcome.RecoveryRequired, outcome)
            coVerify(exactly = 1) { restoreStateRepository.discardPreparedAttempt(any()) }
        }

    @Test
    fun `IO rejection keeps active N available for retry and maps to IoFailure`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } returns
            current(activeUndo = ActiveUndo(UNDO_N, 1L))
        val effectsSlot = slot<DatabaseReplacementEffects>()
        val error = BackupError.Io(IOException("capacity query failed"))
        coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, capture(effectsSlot)) } coAnswers {
            effectsSlot.captured.onRejectedBeforeMutation(error)
            DatabaseReplacementResult.RejectedBeforeMutation(error)
        }

        val outcome = coordinator.performUndoRestore(UNDO_N)

        assertEquals(UndoRestoreOutcome.IoFailure, outcome)
        coVerify(exactly = 1) { restoreStateRepository.discardPreparedAttempt(any()) }
        coVerify(exactly = 0) { restoreStateRepository.finalizeAttempt(any(), any(), any()) }
        coVerify(exactly = 0) { snapshotProvider.deleteUndo(UNDO_N) }
    }

    @Test
    fun `undo write failure remains retryable IO and does not finalize`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } returns
            current(activeUndo = ActiveUndo(UNDO_N, 1L))
        coEvery { databaseReplacement.rollbackFromUndo(UNDO_N, any()) } returns
            DatabaseReplacementResult.FailedAfterMutation(
                BackupError.Io(IOException("ENOSPC")),
            )

        val outcome = coordinator.performUndoRestore(UNDO_N)

        assertEquals(UndoRestoreOutcome.IoFailure, outcome)
        coVerify(exactly = 0) { restoreStateRepository.finalizeAttempt(any(), any(), any()) }
    }

    @Test
    fun `legacy marker plus valid C installs synthetic attempt before deleting C`() = runTest {
        stubLegacy(
            LegacyRestoreState(
                restoreInProgress = true,
                context = CONTEXT,
                preRestoreBackupAvailable = true,
                preRestoreOriginalDateEpochMs = 123L,
            ),
            legacyUsable = true,
        )
        coEvery { databaseReplacement.rollbackFromUndo(LEGACY_INTERRUPTED_UNDO, any()) } returns
            DatabaseReplacementResult.Committed()

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
        val expected = RestoreAttempt.Restore(
            id = LegacyRestoreOwners.InterruptedAttempt,
            phase = RestoreAttempt.Phase.Prepared,
            context = CONTEXT,
            undoRef = LEGACY_INTERRUPTED_UNDO,
            sourceRef = null,
        )
        coVerifyOrder {
            snapshotProvider.migrateLegacyUndo(LEGACY_INTERRUPTED_UNDO)
            restoreStateRepository.installLegacyState(EPOCH, expected, activeUndo = null)
            snapshotProvider.deleteLegacyPreRestore()
            databaseReplacement.rollbackFromUndo(LEGACY_INTERRUPTED_UNDO, any())
        }
    }

    @Test
    fun `legacy valid C recovers even when live db is invalid`() = runTest {
        stubLegacy(
            LegacyRestoreState(true, CONTEXT, false, null),
            legacyUsable = true,
        )
        coEvery { snapshotProvider.inspectLiveDatabaseWithoutRoom() } returns
            BackupResult.Failure(BackupError.CorruptedBackup("bad live"))

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
        coVerify(exactly = 1) {
            databaseReplacement.rollbackFromUndo(LEGACY_INTERRUPTED_UNDO, any())
        }
        coVerify(exactly = 0) { snapshotProvider.inspectLiveDatabaseWithoutRoom() }
    }

    @Test
    fun `legacy marker with missing C and healthy live db enters acceptance flow`() = runTest {
        stubLegacy(
            LegacyRestoreState(true, CONTEXT, true, 123L),
            legacyUsable = false,
        )

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.InterruptedRestore, outcome)
        coVerify(exactly = 1) {
            restoreStateRepository.installLegacyState(
                EPOCH,
                RestoreAttempt.Restore(
                    id = LegacyRestoreOwners.InterruptedAttempt,
                    phase = RestoreAttempt.Phase.Prepared,
                    context = CONTEXT,
                    undoRef = null,
                    sourceRef = null,
                ),
                activeUndo = null,
            )
        }
        coVerify(exactly = 0) { snapshotProvider.deleteLegacyPreRestore() }
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
    }

    @Test
    fun `legacy marker with neither usable live db nor C requires recovery`() = runTest {
        stubLegacy(
            LegacyRestoreState(true, CONTEXT, false, null),
            legacyUsable = false,
        )
        coEvery { snapshotProvider.inspectLiveDatabaseWithoutRoom() } returns
            BackupResult.Failure(BackupError.CorruptedBackup("bad live"))

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
    }

    @Test
    fun `legacy availability plus valid C becomes active pointer with original date`() = runTest {
        val originalDate = 987_654_321L
        stubLegacy(
            LegacyRestoreState(false, null, true, originalDate),
            legacyUsable = true,
        )

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
        coVerifyOrder {
            snapshotProvider.migrateLegacyUndo(LEGACY_ACTIVE_UNDO)
            restoreStateRepository.installLegacyState(
                EPOCH,
                attempt = null,
                activeUndo = ActiveUndo(LEGACY_ACTIVE_UNDO, originalDate),
            )
            snapshotProvider.deleteLegacyPreRestore()
        }
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
    }

    @Test
    fun `legacy availability with unusable C clears stale pointer`() = runTest {
        stubLegacy(
            LegacyRestoreState(false, null, true, 123L),
            legacyUsable = false,
        )

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
        coVerify(exactly = 1) {
            restoreStateRepository.installLegacyState(EPOCH, attempt = null, activeUndo = null)
        }
        coVerify(exactly = 0) { snapshotProvider.migrateLegacyUndo(any()) }
    }

    @Test
    fun `legacy availability with usable C but missing date clears stale pointer`() = runTest {
        stubLegacy(
            LegacyRestoreState(false, null, true, null),
            legacyUsable = true,
        )

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
        coVerify(exactly = 1) {
            restoreStateRepository.installLegacyState(EPOCH, attempt = null, activeUndo = null)
        }
        coVerify(exactly = 0) { snapshotProvider.validateLegacyUndo() }
        coVerify(exactly = 0) { snapshotProvider.migrateLegacyUndo(any()) }
        coVerify(exactly = 1) { snapshotProvider.deleteLegacyPreRestore() }
    }

    @Test
    fun `legacy unowned C without marker or availability is never guessed into a pointer`() =
        runTest {
            stubLegacy(
                LegacyRestoreState(false, null, false, null),
                legacyUsable = true,
            )

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
            coVerify(exactly = 1) {
                restoreStateRepository.installLegacyState(EPOCH, attempt = null, activeUndo = null)
            }
            coVerify(exactly = 0) { snapshotProvider.migrateLegacyUndo(any()) }
            coVerify(exactly = 1) { snapshotProvider.deleteLegacyPreRestore() }
        }

    @Test
    fun `legacy replay durability-syncs already-published immutable undo`() =
        runTest {
            stubLegacy(
                LegacyRestoreState(true, CONTEXT, false, null),
                legacyUsable = false,
            )
            every { snapshotProvider.getUndoFile(LEGACY_INTERRUPTED_UNDO) } returns
                File("undo_${LegacyRestoreOwners.InterruptedAttempt}.db")
            coEvery { snapshotProvider.validateUndo(LEGACY_INTERRUPTED_UNDO) } returns
                BackupResult.Success(Unit)

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
            coVerify(exactly = 0) { snapshotProvider.validateLegacyUndo() }
            coVerify(exactly = 1) { snapshotProvider.migrateLegacyUndo(LEGACY_INTERRUPTED_UNDO) }
            coVerify(exactly = 1) {
                restoreStateRepository.installLegacyState(EPOCH, any(), activeUndo = null)
            }
        }

    @Test
    fun `legacy state-write failure preserves C and never starts rollback`() = runTest {
        stubLegacy(
            LegacyRestoreState(true, CONTEXT, false, null),
            legacyUsable = true,
        )
        coEvery { restoreStateRepository.installLegacyState(any(), any(), any()) } returns false

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { snapshotProvider.deleteLegacyPreRestore() }
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
    }

    @Test
    fun `legacy publication durability failure preserves C and protocol state`() = runTest {
        stubLegacy(
            LegacyRestoreState(true, CONTEXT, false, null),
            legacyUsable = true,
        )
        coEvery { snapshotProvider.migrateLegacyUndo(LEGACY_INTERRUPTED_UNDO) } returns
            BackupResult.Failure(BackupError.Io(IOException("directory fsync failed")))
        every { snapshotProvider.getUndoFile(LEGACY_INTERRUPTED_UNDO) } returns
            File("undo_${LegacyRestoreOwners.InterruptedAttempt}.db")
        coEvery { snapshotProvider.validateUndo(LEGACY_INTERRUPTED_UNDO) } returns
            BackupResult.Success(Unit)

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { restoreStateRepository.installLegacyState(any(), any(), any()) }
        coVerify(exactly = 0) { snapshotProvider.deleteLegacyPreRestore() }
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
    }

    @Test
    fun `legacy state-write exception preserves C and immutable undo without crashing`() = runTest {
        stubLegacy(
            LegacyRestoreState(true, CONTEXT, false, null),
            legacyUsable = true,
        )
        coEvery { restoreStateRepository.installLegacyState(any(), any(), any()) } throws
            IOException("ENOSPC")

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        coVerify(exactly = 0) { snapshotProvider.deleteLegacyPreRestore() }
        coVerify(exactly = 0) { snapshotProvider.deleteUndo(LEGACY_INTERRUPTED_UNDO) }
        coVerify(exactly = 0) { databaseReplacement.rollbackFromUndo(any(), any()) }
    }

    @Test
    fun `legacy C deletion failure retries after installed state has resolved`() = runTest {
        val legacy = LegacyRestoreState(true, CONTEXT, false, null)
        val resolved = current()
        every { snapshotProvider.getUndoFile(LEGACY_INTERRUPTED_UNDO) } returns null
        coEvery { snapshotProvider.validateLegacyUndo() } returns BackupResult.Success(Unit)
        coEvery { snapshotProvider.migrateLegacyUndo(LEGACY_INTERRUPTED_UNDO) } returns
            BackupResult.Success(File("undo_${LegacyRestoreOwners.InterruptedAttempt}.db"))
        coEvery { restoreStateRepository.readProtocol() } returnsMany listOf(
            RestoreProtocolRead.Legacy(EPOCH, legacy),
            resolved,
            resolved,
        )
        coEvery { snapshotProvider.deleteLegacyPreRestore() } returnsMany listOf(false, true)

        assertEquals(
            RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack,
            coordinator.handlePostRestoreLaunch(),
        )
        assertEquals(
            RestoreRecoveryCoordinator.PreflightOutcome.NoOp,
            coordinator.handlePostRestoreLaunch(),
        )
        coVerify(exactly = 1) { snapshotProvider.migrateLegacyUndo(LEGACY_INTERRUPTED_UNDO) }
        coVerify(exactly = 1) { restoreStateRepository.installLegacyState(EPOCH, any(), null) }
        coVerify(exactly = 2) { snapshotProvider.deleteLegacyPreRestore() }
    }

    @Test
    fun `recovery export failure cannot replace a RecoveryRequired verdict`() = runTest {
        coEvery { restoreStateRepository.readProtocol() } returns
            RestoreProtocolRead.Corrupt(EPOCH, "bad owner state")
        coEvery { snapshotProvider.preserveDbBeforeMigration() } returns
            BackupResult.Failure(BackupError.Io(IOException("ENOSPC")))

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        assertEquals(RecoveryExportOutcome.Failed, coordinator.lastRecoveryExportOutcome)
        assertTrue(coordinator.recoverySurfaceRequired)
    }

    @Test
    fun `restartApp delegates only to the platform reinitializer`() {
        coordinator.restartApp()

        verify(exactly = 1) { appReinitializer.reinitialize() }
    }

    private fun stubLegacy(
        legacy: LegacyRestoreState,
        legacyUsable: Boolean,
    ) {
        every { snapshotProvider.getUndoFile(LEGACY_INTERRUPTED_UNDO) } returns null
        every { snapshotProvider.getUndoFile(LEGACY_ACTIVE_UNDO) } returns null
        coEvery { restoreStateRepository.readProtocol() } returns
            RestoreProtocolRead.Legacy(EPOCH, legacy)
        coEvery { snapshotProvider.validateLegacyUndo() } returns if (legacyUsable) {
            BackupResult.Success(Unit)
        } else {
            BackupResult.Failure(BackupError.CorruptedBackup("legacy C missing"))
        }
        coEvery { snapshotProvider.migrateLegacyUndo(any()) } returns
            BackupResult.Success(File("migrated-legacy-undo.db"))
    }

    private fun makeCoordinator(): RestoreRecoveryCoordinator = RestoreRecoveryCoordinator(
        appReinitializer = appReinitializer,
        platformInfo = platformInfo,
        snapshotProvider = snapshotProvider,
        databaseReplacement = databaseReplacement,
        restoreStateRepository = restoreStateRepository,
        appDialogPublisher = appDialogPublisher,
        reporter = reporter,
    )

    private fun current(
        attempt: RestoreAttempt? = null,
        activeUndo: ActiveUndo? = null,
        terminalOutbox: RestoreTerminal? = null,
    ): RestoreProtocolRead.Current = RestoreProtocolRead.Current(
        RestoreProtocolState(
            installEpoch = EPOCH,
            attempt = attempt,
            activeUndo = activeUndo,
            terminalOutbox = terminalOutbox,
        ),
    )

    private fun restoreAttempt(
        phase: RestoreAttempt.Phase,
        undoRef: UndoRef? = UNDO_N,
        sourceRef: RestoreSourceRef? = RestoreSourceRef(RESTORE_OWNER),
    ): RestoreAttempt.Restore = RestoreAttempt.Restore(
        id = RESTORE_OWNER,
        phase = phase,
        context = CONTEXT,
        undoRef = undoRef,
        sourceRef = sourceRef,
    )

    private fun rollbackAttempt(
        sourceRef: UndoRef = UNDO_N,
        origin: RestoreAttempt.RollbackOrigin = RestoreAttempt.RollbackOrigin.ScenarioOneRecovery,
        phase: RestoreAttempt.Phase,
    ): RestoreAttempt.Rollback = RestoreAttempt.Rollback(
        id = ROLLBACK_OWNER,
        phase = phase,
        sourceRef = sourceRef,
        origin = origin,
    )

    private companion object {
        val EPOCH = InstallEpoch(owner(900))
        val RESTORE_OWNER = owner(1)
        val ROLLBACK_OWNER = owner(2)
        val UNDO_N = UndoRef(RESTORE_OWNER)
        val UNDO_P = UndoRef(owner(3))
        val LEGACY_INTERRUPTED_UNDO = UndoRef(LegacyRestoreOwners.InterruptedAttempt)
        val LEGACY_ACTIVE_UNDO = UndoRef(LegacyRestoreOwners.ActiveUndo)
        val CONTEXT = RestoreInProgressContext(
            backupSchemaVersion = APP_DATABASE_VERSION,
            backupCreatedAtEpochMs = 1_700_000_000_000L,
            backupAppVersion = "1.2.2",
            startedAtEpochMs = 1_700_000_100_000L,
        )

        fun owner(suffix: Int): RestoreOwnerId = RestoreOwnerId(
            "30000000-0000-4000-8000-${suffix.toString().padStart(12, '0')}",
        )
    }
}
