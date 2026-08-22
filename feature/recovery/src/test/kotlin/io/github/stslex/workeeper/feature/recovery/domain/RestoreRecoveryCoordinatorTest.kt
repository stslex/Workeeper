// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RestoreRecoveryCoordinatorTest {

    private val appReinitializer = mockk<AppReinitializer>(relaxed = true)
    private val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val databaseReplacement = mockk<DatabaseReplacement>(relaxed = true)
    private val restoreStateRepository = mockk<RestoreStateRepository>(relaxed = true)
    private val appDialogPublisher = mockk<AppDialogPublisher>(relaxed = true)
    private val reporter = mockk<RestoreRecoveryReporter>(relaxed = true)
    private val platformInfo = mockk<PlatformInfoProvider>(relaxed = true)

    private lateinit var coordinator: RestoreRecoveryCoordinator

    @BeforeEach
    fun setUp() {
        coordinator = RestoreRecoveryCoordinator(
            appReinitializer = appReinitializer,
            platformInfo = platformInfo,
            snapshotProvider = snapshotProvider,
            databaseReplacement = databaseReplacement,
            restoreStateRepository = restoreStateRepository,
            appDialogPublisher = appDialogPublisher,
            reporter = reporter,
        )
    }

    /** Real transaction shape: the seam runs the caller's onCommitted hook, then commits. */
    private fun stubRollbackCommitted() {
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
            DatabaseReplacementResult.Committed
        }
    }

    @Test
    fun `handlePostRestoreLaunch with no in-progress restore is a no-op`() = runTest {
        coEvery { restoreStateRepository.getRestoreInProgressContext() } returns null
        val outcome = coordinator.handlePostRestoreLaunch()
        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `handlePostRestoreLaunch with valid migration clears flag and publishes success`() =
        runTest {
            val context = makeContext(startedAt = 1_700_000_000_000L)
            coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
            coEvery { snapshotProvider.currentSchemaVersion() } returns 6

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded, outcome)
            coVerify(exactly = 1) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 1) {
                restoreStateRepository.markPreRestoreBackupAvailable(1_700_000_000_000L)
            }
            val publishedSlot = slot<AppDialog>()
            coVerify { appDialogPublisher.publish(capture(publishedSlot)) }
            assertTrue(publishedSlot.captured is AppDialog.RestoreSuccess)
            assertTrue(
                (publishedSlot.captured as AppDialog.RestoreSuccess).previousVersionAvailable,
            )
        }

    @Test
    fun `handlePostRestoreLaunch with migration throw rolls back and publishes failure via the hook`() =
        runTest {
            val context = makeContext()
            coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
            val cause = IllegalStateException("migration crashed")
            coEvery { snapshotProvider.currentSchemaVersion() } throws cause
            stubRollbackCommitted()

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
            coVerify(exactly = 1) {
                reporter.recordRestoreTimeFailure(
                    exception = cause,
                    context = context,
                    appVersionName = any(),
                )
            }
            coVerify(exactly = 1) { databaseReplacement.rollbackToPreRestoreBackup(any()) }
            // The flag clears + dialog publish ride the transaction's onCommitted hook — they ran
            // because the stub invoked the hook, proving the coordinator PASSES them as the hook
            // (not as post-await code an initiator's death could strand).
            coVerify(exactly = 1) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            val publishedSlot = slot<AppDialog>()
            coVerify { appDialogPublisher.publish(capture(publishedSlot)) }
            assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
        }

    @Test
    fun `scenario 1 rollback FailedAfterMutation keeps shipped defensive cleanup`() =
        runTest {
            val context = makeContext()
            coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
            coEvery {
                snapshotProvider.currentSchemaVersion()
            } throws IllegalStateException("migration crashed")
            coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } returns
                DatabaseReplacementResult.FailedAfterMutation(
                    BackupError.Io(java.io.IOException("disk full")),
                )

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
            // Shipped defence in depth, preserved: the rollback ATTEMPT genuinely failed after
            // mutation — clear the flag set and delete the preserved file so the next launch
            // starts clean, and publish the failure dialog.
            coVerify(exactly = 1) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 1) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            val publishedSlot = slot<AppDialog>()
            coVerify { appDialogPublisher.publish(capture(publishedSlot)) }
            assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
        }

    @Test
    fun `scenario 1 rollback RejectedBeforeMutation preserves EVERY asset and marker`() =
        runTest {
            // Finding 4: a pre-mutation rejection (close failed, graph-only transition holds the
            // machine) means NOTHING was mutated — a cold start or a later transaction completes
            // this rollback, so the coordinator must not delete the file, clear the markers, or
            // claim failure to the user.
            val context = makeContext()
            coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
            coEvery {
                snapshotProvider.currentSchemaVersion()
            } throws IllegalStateException("migration crashed")
            coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } returns
                DatabaseReplacementResult.RejectedBeforeMutation(
                    BackupError.Io(java.io.IOException("close rejected")),
                )

            coordinator.handlePostRestoreLaunch()

            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 0) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        }

    @Test
    fun `scenario 1 rollback FatalNoGeneration deletes nothing`() = runTest {
        val context = makeContext()
        coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
        coEvery {
            snapshotProvider.currentSchemaVersion()
        } throws IllegalStateException("migration crashed")
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } returns
            DatabaseReplacementResult.FatalNoGeneration

        coordinator.handlePostRestoreLaunch()

        // Terminal runtime: whatever assets survived the ladder are the recovery path for the
        // NEXT process — this caller must not consume them.
        coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
        coVerify(exactly = 0) { restoreStateRepository.clearRestoreInProgress() }
        coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
    }

    @Test
    fun `performUndoRestore returns FileMissing when no preserved backup exists`() = runTest {
        every { snapshotProvider.getPreRestoreBackupFile() } returns null

        val result = coordinator.performUndoRestore()

        assertEquals(UndoRestoreOutcome.FileMissing, result)
        coVerify(exactly = 0) { databaseReplacement.rollbackToPreRestoreBackup(any()) }
        coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `performUndoRestore FailedAfterMutation maps to IoFailure and deletes nothing`() = runTest {
        every { snapshotProvider.getPreRestoreBackupFile() } returns java.io.File("pre_restore_backup.db")
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } returns
            DatabaseReplacementResult.FailedAfterMutation(
                BackupError.Io(java.io.IOException("disk full")),
            )

        val result = coordinator.performUndoRestore()

        assertEquals(UndoRestoreOutcome.IoFailure, result)
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        // Post-mutation failure: the recovery assets belong to the runtime's ladder — the
        // coordinator must not clear the marker (nor delete anything).
        coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
    }

    @Test
    fun `performUndoRestore RejectedBeforeMutation maps to IoFailure and keeps the retry slot`() =
        runTest {
            every { snapshotProvider.getPreRestoreBackupFile() } returns java.io.File("pre_restore_backup.db")
            coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } returns
                DatabaseReplacementResult.RejectedBeforeMutation(
                    BackupError.Io(java.io.IOException("lease drain timed out")),
                )

            val result = coordinator.performUndoRestore()

            // pre_restore_backup.db is untouched and pre_restore_backup_available stays set, so
            // the user can re-tap the dialog or retry from Settings → "Revert last restore".
            assertEquals(UndoRestoreOutcome.IoFailure, result)
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        }

    @Test
    fun `performUndoRestore happy path runs state and dialog writes before the caller hook`() =
        runTest {
            every { snapshotProvider.getPreRestoreBackupFile() } returns java.io.File("pre_restore_backup.db")
            stubRollbackCommitted()
            var callerHookRan = false

            val result = coordinator.performUndoRestore(onCommitted = { callerHookRan = true })

            assertEquals(UndoRestoreOutcome.Succeeded, result)
            assertTrue(callerHookRan, "the caller's onCommitted must ride the transaction hook")
            // Side-effect-first INSIDE the hook: undo state + success dialog precede the caller's
            // acknowledge (the callerHookRan flag), preserving the dismiss-after discipline.
            coVerifyOrder {
                restoreStateRepository.clearPreRestoreBackupAvailable()
                appDialogPublisher.publish(AppDialog.UndoRestoreSuccess)
            }
        }

    @Test
    fun `restartApp delegates to the AppReinitializer seam`() {
        coordinator.restartApp()

        verify(exactly = 1) { appReinitializer.reinitialize() }
    }

    private fun makeContext(
        backupSchemaVersion: Int = 5,
        backupCreatedAt: Long = 1_690_000_000_000L,
        backupAppVersion: String = "1.0.0",
        startedAt: Long = 1_700_000_000_000L,
    ): RestoreInProgressContext = RestoreInProgressContext(
        backupSchemaVersion = backupSchemaVersion,
        backupCreatedAtEpochMs = backupCreatedAt,
        backupAppVersion = backupAppVersion,
        startedAtEpochMs = startedAt,
    )
}
