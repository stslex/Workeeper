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

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RecoveryCompleted, outcome)
        coVerify(exactly = 0) { snapshotProvider.currentSchemaVersion() }
        coVerify(exactly = 0) { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) }
        coVerify(exactly = 1) { restoreStateRepository.resolveAttempt(attempt.id) }
        coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        coVerify(exactly = 0) { restoreStateRepository.markPreRestoreBackupAvailable(any()) }
    }

    @Test
    fun `the recovery rollback CARRIES the journal's reservation path through its re-claim`() =
        runTest {
            // Erasing the path on the re-claim would leave a second interruption with only the
            // canonical slot — an OLDER snapshot — silently reverting data the failed attempt
            // never touched.
            val attempt = makeAttempt(rollbackSnapshotPath = "/data/cache/rollback_reservation_A.db")
            coEvery { restoreStateRepository.getAttempt() } returns attempt
            coEvery { snapshotProvider.currentSchemaVersion() } throws IllegalStateException("boom")
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
                sourcePath = ROLLBACK_PATH,
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

    @Test
    fun `pre-PONR rejection yields the SAFE retry path`() = runTest {
        // PROVEN pre-mutation rejection: nothing was closed, mutated or torn down, so the live
        // database is intact and open. The launch continues on the safe path — no restart (it
        // would loop) and no recovery surface (the intact database does not warrant one), with
        // every asset and the journal entry preserved for the next attempt.
        coEvery { restoreStateRepository.getAttempt() } returns
            makeAttempt(phase = RestoreAttempt.Phase.Prepared)
        val error = BackupError.Io(IOException("machine busy"))
        coEvery { databaseReplacement.rollbackToPreRestoreBackup(any(), any()) } coAnswers {
            secondArg<DatabaseReplacementEffects>().onRejectedBeforeMutation(error)
            DatabaseReplacementResult.RejectedBeforeMutation(error)
        }

        val outcome = coordinator.handlePostRestoreLaunch()

        assertEquals(RestoreRecoveryCoordinator.PreflightOutcome.RetrySafe, outcome)
        assertFalse(coordinator.recoverySurfaceRequired)
        coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
        coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
    }

    @ParameterizedTest
    @MethodSource("postMutationRollbackResults")
    fun `post-mutation outcomes require terminal recovery`(
        rollbackResult: DatabaseReplacementResult,
    ) = runTest {
        // Post-PONR, a closed handle, or a terminal runtime: this process must arm NO DB-bound
        // work and must not show the main UI over a database of unknown provenance. Assets and
        // the journal entry are preserved; the user reaches the explicit recovery surface.
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
    fun `performUndoRestore happy path - resolve, clear, publish, THEN acknowledge, in order`() =
        runTest {
            stubPreservedBackupExists()
            // Order recorder across ALL four writes — the acknowledge's POSITION is part of the
            // dismiss-after discipline (a dismiss before the success dialog persists would lose
            // the dialog across a crash between the two).
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
                listOf("resolve", "clear", "publish", "acknowledge"),
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

        /**
         * Every rollback outcome that means "the mutation's result is unknown or the runtime is
         * terminal" — all of them must land on the same terminal-recovery verdict.
         */
        @JvmStatic
        fun postMutationRollbackResults(): List<DatabaseReplacementResult> = listOf(
            DatabaseReplacementResult.FailedAfterMutation(BackupError.Io(IOException("disk full"))),
            DatabaseReplacementResult.RecoveredByRollback(
                BackupError.Io(IOException("swap failed post-PONR")),
            ),
            DatabaseReplacementResult.FatalNoGeneration(),
        )
    }
}
