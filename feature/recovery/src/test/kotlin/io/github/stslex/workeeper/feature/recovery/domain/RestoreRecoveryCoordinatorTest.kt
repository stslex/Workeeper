// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.io.IOException

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
        // GUARD: stub these Booleans explicitly — a relaxed mock answers false, which the
        // effects' check(...) calls read as "someone else owns the journal slot".
        coEvery { restoreStateRepository.getAttempt() } returns null
        coEvery { restoreStateRepository.beginAttempt(any()) } returns true
        coEvery { restoreStateRepository.recordAttemptCommitted(any()) } returns true
        coEvery { restoreStateRepository.resolveAttempt(any()) } returns true
        coordinator = makeCoordinator(restoreStateRepository)
    }

    @Test
    fun `handlePostRestoreLaunch with no unresolved attempt is a no-op`() = runTest {
        coEvery { restoreStateRepository.getAttempt() } returns null

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        coVerify(exactly = 0) { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) }
        assertFalse(coordinator.recoverySurfaceRequired)
    }

    @Test
    fun `a PREPARED attempt never peeks the schema and never claims success`() = runTest {
        // A Prepared attempt's outcome is unknown: the old database may still be on disk, so a
        // successful peek would publish a success for a restore that never happened.
        coEvery { restoreStateRepository.getAttempt() } returns
            makeAttempt(phase = RestoreAttempt.Phase.Prepared)
        stubRollbackCommitted()

        val outcome = coordinator.handlePostRestoreLaunch()

        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
        coVerify(exactly = 0) { restoreStateRepository.markPreRestoreBackupAvailable(any()) }
        val published = mutableListOf<AppDialog>()
        coVerify { appDialogPublisher.publish(capture(published)) }
        assertTrue(
            published.none { it is AppDialog.RestoreSuccess },
            "a Prepared attempt must never produce a success dialog",
        )
        assertTrue(published.any { it is AppDialog.RestoreFailure })
    }

    @Test
    fun `an interrupted but COMMITTED rollback is finished, never re-driven`() = runTest {
        // A committed rollback already consumed its snapshot; re-driving it would fail and leave
        // the attempt unresolved forever, blocking every future restore and undo.
        val attempt = makeAttempt(
            kind = RestoreAttempt.Kind.Rollback,
            phase = RestoreAttempt.Phase.Committed,
            context = null,
        )
        coEvery { restoreStateRepository.getAttempt() } returns attempt
        // The canonical file is gone (the committed rollback consumed it), so the flag clears.
        every { snapshotProvider.getPreRestoreBackupFile() } returns null
        val order = mutableListOf<String>()
        coEvery { restoreStateRepository.clearPreRestoreBackupAvailable() } coAnswers {
            order += "clear"
        }
        coEvery { restoreStateRepository.resolveAttempt(any()) } coAnswers {
            order += "resolve"
            true
        }

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, outcome)
        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        coVerify(exactly = 0) { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) }
        coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(attempt.id) }
        coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        coVerify(exactly = 0) { restoreStateRepository.markPreRestoreBackupAvailable(any()) }
        assertEquals(
            listOf("clear", "resolve"),
            order,
            "data-bearing write FIRST, resolve LAST — a death in between must replay",
        )
    }

    @Test
    fun `a committed RESERVATION-sourced rollback keeps the previous restore's still-valid undo`() =
        runTest {
            // The journal's non-null source path is the durable discriminator: A was consumed,
            // so the canonical slot B — the previous restore's undo — survives.
            val sourceA = File.createTempFile("rollback_reservation_A", ".db")
                .apply { writeText("A-EXPLICIT-SOURCE") }
            try {
                val attempt = makeAttempt(
                    kind = RestoreAttempt.Kind.Rollback,
                    phase = RestoreAttempt.Phase.Committed,
                    context = null,
                    rollbackSnapshotPath = sourceA.absolutePath,
                )
                coEvery { restoreStateRepository.getAttempt() } returns attempt
                every { snapshotProvider.getPreRestoreBackupFile() } returns
                    File("pre_restore_backup.db")

                val outcome = coordinator.handlePostRestoreLaunch()

                assertEquals(
                    RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted,
                    outcome,
                )
                assertFalse(sourceA.exists(), "the exact named source A is consumed")
                coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
                coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
                coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(attempt.id) }
            } finally {
                sourceA.delete()
            }
        }

    @Test
    fun `a committed EXPLICIT-source rollback with no canonical clears availability`() = runTest {
        // A is deleted; with no canonical behind it, availability clears.
        val sourceA = File.createTempFile("rollback_reservation_A", ".db")
            .apply { writeText("A-EXPLICIT-SOURCE") }
        try {
            val attempt = makeAttempt(
                kind = RestoreAttempt.Kind.Rollback,
                phase = RestoreAttempt.Phase.Committed,
                context = null,
                rollbackSnapshotPath = sourceA.absolutePath,
            )
            coEvery { restoreStateRepository.getAttempt() } returns attempt
            every { snapshotProvider.getPreRestoreBackupFile() } returns null

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, outcome)
            assertFalse(sourceA.exists())
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(attempt.id) }
        } finally {
            sourceA.delete()
        }
    }

    @Test
    fun `a committed CANONICAL-sourced rollback consumes the canonical - the same undo is never offered again`() =
        runTest {
            // sourcePath == null means the committed rollback applied the canonical slot, so
            // finalization consumes it from the journal, never from file existence.
            val attempt = makeAttempt(
                kind = RestoreAttempt.Kind.Rollback,
                phase = RestoreAttempt.Phase.Committed,
                context = null,
                rollbackSnapshotPath = null,
            )
            coEvery { restoreStateRepository.getAttempt() } returns attempt
            // The canonical STILL EXISTS and availability is still true — the crash shape.
            every { snapshotProvider.getPreRestoreBackupFile() } returns
                File("pre_restore_backup.db")
            val order = mutableListOf<String>()
            coEvery { snapshotProvider.deletePreRestoreBackup() } coAnswers { order += "consume" }
            coEvery { restoreStateRepository.clearPreRestoreBackupAvailable() } coAnswers {
                order += "clear"
            }
            coEvery { restoreStateRepository.resolveAttempt(any()) } coAnswers {
                order += "resolve"
                true
            }

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, outcome)
            assertEquals(
                listOf("consume", "clear", "resolve"),
                order,
                "consume the canonical, clear availability, resolve LAST",
            )

            // A second launch over the resolved journal is a plain NoOp — the undo is gone.
            coEvery { restoreStateRepository.getAttempt() } returns null
            assertEquals(
                RestoreRecoveryCoordinator.PreflightOutcome.NoOp,
                coordinator.handlePostRestoreLaunch(),
            )
        }

    @Test
    fun `a committed-rollback finalization failure keeps the journal - the replay is idempotent`() =
        runTest {
            // The availability clear throws before the resolve — the journal must stay Committed
            // so the next launch replays the whole branch idempotently.
            val attempt = makeAttempt(
                kind = RestoreAttempt.Kind.Rollback,
                phase = RestoreAttempt.Phase.Committed,
                context = null,
                rollbackSnapshotPath = null,
            )
            coEvery { restoreStateRepository.getAttempt() } returns attempt
            every { snapshotProvider.getPreRestoreBackupFile() } returns
                File("pre_restore_backup.db")
            coEvery { restoreStateRepository.clearPreRestoreBackupAvailable() } throws
                IOException("datastore write failed")

            val first = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, first)
            coVerify(exactly = 0) {
                restoreStateRepository.resolveAttempt(any())
            }

            // The replay (same journal, the write now succeeds) reaches the terminal state.
            coEvery { restoreStateRepository.clearPreRestoreBackupAvailable() } coAnswers { }
            val second = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, second)
            coVerify(exactly = 2) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(attempt.id) }
        }

    @Test
    fun `the recovery rollback CARRIES the journal's reservation path through its re-claim`() =
        runTest {
            // A Prepared attempt's reservation is the only file holding the true pre-attempt DB;
            // erasing its path would leave a later interruption with only the older canonical.
            val attempt = makeAttempt(
                phase = RestoreAttempt.Phase.Prepared,
                rollbackSnapshotPath = "/data/cache/rollback_reservation_A.db",
            )
            coEvery { restoreStateRepository.getAttempt() } returns attempt
            val claimed = slot<RestoreAttempt>()
            coEvery { restoreStateRepository.beginAttempt(capture(claimed)) } returns true
            coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } coAnswers {
                secondArg<DatabaseReplacementEffects>().onBeforeMutation("")
                DatabaseReplacementResult.Committed()
            }

            coordinator.handlePostRestoreLaunch()

            assertEquals(attempt.rollbackSnapshotPath, claimed.captured.rollbackSnapshotPath)
            assertEquals(attempt.id, claimed.captured.id)
        }

    @Test
    fun `a COMMITTED attempt recovers from the CANONICAL slot - its promotion is durably done`() =
        runTest {
            // For a Committed attempt the commit ordering proves the canonical slot holds this
            // attempt's pre-image, so the rollback is submitted with sourcePath = null.
            val attempt = makeAttempt(
                phase = RestoreAttempt.Phase.Committed,
                rollbackSnapshotPath = "/data/cache/rollback_reservation_A.db",
            )
            coEvery { restoreStateRepository.getAttempt() } returns attempt
            coEvery { snapshotProvider.currentSchemaVersion() } throws IllegalStateException("boom")
            val pathSlot = slot<String?>()
            val claimed = slot<RestoreAttempt>()
            coEvery { restoreStateRepository.beginAttempt(capture(claimed)) } returns true
            coEvery {
                databaseReplacement.rollbackToPreRestoreBackup(captureNullable(pathSlot), any())
            } coAnswers {
                secondArg<DatabaseReplacementEffects>().onBeforeMutation("")
                DatabaseReplacementResult.Committed()
            }

            coordinator.handlePostRestoreLaunch()

            assertNull(pathSlot.captured, "a Committed attempt's source is the canonical slot")
            assertNull(claimed.captured.rollbackSnapshotPath)
        }

    @Test
    fun `the recovery re-claim CHECKS ownership - a foreign-owned slot rejects before mutating`() =
        runTest {
            // Mutating the live database with no journal claim would be unrecoverable, so a
            // refused beginAttempt must throw out of onBeforeMutation and reject the transaction.
            coEvery { restoreStateRepository.getAttempt() } returns
                makeAttempt(phase = RestoreAttempt.Phase.Prepared)
            coEvery { restoreStateRepository.beginAttempt(any()) } returns false
            val effectsSlot = slot<DatabaseReplacementEffects>()
            coEvery {
                databaseReplacement.rollbackToPreRestoreBackup(any(), capture(effectsSlot))
            } returns DatabaseReplacementResult.RejectedBeforeMutation(
                BackupError.Io(IOException("rejected by the effects throw")),
            )

            coordinator.handlePostRestoreLaunch()

            val failure = runCatching { effectsSlot.captured.onBeforeMutation("") }
                .exceptionOrNull()
            assertTrue(
                failure is IllegalStateException,
                "an unchecked refusal would let the rollback mutate unjournaled; got $failure",
            )
        }

    @Test
    fun `the LEGACY marker's recovery completes end-to-end under the synthetic owner id`() =
        runTest {
            // The synthetic legacy owner claims, commits and resolves its own journal slot.
            val legacy = RestoreAttempt(
                id = LEGACY_ATTEMPT_ID,
                kind = RestoreAttempt.Kind.Restore,
                phase = RestoreAttempt.Phase.Prepared,
                context = makeContext(),
                rollbackSnapshotPath = null,
            )
            val journal = FakeRestoreStateRepository(initialAttempt = legacy)
            stubRollbackCommitted()
            val legacyLaunch = makeCoordinator(journal)

            val outcome = legacyLaunch.handlePostRestoreLaunch()

            assertEquals(
                RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack,
                outcome,
                "the legitimate legacy recovery must complete, not loop through RecoveryRequired",
            )
            assertNull(journal.getAttempt(), "the synthetic owner resolved its own slot")
        }

    @Test
    fun `a COMMITTED attempt with no context cannot be verified and recovers`() = runTest {
        // Committed but context-less — nothing to date the undo offer with, so no success path.
        coEvery { restoreStateRepository.getAttempt() } returns makeAttempt(context = null)
        stubRollbackCommitted()

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        coVerify(exactly = 0) { reporter.recordRestoreTimeFailure(any(), any(), any()) }
    }

    @Test
    fun `a COMMITTED attempt verifies by peek and succeeds`() = runTest {
        val context = makeContext(startedAt = 1_700_000_000_000L)
        coEvery { restoreStateRepository.getAttempt() } returns makeAttempt(context = context)
        coEvery { snapshotProvider.currentSchemaVersion() } returns 6

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded, outcome)
        assertFalse(coordinator.recoverySurfaceRequired)
        coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(ATTEMPT_ID) }
        coVerify(exactly = 1) {
            restoreStateRepository.markPreRestoreBackupAvailable(1_700_000_000_000L)
        }
        coVerify(exactly = 0) { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) }
        val publishedSlot = slot<AppDialog>()
        coVerify(exactly = 1) { appDialogPublisher.publish(capture(publishedSlot)) }
        assertTrue(publishedSlot.captured is AppDialog.RestoreSuccess)
        assertTrue((publishedSlot.captured as AppDialog.RestoreSuccess).previousVersionAvailable)
    }

    @Test
    fun `success finalization is data-bearing-first - the attempt resolves LAST`() = runTest {
        // A death mid-finalization must leave the journal Committed so the next launch replays.
        coEvery { restoreStateRepository.getAttempt() } returns
            makeAttempt(context = makeContext(startedAt = 1_700_000_000_000L))
        coEvery { snapshotProvider.currentSchemaVersion() } returns 6
        val order = mutableListOf<String>()
        coEvery { restoreStateRepository.markPreRestoreBackupAvailable(any()) } coAnswers {
            order += "mark"
        }
        coEvery { appDialogPublisher.publish(any()) } coAnswers { order += "publish" }
        coEvery { restoreStateRepository.resolveAttempt(any()) } coAnswers {
            order += "resolve"
            true
        }

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded, outcome)
        assertEquals(
            listOf("mark", "publish", "resolve"),
            order,
            "undo availability and the persisted dialog must land BEFORE the replay token goes",
        )
    }

    @Test
    fun `a finalization failure never leaves the journal resolved with the undo hidden`() =
        runTest {
            // "journal resolved ∧ undo snapshot exists ∧ availability absent" must be
            // unconstructible: the mark throws, so the resolve must not run.
            coEvery { restoreStateRepository.getAttempt() } returns makeAttempt()
            coEvery { snapshotProvider.currentSchemaVersion() } returns 6
            coEvery { restoreStateRepository.markPreRestoreBackupAvailable(any()) } throws
                IOException("datastore write failed")

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(
                RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded,
                outcome,
                "the restore IS durably committed and verified; only the finalization replays",
            )
            coVerify(exactly = 0) {
                restoreStateRepository.resolveAttempt(any())
            }
        }

    @Test
    fun `success finalization cleans up the RETAINED reservation copy idempotently`() = runTest {
        // Copy-based promotion retains the reservation until Committed is durable; the committed
        // cold-start finalization deletes the journal-named file.
        val retained = File.createTempFile("rollback_reservation_", ".db")
            .apply { writeText("retained-copy") }
        try {
            coEvery { restoreStateRepository.getAttempt() } returns
                makeAttempt(rollbackSnapshotPath = retained.absolutePath)
            coEvery { snapshotProvider.currentSchemaVersion() } returns 6

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded, outcome)
            assertFalse(retained.exists(), "the retained reservation copy must be cleaned up")
        } finally {
            retained.delete()
        }
    }

    @Test
    fun `a COMMITTED attempt whose peek fails recovers`() = runTest {
        val context = makeContext()
        coEvery { restoreStateRepository.getAttempt() } returns makeAttempt(context = context)
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
        coVerify(exactly = 1) {
            databaseReplacement.rollbackToPreRestoreBackup(
                // A Committed attempt's promotion is durably done, so the canonical slot is the
                // provable source — never the possibly-cleaned-up reservation path.
                sourcePath = null,
                effects = any(),
            )
        }
        // The resolve, the marker clear and the dialog publish ride the transaction's typed
        // effects — they ran because the stub invoked the effects object.
        coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(ATTEMPT_ID) }
        coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        val publishedSlot = slot<AppDialog>()
        coVerify(exactly = 1) { appDialogPublisher.publish(capture(publishedSlot)) }
        assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
    }

    @Test
    fun `the rollback uses the attempt's reserved snapshot path`() = runTest {
        // Before promotion onto the canonical slot the per-attempt reservation is the only file
        // holding the true pre-attempt database, so rollback targets the path the journal names.
        val reservedPath = "/data/user/0/app/cache/rollback/attempt-9d21.db"
        coEvery { restoreStateRepository.getAttempt() } returns makeAttempt(
            phase = RestoreAttempt.Phase.Prepared,
            rollbackSnapshotPath = reservedPath,
        )
        val pathSlot = slot<String?>()
        coEvery {
            databaseReplacement.rollbackToPreRestoreBackup(captureNullable(pathSlot), any())
        } returns DatabaseReplacementResult.Committed()

        coordinator.handlePostRestoreLaunch()

        assertEquals(reservedPath, pathSlot.captured)
    }

    @ParameterizedTest
    @MethodSource("nonCommitRollbackResults")
    fun `every non-commit rollback outcome requires terminal recovery`(
        rollbackResult: DatabaseReplacementResult,
    ) = runTest {
        // A pre-PONR rejection proves only that THIS rollback did not mutate, never what the
        // original Prepared attempt did — so every non-commit outcome routes to recovery.
        coEvery { restoreStateRepository.getAttempt() } returns
            makeAttempt(phase = RestoreAttempt.Phase.Prepared)
        coEvery {
            databaseReplacement.rollbackToPreRestoreBackup(any(), any())
        } returns rollbackResult

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        assertTrue(coordinator.recoverySurfaceRequired)
        assertEquals(outcome, coordinator.lastPreflightOutcome)
        coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
        coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
    }

    @Test
    fun `a rollback that commits without a durable record requires recovery`() = runTest {
        // The swap committed but its durable bookkeeping did not, so the live file's provenance
        // is not provable — terminal recovery, never RestoreRolledBack.
        coEvery { restoreStateRepository.getAttempt() } returns
            makeAttempt(phase = RestoreAttempt.Phase.Prepared)
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } returns
            DatabaseReplacementResult.Committed(
                effectsError = BackupError.Io(IOException("journal edit failed")),
            )

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        assertTrue(coordinator.recoverySurfaceRequired)
    }

    @Test
    fun `an interrupted restore is reported even though nothing threw`() = runTest {
        // The Prepared branch is the crash-mid-restore case the journal exists to catch, and it
        // has no throwable by construction — the process died, it did not fail. Reporting only
        // when a cause exists left exactly this case invisible in Crashlytics.
        coEvery { restoreStateRepository.getAttempt() } returns
            makeAttempt(phase = RestoreAttempt.Phase.Prepared)
        stubRollbackCommitted()

        coordinator.handlePostRestoreLaunch()

        verify(exactly = 1) {
            reporter.recordRestoreTimeFailure(any(), any(), any())
        }
    }

    @Test
    fun `a RecoveryRequired verdict preserves the live db for the export button`() = runTest {
        // RecoveryActivity's "Export your data" shares cache/pre_migration_backup.db. The
        // Scenario-1 route never reaches StartupMigrationCoordinator.routeToRecovery, which is
        // the only OTHER producer of that file, so this verdict must preserve it itself.
        coEvery { restoreStateRepository.getAttempt() } returns
            makeAttempt(phase = RestoreAttempt.Phase.Prepared)
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } returns
            DatabaseReplacementResult.RejectedBeforeMutation(
                BackupError.CorruptedBackup(reason = "journal-named rollback source is missing"),
            )
        coEvery { snapshotProvider.preserveDbBeforeMigration() } returns
            File("pre_migration_backup.db")

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        assertTrue(coordinator.recoverySurfaceRequired)
        coVerify(exactly = 1) { snapshotProvider.preserveDbBeforeMigration() }
    }

    @Test
    fun `a failing preserve still routes to recovery instead of crashing the launch`() = runTest {
        // The preserve runs inside Application.onCreate's runBlocking: an escape here would
        // crash the very launch whose job is to reach the recovery surface.
        coEvery { restoreStateRepository.getAttempt() } returns
            makeAttempt(phase = RestoreAttempt.Phase.Prepared)
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } returns
            DatabaseReplacementResult.FatalNoGeneration()
        coEvery { snapshotProvider.preserveDbBeforeMigration() } throws IOException("disk full")

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired, outcome)
        assertTrue(coordinator.recoverySurfaceRequired)
    }

    @Test
    fun `an outcome that keeps the main UI never copies the database`() = runTest {
        // The discriminating negative: preserving is a whole-file copy, so it must not land on
        // the ordinary cold start that every launch takes.
        coEvery { restoreStateRepository.getAttempt() } returns null

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.NoOp, outcome)
        assertFalse(coordinator.recoverySurfaceRequired)
        coVerify(exactly = 0) { snapshotProvider.preserveDbBeforeMigration() }
    }

    @Test
    fun `recovery path survives a process restart after FailedAfterMutation`() = runTest {
        // Launch 1's rollback fails after the point of no return; launch 2 is a NEW coordinator
        // over the SAME durable journal and completes the rollback.
        val journal = FakeRestoreStateRepository(initialAttempt = makeAttempt())
        coEvery { snapshotProvider.currentSchemaVersion() } throws
            IllegalStateException("migration crashed")
        val postPonrError = BackupError.Io(IOException("disk full"))
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } coAnswers {
            val effects = secondArg<DatabaseReplacementEffects>()
            effects.onBeforeMutation(ROLLBACK_PATH)
            effects.onFailedAfterMutation(postPonrError)
            DatabaseReplacementResult.FailedAfterMutation(postPonrError)
        }

        val firstLaunch = makeCoordinator(journal)
        val firstOutcome = firstLaunch.handlePostRestoreLaunch()

        assertEquals(
            RestoreRecoveryCoordinator.PreflightOutcome.RecoveryRequired,
            firstOutcome,
        )
        assertTrue(firstLaunch.recoverySurfaceRequired)
        // Nothing consumed: the journal entry and the undo marker survive into the next process.
        val survivingAttempt = journal.getAttempt()
        assertNotNull(survivingAttempt, "the unresolved attempt must survive the failed rollback")
        assertEquals(ATTEMPT_ID, survivingAttempt?.id)
        assertTrue(journal.preRestoreBackupAvailable, "the undo asset marker was not consumed")
        coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
        coVerify(exactly = 1) { appDialogPublisher.publish(any()) }

        // Simulated restart: a NEW coordinator over the SAME journal; this time it commits.
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } coAnswers {
            val effects = secondArg<DatabaseReplacementEffects>()
            effects.onBeforeMutation(ROLLBACK_PATH)
            effects.onMutationCommitted()
            effects.onCommitted()
            DatabaseReplacementResult.Committed()
        }
        val secondLaunch = makeCoordinator(journal)

        val secondOutcome = secondLaunch.handlePostRestoreLaunch()

        assertEquals(
            RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack,
            secondOutcome,
        )
        assertNull(journal.getAttempt(), "the owning attempt cleared the journal slot")
        assertFalse(journal.preRestoreBackupAvailable, "the preserved file was consumed")
        val published = mutableListOf<AppDialog>()
        coVerify(exactly = 2) { appDialogPublisher.publish(capture(published)) }
        assertTrue(published.all { it is AppDialog.RestoreFailure })
    }

    @Test
    fun `scenario-1 compensation is PACKAGED in the effects object - nothing runs post-await`() =
        runTest {
            coEvery { restoreStateRepository.getAttempt() } returns makeAttempt()
            coEvery {
                snapshotProvider.currentSchemaVersion()
            } throws IllegalStateException("migration crashed")
            val effectsSlot = slot<DatabaseReplacementEffects>()
            coEvery {
                databaseReplacement.rollbackToPreRestoreBackup(any(), capture(effectsSlot))
            } returns DatabaseReplacementResult.Committed()

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack, outcome)
            coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }

            // The writes live INSIDE the captured effects, run by the transaction coroutine; the
            // rollback reuses the RECOVERED attempt's id rather than opening a new one.
            assertEquals(ATTEMPT_ID, effectsSlot.captured.attemptId)
            effectsSlot.captured.onCommitted()
            coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(ATTEMPT_ID) }
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            val publishedSlot = slot<AppDialog>()
            coVerify(exactly = 1) { appDialogPublisher.publish(capture(publishedSlot)) }
            assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
        }

    @Test
    fun `performUndoRestore returns FileMissing when no preserved backup exists`() = runTest {
        every { snapshotProvider.getPreRestoreBackupFile() } returns null

        val result = coordinator.performUndoRestore()

        assertEquals(UndoRestoreOutcome.FileMissing, result)
        coVerify(exactly = 0) { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) }
        coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `performUndoRestore FailedAfterMutation maps to IoFailure and deletes nothing`() = runTest {
        stubPreservedBackupExists()
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } returns
            DatabaseReplacementResult.FailedAfterMutation(BackupError.Io(IOException("disk full")))

        val result = coordinator.performUndoRestore()

        assertEquals(UndoRestoreOutcome.IoFailure, result)
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        // Post-mutation failure: the coordinator must not clear the marker, resolve, or delete.
        coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
        coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
    }

    @Test
    fun `performUndoRestore RejectedBeforeMutation maps to IoFailure and keeps the retry slot`() =
        runTest {
            stubPreservedBackupExists()
            coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } returns
                DatabaseReplacementResult.RejectedBeforeMutation(
                    BackupError.Io(IOException("lease drain timed out")),
                )

            val result = coordinator.performUndoRestore()

            // The backup and its availability flag stay put, so the user can retry.
            assertEquals(UndoRestoreOutcome.IoFailure, result)
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        }

    @Test
    fun `the undo claims the journal as its own Rollback attempt before mutating`() = runTest {
        stubPreservedBackupExists()
        val pathSlot = slot<String?>()
        val effectsSlot = slot<DatabaseReplacementEffects>()
        coEvery {
            databaseReplacement.rollbackToPreRestoreBackup(
                captureNullable(pathSlot),
                capture(effectsSlot),
            )
        } returns DatabaseReplacementResult.Committed()

        coordinator.performUndoRestore()

        // The undo has no per-attempt reservation: it rolls back onto the canonical undo slot.
        assertTrue(pathSlot.isCaptured)
        assertNull(pathSlot.captured)
        val effects = effectsSlot.captured
        effects.onBeforeMutation(ROLLBACK_PATH)

        val claimedSlot = slot<RestoreAttempt>()
        coVerify(exactly = 1) { restoreStateRepository.beginAttempt(capture(claimedSlot)) }
        val claimed = claimedSlot.captured
        assertEquals(effects.attemptId, claimed.id, "the claim runs under THIS attempt's id")
        assertEquals(RestoreAttempt.Kind.Rollback, claimed.kind)
        assertEquals(RestoreAttempt.Phase.Prepared, claimed.phase)
        assertNull(claimed.context, "a rollback carries no restore manifest context")

        effects.onMutationCommitted()
        coVerify(exactly = 1) { restoreStateRepository.recordAttemptCommitted(effects.attemptId) }
    }

    @Test
    fun `performUndoRestore happy path - clear, publish, resolve LAST, THEN acknowledge`() =
        runTest {
            stubPreservedBackupExists()
            // Order recorder: data-bearing writes precede the resolve, and the acknowledge's
            // position is part of the dismiss-after discipline.
            val order = mutableListOf<String>()
            coEvery { restoreStateRepository.resolveAttempt(any()) } coAnswers {
                order += "resolve"
                true
            }
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
                listOf("clear", "publish", "resolve", "acknowledge"),
                order,
                "data-bearing writes first, resolve LAST, the caller's acknowledge after all",
            )
        }

    @Test
    fun `undo compensation is PACKAGED in the effects object - nothing runs post-await`() =
        runTest {
            // The stub CAPTURES the effects WITHOUT invoking them and returns Committed: writes
            // done post-await would appear now — they must not.
            stubPreservedBackupExists()
            val effectsSlot = slot<DatabaseReplacementEffects>()
            coEvery {
                databaseReplacement.rollbackToPreRestoreBackup(any(), capture(effectsSlot))
            } returns DatabaseReplacementResult.Committed()
            var callerAcknowledged = false

            val result = coordinator.performUndoRestore(onCommitted = { callerAcknowledged = true })

            assertEquals(UndoRestoreOutcome.Succeeded, result)
            coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
            assertFalse(callerAcknowledged, "nothing may run outside the transaction's effects")

            // The writes live INSIDE the captured effects, run by the transaction coroutine.
            val effects = effectsSlot.captured
            effects.onCommitted()
            coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(effects.attemptId) }
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 1) { appDialogPublisher.publish(AppDialog.UndoRestoreSuccess) }
            assertTrue(callerAcknowledged)
        }

    @Test
    fun `restartApp delegates to the AppReinitializer seam`() {
        coordinator.restartApp()

        verify(exactly = 1) { appReinitializer.reinitialize() }
    }

    /** Real transaction shape: the seam runs the typed effects' ladder, then commits. */
    private fun stubRollbackCommitted() {
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } coAnswers {
            val effects = secondArg<DatabaseReplacementEffects>()
            effects.onBeforeMutation(ROLLBACK_PATH)
            effects.onMutationCommitted()
            effects.onCommitted()
            DatabaseReplacementResult.Committed()
        }
    }

    private fun stubPreservedBackupExists() {
        every { snapshotProvider.getPreRestoreBackupFile() } returns
            File("pre_restore_backup.db")
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

    private fun makeAttempt(
        id: String = ATTEMPT_ID,
        kind: RestoreAttempt.Kind = RestoreAttempt.Kind.Restore,
        phase: RestoreAttempt.Phase = RestoreAttempt.Phase.Committed,
        context: RestoreInProgressContext? = makeContext(),
        rollbackSnapshotPath: String? = ROLLBACK_PATH,
    ): RestoreAttempt = RestoreAttempt(
        id = id,
        kind = kind,
        phase = phase,
        context = context,
        rollbackSnapshotPath = rollbackSnapshotPath,
    )

    /**
     * Stateful in-memory [RestoreStateRepository] for the process-restart gate: two coordinators
     * share it the way two processes share DataStore, with the real ownership semantics.
     */
    private class FakeRestoreStateRepository(
        initialAttempt: RestoreAttempt?,
    ) : RestoreStateRepository {

        var preRestoreBackupAvailable: Boolean = true
            private set

        private var attempt: RestoreAttempt? = initialAttempt
        private var originalDate: Long? = initialAttempt?.context?.startedAtEpochMs

        override suspend fun beginAttempt(attempt: RestoreAttempt): Boolean {
            val current = this.attempt
            if (current != null && current.id != attempt.id) return false
            this.attempt = attempt
            return true
        }

        override suspend fun recordAttemptCommitted(attemptId: String): Boolean {
            val current = attempt ?: return false
            if (current.id != attemptId) return false
            attempt = current.copy(phase = RestoreAttempt.Phase.Committed)
            return true
        }

        override suspend fun resolveAttempt(attemptId: String): Boolean {
            if (attempt?.id != attemptId) return false
            attempt = null
            return true
        }

        override suspend fun getAttempt(): RestoreAttempt? = attempt

        override suspend fun markPreRestoreBackupAvailable(originalDataDateEpochMs: Long) {
            preRestoreBackupAvailable = true
            originalDate = originalDataDateEpochMs
        }

        override suspend fun clearPreRestoreBackupAvailable() {
            preRestoreBackupAvailable = false
        }

        override fun observePreRestoreBackupAvailable(): Flow<Boolean> =
            flowOf(preRestoreBackupAvailable)

        override suspend fun getPreRestoreOriginalDate(): Long? = originalDate
    }

    companion object {

        private const val ATTEMPT_ID = "attempt-7f3c"
        private const val ROLLBACK_PATH = "/data/user/0/app/cache/rollback/attempt-7f3c.db"

        /** Mirrors `RestoreStateRepositoryImpl.LEGACY_ATTEMPT_ID` (wire format, private there). */
        private const val LEGACY_ATTEMPT_ID = "legacy-restore-in-progress"

        /** Every non-commit rollback outcome; all land on the same terminal-recovery verdict. */
        @JvmStatic
        fun nonCommitRollbackResults(): List<DatabaseReplacementResult> = listOf(
            DatabaseReplacementResult.RejectedBeforeMutation(
                BackupError.CorruptedBackup(reason = "journal-named rollback source is missing"),
            ),
            DatabaseReplacementResult.FailedAfterMutation(BackupError.Io(IOException("disk full"))),
            DatabaseReplacementResult.RecoveredByRollback(
                BackupError.Io(IOException("swap failed post-PONR")),
            ),
            DatabaseReplacementResult.FatalNoGeneration(),
        )
    }
}
