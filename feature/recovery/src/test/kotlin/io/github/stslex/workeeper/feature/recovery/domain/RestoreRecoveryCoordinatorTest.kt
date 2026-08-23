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
        // The durable attempt journal is the pre-flight's ONLY input, so its defaults are
        // explicit: "no unresolved attempt", and an owned slot. A relaxed Boolean would answer
        // `false`, which the effects' `check(...)` calls read as "someone else owns the slot".
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
        // THE kill-point (spec §8.5a): a process death after the journal was claimed but before
        // the close/mutation ran leaves the OLD, still-valid database on disk. A schema peek
        // would SUCCEED against it and publish a RestoreSuccess for a restore that never
        // happened — the exact lie the attempt journal exists to prevent. `Prepared` means "the
        // outcome is unknown", and unknown routes to recovery WITHOUT consulting the schema.
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
        // A COMMITTED rollback already applied its snapshot durably and CONSUMED it. Re-driving
        // it would look for a preserved file that no longer exists, fail, and leave the attempt
        // unresolved forever — which then refuses every future restore and undo, because their
        // `beginAttempt` sees a foreign owner. The pre-flight finishes the bookkeeping instead.
        val attempt = makeAttempt(
            kind = RestoreAttempt.Kind.Rollback,
            phase = RestoreAttempt.Phase.Committed,
            context = null,
        )
        coEvery { restoreStateRepository.getAttempt() } returns attempt
        // GROUND TRUTH for the availability verdict (R4): the canonical file is gone — the
        // committed rollback consumed it — so the flag clears.
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
            // The committed rollback applied its own reservation, so the canonical slot — the
            // PREVIOUS restore's undo — was never consumed and remains valid. Clearing the flag
            // here (the pre-R4 unconditional clear) revoked an undo the user still owns.
            val attempt = makeAttempt(
                kind = RestoreAttempt.Kind.Rollback,
                phase = RestoreAttempt.Phase.Committed,
                context = null,
            )
            coEvery { restoreStateRepository.getAttempt() } returns attempt
            every { snapshotProvider.getPreRestoreBackupFile() } returns
                File("pre_restore_backup.db")

            val outcome = coordinator.handlePostRestoreLaunch()

            assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, outcome)
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(attempt.id) }
        }

    @Test
    fun `the recovery rollback CARRIES the journal's reservation path through its re-claim`() =
        runTest {
            // A PREPARED attempt's reservation is the only file holding the true pre-attempt
            // database. Erasing the path on the re-claim would leave a second interruption with
            // only the canonical slot — an OLDER snapshot — silently reverting data the failed
            // attempt never touched.
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
            // For a Committed attempt, the commit ordering (promote < record) proves the
            // canonical slot holds THIS attempt's pre-image, and the retained reservation may
            // already be cleaned up — so the rollback is submitted with sourcePath = null
            // (canonical), never the possibly-gone reservation path, and its re-claim records
            // the same truth.
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
            // R4 blocker C: mutating the live database with no journal claim of our own would
            // leave the interrupted state unrecoverable. A refused `beginAttempt` must throw out
            // of onBeforeMutation — the transaction then rejects before anything irreversible.
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
            // Mandated R4 test 6 (coordinator half; the repository half proves the atomic
            // conversion against real DataStore): the synthetic legacy owner claims, commits and
            // resolves its own slot — pre-R4 the ignored claim refusal left the mutation
            // unjournaled and `recordAttemptCommitted` then failed the whole recovery.
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
        // Committed but context-less: nothing to report against and nothing to date the undo
        // offer with — the pre-flight refuses the success path rather than half-taking it.
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
        // R4 invariant 7: a death anywhere inside the finalization must leave the journal at
        // `Committed` so the next launch replays it — pre-R4 the resolve came first and a death
        // in between erased the replay token while hiding a valid undo snapshot forever.
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
            // Mandated R4 test 11: "journal resolved ∧ undo snapshot exists ∧ availability
            // absent" must be unconstructible. The mark THROWS — the resolve must not run, so
            // the Committed journal replays the whole finalization next launch.
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
        // Copy-based promotion retains the reservation until `Committed` is durable; a death
        // between the record and the same-process delete leaves it behind. The committed
        // cold-start finalization deletes the journal-named file — real file, real delete.
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
                // A COMMITTED attempt's promotion is durably done, so the canonical slot is the
                // provable source (R4) — never the possibly-cleaned-up reservation path.
                sourcePath = null,
                effects = any(),
            )
        }
        // The journal resolve, the marker clear and the dialog publish ride the transaction's
        // typed effects — they ran because the stub invoked the effects object, proving the
        // coordinator PASSES them (not post-await code an initiator's death could strand).
        coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(ATTEMPT_ID) }
        coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        val publishedSlot = slot<AppDialog>()
        coVerify(exactly = 1) { appDialogPublisher.publish(capture(publishedSlot)) }
        assertTrue(publishedSlot.captured is AppDialog.RestoreFailure)
    }

    @Test
    fun `the rollback uses the attempt's reserved snapshot path`() = runTest {
        // Between the live-file mutation and the reservation's promotion onto the canonical undo
        // slot, the per-attempt reservation is the ONLY file holding the true pre-attempt
        // database — the pre-flight must roll back onto the path the journal names, not onto the
        // canonical slot (`sourcePath = null`).
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
        // R4 blocker B. A pre-PONR REJECTION of the recovery rollback proves only that THIS
        // rollback did not mutate — never what the ORIGINAL Prepared attempt did to the live
        // file before dying — so it licenses Main UI no more than a post-PONR failure does.
        // Every non-commit outcome lands on the same verdict: arm NO DB-bound work, show no
        // main UI, preserve every asset and the journal entry, route to the recovery surface.
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
        // The rollback swap committed but its durable bookkeeping did not: the journal still
        // names an unresolved attempt, so the live file's provenance is not provable. That is
        // terminal recovery — never RestoreRolledBack, which would restart into a database
        // nobody can vouch for.
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
    fun `recovery path survives a process restart after FailedAfterMutation`() = runTest {
        // PROCESS-RESTART gate: launch 1's rollback fails after the point of no return → the
        // durable journal still holds the SAME unresolved attempt and every asset marker stands;
        // launch 2 is a NEW coordinator over that SAME journal and completes the rollback.
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
        // Nothing consumed: the journal entry, still owned by the SAME attempt id, and the undo
        // asset marker both survive into the next process.
        val survivingAttempt = journal.getAttempt()
        assertNotNull(survivingAttempt, "the unresolved attempt must survive the failed rollback")
        assertEquals(ATTEMPT_ID, survivingAttempt?.id)
        assertTrue(journal.preRestoreBackupAvailable, "the undo asset marker was not consumed")
        coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
        coVerify(exactly = 1) { appDialogPublisher.publish(any()) }

        // Simulated restart: a NEW coordinator over the SAME durable journal; this time the
        // transaction commits and runs the typed effects to completion.
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

            // The writes live INSIDE the captured effects — the transaction coroutine (which
            // survives the initiator's death) is what executes them. The rollback reuses the
            // RECOVERED attempt's id: it finishes that attempt, it does not open a new one.
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
        // Post-mutation failure: the recovery assets belong to the runtime/journal protocol —
        // the coordinator must not clear the marker, resolve the attempt, or delete anything.
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

            // pre_restore_backup.db is untouched and pre_restore_backup_available stays set, so
            // the user can re-tap the dialog or retry from Settings → "Revert last restore".
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

        // The undo has no per-attempt reservation to prefer: it rolls back onto the canonical
        // `pre_restore_backup.db` undo slot, which the seam selects on a null sourcePath.
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
            // Order recorder across ALL four writes. Data-bearing writes precede the resolve
            // (R4 invariant 7: a death mid-sequence leaves a Committed rollback the replay
            // branch finishes, never a resolved journal with half-done bookkeeping), and the
            // acknowledge's POSITION is part of the dismiss-after discipline (a dismiss before
            // the success dialog persists would lose the dialog across a crash between the two).
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
            // The discriminator for mandate 2 (an effects-invoking stub alone cannot tell
            // "packaged in effects" from "post-await code gated on Committed"): the seam stub
            // CAPTURES the effects WITHOUT invoking them and returns Committed. If the
            // coordinator ran its writes post-await, they would appear now — they must not.
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

            // The writes live INSIDE the captured effects — the transaction coroutine (which
            // survives the initiator's death) is what executes them.
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
     * Stateful in-memory [RestoreStateRepository] for the process-restart gate: two coordinator
     * instances (two "launches") share this object the way two processes share DataStore. The
     * ownership semantics are the real ones — only the id that owns the slot may advance or
     * clear it.
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

        /**
         * Every rollback outcome short of a clean durable commit — the pre-PONR rejection
         * INCLUDED (R4): none of them proves the live database's provenance, so all of them
         * must land on the same terminal-recovery verdict.
         */
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
