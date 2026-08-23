// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
        // Relaxed would return false anyway — explicit because the journal gate is load-bearing.
        coEvery { restoreStateRepository.isRestoreMutationInterrupted() } returns false
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

    /** Real transaction shape: the seam runs the typed effects' onCommitted, then commits. */
    private fun stubRollbackCommitted() {
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } coAnswers {
            firstArg<DatabaseReplacementEffects>().onCommitted()
            DatabaseReplacementResult.Committed()
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
            // The flag clears + dialog publish ride the transaction's onCommitted effect — they
            // ran because the stub invoked the effects object, proving the coordinator PASSES
            // them as effects (not as post-await code an initiator's death could strand).
            coVerify(exactly = 1) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            val publishedSlot = slot<AppDialog>()
            coVerify { appDialogPublisher.publish(capture(publishedSlot)) }
            assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
        }

    @Test
    fun `scenario 1 rollback FailedAfterMutation preserves EVERY asset and marker`() =
        runTest {
            // Round-2 mandate 4: the old defensive delete-on-FailedAfterMutation branch is
            // REMOVED. A rollback that failed after mutation leaves `restore_in_progress` set
            // and `pre_restore_backup.db` on disk, so the NEXT launch re-enters the pre-flight
            // and retries. The caller deletes/clears NOTHING — and must NOT restart (the
            // rollback is still pending; restarting would boot-loop silently forever): the
            // outcome is RecoveryRetryPending, and the user gets a truthful failure dialog.
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

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRetryPending, outcome)
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 0) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            val publishedSlot = slot<AppDialog>()
            coVerify(exactly = 1) { appDialogPublisher.publish(capture(publishedSlot)) }
            assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
        }

    @Test
    fun `scenario 1 rollback RejectedBeforeMutation preserves EVERY asset and marker`() =
        runTest {
            // Finding 4: a pre-mutation rejection (a graph-only transition holds the machine)
            // means NOTHING was mutated — a cold start or a later transaction completes this
            // rollback, so the coordinator must not delete the file or clear the markers; the
            // user still gets truthful feedback and no restart loop (RecoveryRetryPending).
            val context = makeContext()
            coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
            coEvery {
                snapshotProvider.currentSchemaVersion()
            } throws IllegalStateException("migration crashed")
            coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } returns
                DatabaseReplacementResult.RejectedBeforeMutation(
                    BackupError.Io(java.io.IOException("machine busy")),
                )

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRetryPending, outcome)
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 0) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
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
    fun `journal-interrupted mutation routes straight to the failure path - no schema peek, never RestoreSuccess`() =
        runTest {
            // Spec §8.4: an interrupted-mutation journal entry means the swap failed after the
            // point of no return — a schema peek could SUCCEED against the untouched OLD file
            // and produce a false "restore succeeded". The journal must route straight to the
            // rollback path without ever consulting the schema.
            val context = makeContext()
            coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
            coEvery { restoreStateRepository.isRestoreMutationInterrupted() } returns true
            stubRollbackCommitted()

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
            coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
            coVerify(exactly = 1) { databaseReplacement.rollbackToPreRestoreBackup(any()) }
            // The committed-effects stub publishes exactly one dialog — the FAILURE one; a
            // RestoreSuccess here would be the exact lie the journal exists to prevent.
            val publishedSlot = slot<AppDialog>()
            coVerify(exactly = 1) { appDialogPublisher.publish(capture(publishedSlot)) }
            assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
        }

    @Test
    fun `recovery path survives a process restart after FailedAfterMutation`() = runTest {
        // PROCESS-RESTART gate: launch 1's rollback fails after mutation → every durable asset
        // survives in the (stateful, shared) repository; launch 2 re-enters the pre-flight over
        // the SAME state and completes the rollback.
        val fakeRepository = FakeRestoreStateRepository(initialContext = makeContext())
        coEvery {
            snapshotProvider.currentSchemaVersion()
        } throws IllegalStateException("migration crashed")
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } returns
            DatabaseReplacementResult.FailedAfterMutation(
                BackupError.Io(java.io.IOException("disk full")),
            )

        val firstLaunch = makeCoordinator(fakeRepository)
        val firstOutcome = firstLaunch.handlePostRestoreLaunch()

        // Nothing consumed: the in-progress context is still there for the next launch; the
        // failed attempt gave the user a truthful failure dialog and did NOT restart (a restart
        // with the rollback uncommitted would boot-loop silently forever).
        assertEquals(
            RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRetryPending,
            firstOutcome,
        )
        assertNotNull(fakeRepository.getRestoreInProgressContext())
        coVerify(exactly = 1) { appDialogPublisher.publish(any()) }

        // Simulated restart: a NEW coordinator over the SAME durable state; this time the
        // rollback transaction commits and runs the typed effects.
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any()) } coAnswers {
            firstArg<DatabaseReplacementEffects>().onCommitted()
            DatabaseReplacementResult.Committed()
        }
        val secondLaunch = makeCoordinator(fakeRepository)
        val outcome = secondLaunch.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
        assertNull(fakeRepository.getRestoreInProgressContext())
        val published = mutableListOf<AppDialog>()
        coVerify(exactly = 2) { appDialogPublisher.publish(capture(published)) }
        assertTrue(published.all { it is AppDialog.RestoreFailure })
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
    fun `performUndoRestore happy path - clear, publish, THEN caller acknowledge, in order`() =
        runTest {
            every { snapshotProvider.getPreRestoreBackupFile() } returns java.io.File("pre_restore_backup.db")
            // Order recorder across ALL three writes — the acknowledge's POSITION is part of
            // the dismiss-after discipline (a dismiss before the success dialog persists would
            // lose the dialog across a crash between the two).
            val order = mutableListOf<String>()
            coEvery { restoreStateRepository.clearPreRestoreBackupAvailable() } coAnswers {
                order += "clear"
            }
            coEvery { appDialogPublisher.publish(AppDialog.UndoRestoreSuccess) } coAnswers {
                order += "publish"
            }
            stubRollbackCommitted()

            val result = coordinator.performUndoRestore(onCommitted = { order += "acknowledge" })

            assertEquals(UndoRestoreOutcome.Succeeded, result)
            assertEquals(
                listOf("clear", "publish", "acknowledge"),
                order,
                "side-effect-first inside onCommitted; the caller's acknowledge comes LAST",
            )
        }

    @Test
    fun `undo compensation is PACKAGED in the effects object - nothing runs post-await`() =
        runTest {
            // The discriminator for mandate 2 (an effects-invoking stub alone cannot tell
            // "packaged in effects" from "post-await code gated on Committed"): the seam stub
            // CAPTURES the effects WITHOUT invoking them and returns Committed. If the
            // coordinator ran its writes post-await, they would appear now — they must not.
            every { snapshotProvider.getPreRestoreBackupFile() } returns java.io.File("pre_restore_backup.db")
            val effectsSlot = slot<DatabaseReplacementEffects>()
            coEvery { databaseReplacement.rollbackToPreRestoreBackup(capture(effectsSlot)) } returns
                DatabaseReplacementResult.Committed()
            var callerAcknowledged = false

            val result = coordinator.performUndoRestore(onCommitted = { callerAcknowledged = true })

            assertEquals(UndoRestoreOutcome.Succeeded, result)
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
            assertTrue(!callerAcknowledged, "nothing may run outside the transaction's effects")

            // The writes live INSIDE the captured effects — the transaction coroutine (which
            // survives the initiator's death) is what executes them.
            effectsSlot.captured.onCommitted()
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 1) { appDialogPublisher.publish(AppDialog.UndoRestoreSuccess) }
            assertTrue(callerAcknowledged)
        }

    @Test
    fun `scenario-1 compensation is PACKAGED in the effects object - nothing runs post-await`() =
        runTest {
            val context = makeContext()
            coEvery { restoreStateRepository.getRestoreInProgressContext() } returns context
            coEvery {
                snapshotProvider.currentSchemaVersion()
            } throws IllegalStateException("migration crashed")
            val effectsSlot = slot<DatabaseReplacementEffects>()
            coEvery { databaseReplacement.rollbackToPreRestoreBackup(capture(effectsSlot)) } returns
                DatabaseReplacementResult.Committed()

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
            coVerify(exactly = 0) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }

            effectsSlot.captured.onCommitted()
            coVerify(exactly = 1) { restoreStateRepository.clearRestoreInProgress() }
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            val publishedSlot = slot<AppDialog>()
            coVerify(exactly = 1) { appDialogPublisher.publish(capture(publishedSlot)) }
            assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
        }

    @Test
    fun `restartApp delegates to the AppReinitializer seam`() {
        coordinator.restartApp()

        verify(exactly = 1) { appReinitializer.reinitialize() }
    }

    private fun makeCoordinator(
        stateRepository: RestoreStateRepository,
    ): RestoreRecoveryCoordinator = RestoreRecoveryCoordinator(
        appReinitializer = appReinitializer,
        platformInfo = platformInfo,
        snapshotProvider = snapshotProvider,
        databaseReplacement = databaseReplacement,
        restoreStateRepository = stateRepository,
        appDialogPublisher = appDialogPublisher,
        reporter = reporter,
    )

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

    /**
     * Stateful in-memory [RestoreStateRepository] for the process-restart gate: two coordinator
     * instances (two "launches") share this object the way two processes share DataStore.
     */
    private class FakeRestoreStateRepository(
        initialContext: RestoreInProgressContext?,
    ) : RestoreStateRepository {

        private var context: RestoreInProgressContext? = initialContext
        private var backupAvailable: Boolean = true
        private var mutationInterrupted: Boolean = false

        override suspend fun markRestoreInProgress(context: RestoreInProgressContext) {
            this.context = context
        }

        override suspend fun getRestoreInProgressContext(): RestoreInProgressContext? = context

        override suspend fun clearRestoreInProgress() {
            context = null
            mutationInterrupted = false
        }

        override suspend fun markRestoreMutationInterrupted() {
            mutationInterrupted = true
        }

        override suspend fun isRestoreMutationInterrupted(): Boolean = mutationInterrupted

        override suspend fun markPreRestoreBackupAvailable(originalDataDateEpochMs: Long) {
            backupAvailable = true
        }

        override suspend fun clearPreRestoreBackupAvailable() {
            backupAvailable = false
        }

        override fun observePreRestoreBackupAvailable(): Flow<Boolean> = flowOf(backupAvailable)

        override suspend fun getPreRestoreOriginalDate(): Long? = null
    }
}
