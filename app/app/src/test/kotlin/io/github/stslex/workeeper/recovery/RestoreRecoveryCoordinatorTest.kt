// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.recovery

import android.content.Context
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RestoreRecoveryCoordinatorTest {

    private val androidContext = mockk<Context>(relaxed = true)
    private val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val restoreStateRepository = mockk<RestoreStateRepository>(relaxed = true)
    private val appDialogPublisher = mockk<AppDialogPublisher>(relaxed = true)
    private val reporter = mockk<RestoreRecoveryReporter>(relaxed = true)

    private lateinit var coordinator: RestoreRecoveryCoordinator

    @BeforeEach
    fun setUp() {
        every { androidContext.packageName } returns "io.github.stslex.workeeper"
        coordinator = RestoreRecoveryCoordinator(
            context = androidContext,
            snapshotProvider = snapshotProvider,
            restoreStateRepository = restoreStateRepository,
            appDialogPublisher = appDialogPublisher,
            reporter = reporter,
        )
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
    fun `handlePostRestoreLaunch with migration throw rolls back and publishes failure`() =
        runTest {
            val context = makeContext()
            coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
            val cause = IllegalStateException("migration crashed")
            coEvery { snapshotProvider.currentSchemaVersion() } throws cause
            coEvery {
                snapshotProvider.rollbackToPreRestoreBackup()
            } returns BackupResult.Success(Unit)

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
            coVerify(exactly = 1) {
                reporter.recordRestoreTimeFailure(
                    exception = cause,
                    context = context,
                    appVersionName = any(),
                )
            }
            coVerify(exactly = 1) { snapshotProvider.rollbackToPreRestoreBackup() }
            coVerify(exactly = 1) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            val publishedSlot = slot<AppDialog>()
            coVerify { appDialogPublisher.publish(capture(publishedSlot)) }
            assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
        }

    @Test
    fun `handlePostRestoreLaunch with migration throw and rollback failure still clears flags`() =
        runTest {
            val context = makeContext()
            coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
            coEvery {
                snapshotProvider.currentSchemaVersion()
            } throws IllegalStateException("migration crashed")
            coEvery {
                snapshotProvider.rollbackToPreRestoreBackup()
            } returns BackupResult.Failure(BackupError.Io(java.io.IOException("disk full")))

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
            // Defence in depth: even if rollback fails, the flag set is cleared and
            // the preserved file is deleted so the next launch starts clean.
            coVerify(exactly = 1) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 1) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        }

    @Test
    fun `performUndoRestore returns false when no preserved backup exists`() = runTest {
        every { snapshotProvider.hasPreRestoreBackup() } returns false

        val result = coordinator.performUndoRestore()

        assertFalse(result)
        coVerify(exactly = 0) { snapshotProvider.rollbackToPreRestoreBackup() }
        coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `performUndoRestore returns false when rollback file swap fails`() = runTest {
        every { snapshotProvider.hasPreRestoreBackup() } returns true
        coEvery {
            snapshotProvider.rollbackToPreRestoreBackup()
        } returns BackupResult.Failure(BackupError.Io(java.io.IOException("disk full")))

        val result = coordinator.performUndoRestore()

        assertFalse(result)
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `performUndoRestore happy path swaps clears marker and publishes UndoRestoreSuccess`() =
        runTest {
            every { snapshotProvider.hasPreRestoreBackup() } returns true
            coEvery {
                snapshotProvider.rollbackToPreRestoreBackup()
            } returns BackupResult.Success(Unit)

            val result = coordinator.performUndoRestore()

            assertTrue(result)
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 1) { appDialogPublisher.publish(AppDialog.UndoRestoreSuccess) }
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
