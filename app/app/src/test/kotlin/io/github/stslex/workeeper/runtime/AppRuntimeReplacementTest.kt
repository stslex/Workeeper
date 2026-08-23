// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.di.AppGraph
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/**
 * Replacement-transaction pins for the round-2 protocol EXTENDED with the round-3 durable
 * attempt journal (spec §8.5a): submission-owned bodies with staged source ownership, typed
 * effects executed exactly once on the transaction coroutine, the per-attempt rollback
 * RESERVATION taken inside the transaction (after validation, before anything irreversible),
 * the promote → durable-commit-record → consume ordering, terminal compensation failures folded
 * onto the outcome, PONR at the START of the first irreversible action (teardown / close
 * invocation / rename), close-throw → Fatal, truthful RecoveredByRollback vs Committed, closed
 * admission (leases + atomic UI retire), and Fatal that is terminal under concurrency.
 */
internal class AppRuntimeReplacementTest {

    private class ProbeViewModel(val onClear: () -> Unit = {}) : ViewModel() {
        override fun onCleared() = onClear()
    }

    /**
     * Recording effects double — one label per protocol phase, order-preserving. [calls] is
     * injectable so a test can fold the effect labels into the SHARED protocol log and assert
     * their interleaving against the provider's own calls.
     */
    private class RecordingEffects(
        override val attemptId: String = "attempt-1",
        val calls: MutableList<String> = mutableListOf(),
        private val onBefore: suspend () -> Unit = {},
        private val onMutationCommittedBody: suspend () -> Unit = {},
        private val onCommittedBody: suspend () -> Unit = {},
        private val onRejectedBody: suspend () -> Unit = {},
    ) : DatabaseReplacementEffects {

        /** The reservation path the runtime handed to [onBeforeMutation]; null when never run. */
        var reservationPath: String? = null
            private set

        override suspend fun onBeforeMutation(rollbackSnapshotPath: String) {
            reservationPath = rollbackSnapshotPath
            calls += "beforeMutation"
            onBefore()
        }

        override suspend fun onMutationCommitted() {
            calls += "mutationCommitted"
            onMutationCommittedBody()
        }

        override suspend fun onRejectedBeforeMutation(error: BackupError) {
            calls += "rejected"
            onRejectedBody()
        }

        override suspend fun onCommitted() {
            calls += "committed"
            onCommittedBody()
        }

        override suspend fun onRecoveredByRollback(error: BackupError) {
            calls += "recovered"
        }

        override suspend fun onFailedAfterMutation(error: BackupError) {
            calls += "failedAfterMutation"
        }

        override suspend fun onFatal() {
            calls += "fatal"
        }
    }

    @TempDir
    lateinit var tempDir: File

    private val context = mockk<Context>(relaxed = true)
    private val databases = mutableListOf<AppDatabase>()

    /** Close failures by BUILD INDEX (0 = generation 1's db, 1 = the first candidate, …). */
    private val closeFailureIndices = mutableSetOf<Int>()
    private val graphFactoryFailures = mutableSetOf<Int>()
    private var builtGraphs = 0

    /** Runs inside the graph factory BEFORE its injected failure — the partial-build seam. */
    private var graphFactoryAction: ((Int, AppDatabase, AppScopeLifetime) -> Unit)? = null

    /** Interleaving recorder: closes, db touches, job-ends, swaps and asset deletes land here. */
    private val protocolLog = mutableListOf<String>()

    /** Every value a database touch returned — keeps the touch a genuine use, not a no-op. */
    private val touchedDatabases = mutableListOf<String>()

    private val provider = mockk<DatabaseSnapshotProvider>(relaxed = true)

    /** Every rollback snapshot the provider reserved, in reservation order. */
    private val reservations = mutableListOf<File>()

    private var preflightCalls = 0
    private val preflightOutcomes = ArrayDeque<StartupOutcome>()
    private var preflightAction: (suspend (RuntimeGeneration) -> Unit)? = null
    private var preflightGate: CompletableDeferred<Unit>? = null

    private var epochAdvances = 0

    private fun sourceFile(name: String = "restore_source.db"): File =
        File(tempDir, name).apply { writeText("snapshot-bytes") }

    private fun preservedFile(content: String = "preserved-bytes"): File =
        File(tempDir, "pre_restore_backup.db").apply { writeText(content) }

    private fun runtimeTest(
        replacementPolicy: ReplacementPolicy = ReplacementPolicy.RebuildInProcess,
        // Standard (non-eager) host scheduling — for pins that must observe what the NON-
        // suspending submission frame did before the host coroutine ever ran.
        standardHostDispatcher: Boolean = false,
        body: suspend TestScope.(AppRuntime) -> Unit,
    ) = runTest {
        coEvery { provider.validateSnapshotForRestore(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
            protocolLog += "swap"
            BackupResult.Success(Unit)
        }
        coEvery { provider.getPreRestoreBackupFile() } answers {
            File(tempDir, "pre_restore_backup.db").takeIf { it.exists() }
        }
        coEvery { provider.deletePreRestoreBackup() } coAnswers {
            protocolLog += "deletePreRestoreBackup"
            File(tempDir, "pre_restore_backup.db").delete()
        }
        // R3 rollback-slot reservation (spec §8.5a): a UNIQUE per-attempt file, promoted onto the
        // canonical undo slot only after the attempt's live-file mutation committed.
        coEvery { provider.reserveRollbackSnapshot(any()) } coAnswers {
            val reservation = File(tempDir, "reservation_${firstArg<String>()}.db")
                .apply { writeText(RESERVATION_CONTENT) }
            reservations += reservation
            BackupResult.Success(reservation)
        }
        coEvery { provider.promoteRollbackReservation(any()) } coAnswers {
            val reservation = firstArg<File>()
            reservation.copyTo(File(tempDir, "pre_restore_backup.db"), overwrite = true)
            reservation.delete()
            BackupResult.Success(Unit)
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = AppRuntime(
            applicationContext = context,
            dbFactory = {
                val index = databases.size
                val db = mockk<AppDatabase>(relaxed = true)
                // A call ON the database object, recorded by the mock itself, so the ordering
                // pins below prove a job reached the handle before it was closed. It is
                // `toString()` rather than a DAO read because app/app's unit-test classpath has
                // no room3 — `AppDatabase`'s own members cannot be resolved here at all.
                every { db.toString() } answers {
                    protocolLog += "db-touch-$index"
                    "AppDatabase#$index"
                }
                databases += db
                db
            },
            imageStorageFactory = { mockk<ImageStorage>(relaxed = true) },
            graphFactory = { _, database, _, lifetime, _ ->
                val index = builtGraphs++
                graphFactoryAction?.invoke(index, database, lifetime)
                check(index !in graphFactoryFailures) { "injected graph construction failure" }
                mockk<AppGraph>(relaxed = true) {
                    every { databaseSnapshotProvider } returns provider
                }
            },
            preflight = { generation ->
                preflightCalls++
                preflightAction?.invoke(generation)
                preflightGate?.await()
                preflightOutcomes.removeFirstOrNull() ?: StartupOutcome.Proceed
            },
            closeDatabase = { db ->
                protocolLog += "close"
                check(databases.indexOf(db) !in closeFailureIndices) { "injected close failure" }
            },
            replacementPolicy = replacementPolicy,
            policy = RuntimeTransitionPolicy(
                advanceSnackbarGeneration = { epochAdvances++ },
                mainDispatcher = dispatcher,
                hostDispatcher = if (standardHostDispatcher) {
                    kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
                } else {
                    dispatcher
                },
                stagingDirectory = { tempDir },
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        body(runtime)
    }

    private fun stagedFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("staged_restore_") }

    private fun reservationFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("reservation_") }

    private fun closeIndices(): List<Int> =
        protocolLog.withIndex().filter { it.value == "close" }.map { it.index }

    // ------------------------------------------------------------------------------------------
    // Mandate 1 — staged source ownership at submission.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `restore stages the source at submission and cleans the staged copy on commit`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val source = sourceFile()

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(source))

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertFalse(source.exists(), "ownership transferred: the original path was consumed")
            assertTrue(stagedFiles().isEmpty(), "the runtime deletes its staged copy on commit")
            val swapped = slot<File>()
            coVerify { provider.replaceLiveDatabaseFile(capture(swapped)) }
            assertTrue(
                swapped.captured.name.startsWith("staged_restore_"),
                "the transaction swaps the RUNTIME-OWNED staged copy, got ${swapped.captured}",
            )
        }

    @Test
    fun `cancelled caller deleting its temp path cannot strand the transaction - it commits`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val source = sourceFile()
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val caller = launch { runtime.replace(ReplacementOperation.RestoreFromSnapshot(source)) }
            runCurrent()
            caller.cancel()
            // The caller's cleanup: deleting the ORIGINAL path is a no-op post-staging.
            source.delete()
            runCurrent()

            gate.complete(Unit)
            runCurrent()

            val serving = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertEquals(2, serving.generation.id, "the transaction completed despite the caller")
            assertTrue(stagedFiles().isEmpty(), "terminal cleanup ran on the runtime's copy")
        }

    @Test
    fun `staging failure rejects before anything - no validation, compensation runs`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val missing = File(tempDir, "never_created.db")
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(missing),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
            coVerify(exactly = 0) { provider.validateSnapshotForRestore(any()) }
            coVerify(exactly = 0) { provider.reserveRollbackSnapshot(any()) }
            assertEquals(listOf("rejected"), effects.calls)
        }

    // ------------------------------------------------------------------------------------------
    // Mandate 2 — typed effects, runtime-owned compensation, exactly once.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `marker written - caller killed - lease timeout - transaction-owned cleanup still runs`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val lease = runtime.awaitBackupWorkLease() // the quiesce will time out on it
            var markerWritten = false
            val effects = RecordingEffects(onBefore = { markerWritten = true })
            val source = sourceFile()

            val caller = launch {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(source), effects)
            }
            runCurrent()
            assertTrue(markerWritten, "beforeMutation ran inside the mutex, before the quiesce")
            caller.cancel() // the initiator dies mid-transaction
            advanceTimeBy(3_000)
            runCurrent()

            assertEquals(
                listOf("beforeMutation", "rejected"),
                effects.calls,
                "compensation ran ON the transaction despite the dead caller",
            )
            assertTrue(stagedFiles().isEmpty(), "the staged source was cleaned by the runtime")
            val serving = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertEquals(1, serving.generation.id)
            lease.release()
        }

    @Test
    fun `validation failure - rejection with compensation, nothing irreversible`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            coEvery { provider.validateSnapshotForRestore(any()) } returns BackupResult.Failure(
                BackupError.BackupTooNew(backupSchemaVersion = 9, appSchemaVersion = 5),
            )
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            val rejected =
                assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
            assertInstanceOf(BackupError.BackupTooNew::class.java, rejected.error)
            assertEquals(listOf("rejected"), effects.calls)
            assertTrue(protocolLog.isEmpty(), "no close, no swap")
            assertSame(genOne, runtime.currentGeneration)
        }

    @Test
    fun `beforeMutation failure - rejection, nothing closed or swapped`() = runtimeTest { runtime ->
        runtime.currentGeneration
        val effects = RecordingEffects(onBefore = { error("marker write failed") })

        val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()), effects)

        assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
        assertEquals(listOf("beforeMutation", "rejected"), effects.calls)
        assertTrue(protocolLog.isEmpty())
    }

    @Test
    fun `committed effects failure is SURFACED - never a silently clean commit`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val effects = RecordingEffects(onCommittedBody = { error("dialog write failed") })

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertNotNull(completed.effectsError, "the failed commit-effects must surface")
            assertNotNull(completed.generation, "the operation itself DID commit")
        }

    // ------------------------------------------------------------------------------------------
    // Spec §8.5a — the per-attempt rollback reservation and the durable commit record.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `the rollback snapshot is reserved INSIDE the transaction, after validation`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            coEvery { provider.validateSnapshotForRestore(any()) } returns BackupResult.Failure(
                BackupError.CorruptedBackup(reason = "magic mismatch"),
            )
            val rejectedEffects = RecordingEffects(attemptId = "attempt-rejected")

            val rejected = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile("first.db")),
                rejectedEffects,
            )

            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, rejected)
            // Reservation is INSIDE the transaction and AFTER validation: a snapshot taken for a
            // request the gates reject would churn the undo slot for nothing.
            coVerify(exactly = 0) { provider.reserveRollbackSnapshot(any()) }
            assertNull(rejectedEffects.reservationPath, "no reservation, no pre-mutation path")
            assertTrue(reservationFiles().isEmpty())

            coEvery { provider.validateSnapshotForRestore(any()) } returns BackupResult.Success(Unit)
            val committedEffects = RecordingEffects(attemptId = "attempt-committed")

            val committed = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile("second.db")),
                committedEffects,
            )

            assertInstanceOf(ReplacementOutcome.Completed::class.java, committed)
            coVerify(exactly = 1) { provider.reserveRollbackSnapshot("attempt-committed") }
            assertEquals(
                reservations.single().absolutePath,
                committedEffects.reservationPath,
                "onBeforeMutation persists THIS attempt's reservation path",
            )
        }

    @Test
    fun `a rejected restore discards only its own reservation and leaves the previous undo slot intact`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile(content = "OLD")
            // Rejected AFTER the reservation: the journal claim is what refuses the attempt.
            val effects = RecordingEffects(onBefore = { error("journal slot already owned") })

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
            coVerify(exactly = 1) { provider.reserveRollbackSnapshot(any()) }
            assertEquals(listOf("beforeMutation", "rejected"), effects.calls)
            assertEquals(
                "OLD",
                File(tempDir, "pre_restore_backup.db").readText(),
                "the PREVIOUS undo slot survives a rejected attempt byte-for-byte",
            )
            assertTrue(
                reservationFiles().isEmpty(),
                "the attempt discarded its OWN reservation: ${reservationFiles()}",
            )
        }

    @Test
    fun `a committed restore promotes its reservation onto the undo slot`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile(content = "OLD")

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            val reservation = reservations.single()
            assertEquals(
                RESERVATION_CONTENT,
                File(tempDir, "pre_restore_backup.db").readText(),
                "the undo slot holds the db that IMMEDIATELY preceded this restore",
            )
            assertFalse(reservation.exists(), "the reservation was promoted (moved), not copied")
            assertTrue(reservationFiles().isEmpty())
        }

    @Test
    fun `durable commit is recorded BEFORE the rollback asset is consumed`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            // ONE shared list: the effects' labels interleave with the provider's own calls.
            val effects = RecordingEffects(calls = protocolLog)

            val outcome = runtime.replace(
                ReplacementOperation.RollbackToPreRestoreBackup(),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            val recorded = protocolLog.indexOf("mutationCommitted")
            val consumed = protocolLog.indexOf("deletePreRestoreBackup")
            assertTrue(recorded >= 0, "the durable commit record must run: $protocolLog")
            assertTrue(consumed >= 0, "the rollback asset must be consumed: $protocolLog")
            assertTrue(
                recorded < consumed,
                "a crash between the two must leave a PROVABLE commit, not a lost asset: $protocolLog",
            )
        }

    @Test
    fun `a failed durable commit record keeps every asset and surfaces on the outcome`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile(content = "UNDO")
            val effects = RecordingEffects(
                onMutationCommittedBody = { error("durable journal write failed") },
            )

            val outcome = runtime.replace(
                ReplacementOperation.RollbackToPreRestoreBackup(),
                effects,
            )

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertNotNull(
                completed.effectsError,
                "an unprovable commit is surfaced, never reported as a clean one",
            )
            coVerify(exactly = 0) { provider.deletePreRestoreBackup() }
            assertEquals(
                "UNDO",
                File(tempDir, "pre_restore_backup.db").readText(),
                "nothing was consumed — the next launch can still roll back",
            )
            assertEquals(listOf("beforeMutation", "mutationCommitted", "committed"), effects.calls)
        }

    @Test
    fun `terminal compensation failure is surfaced on the outcome`() = runtimeTest { runtime ->
        runtime.currentGeneration
        coEvery { provider.validateSnapshotForRestore(any()) } returns BackupResult.Failure(
            BackupError.BackupTooNew(backupSchemaVersion = 9, appSchemaVersion = 5),
        )
        val effects = RecordingEffects(onRejectedBody = { error("compensation write failed") })

        val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()), effects)

        val rejected =
            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
        assertInstanceOf(BackupError.BackupTooNew::class.java, rejected.error)
        assertNotNull(
            rejected.effectsError,
            "a throwing compensation leaves durable state disagreeing with the outcome",
        )
        assertEquals(listOf("rejected"), effects.calls)
    }

    // ------------------------------------------------------------------------------------------
    // Mandates 3 + 4 — result truth and asset preservation.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `restore swap failure with successful rollback - RecoveredByRollback, never Completed`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            val swapError = BackupError.Io(IOException("rename failed"))
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                if (swaps++ == 0) BackupResult.Failure(swapError) else BackupResult.Success(Unit)
            }
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            val recovered =
                assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertSame(swapError, recovered.error)
            assertEquals(2, recovered.generation.id, "a successor serves the PRE-operation data")
            assertEquals(
                listOf("beforeMutation", "mutationCommitted", "recovered"),
                effects.calls,
                "the rollback's own commit is recorded; the terminal is recovered, never committed",
            )
        }

    @Test
    fun `inline scenario-1 rollback - the outer RESTORE reports RecoveredByRollback`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = { _ ->
                if (preflightCalls == 1) {
                    inlineResult = runtime.rollbackToPreRestoreBackup()
                }
            }
            preflightOutcomes += StartupOutcome.RestartRequired // after the inline rollback

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            // The inline caller REQUESTED a rollback — its result is honestly Committed.
            assertInstanceOf(DatabaseReplacementResult.Committed::class.java, inlineResult)
            // The outer caller requested a RESTORE that ended rolled back — never Completed.
            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(2, preflightCalls, "the retry attempt ran over the rolled-back file")
        }

    @Test
    fun `rollback operation commits - Completed is the requested-op truth`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RollbackToPreRestoreBackup(),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(listOf("beforeMutation", "mutationCommitted", "committed"), effects.calls)
        }

    @Test
    fun `restart-process swap failure - FailedAfterMutation, journal effects, assets preserved`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            preservedFile()
            coEvery { provider.replaceLiveDatabaseFile(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("rename failed")),
            )
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            assertEquals(listOf("beforeMutation", "failedAfterMutation"), effects.calls)
            assertTrue(File(tempDir, "pre_restore_backup.db").exists(), "assets preserved")
            assertEquals(
                1,
                reservationFiles().size,
                "the journal names this reservation — the runtime keeps it for the next launch",
            )
        }

    @Test
    fun `restart-process close throw - FailedAfterMutation, never rejected, no rename`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            preservedFile()
            closeFailureIndices += 0
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            // Close INVOCATION = PONR (mandate 5): a throwing close is post-PONR unknown state.
            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            assertEquals(0, protocolLog.count { it == "swap" }, "never rename after a failed close")
            assertEquals(listOf("beforeMutation", "failedAfterMutation"), effects.calls)
            assertTrue(File(tempDir, "pre_restore_backup.db").exists(), "assets preserved")
        }

    // ------------------------------------------------------------------------------------------
    // Mandate 5 — PONR at the start of every irreversible action.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `outgoing close failure is FATAL - no rename, no republished generation`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            closeFailureIndices += 0
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            assertFalse(protocolLog.contains("swap"), "a failed close must NEVER be renamed over")
            assertEquals(listOf("beforeMutation", "fatal"), effects.calls)
            assertThrows<IllegalStateException> { runtime.currentGeneration }
        }

    @Test
    fun `strict replacement clears the probe ViewModel and joins the DB job BEFORE close`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            var probeCleared = false
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProbeViewModel(onClear = { probeCleared = true }) as T
            }
            ViewModelProvider(genOne.viewModelStore, factory)[ProbeViewModel::class.java]
            genOne.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                try {
                    awaitCancellation()
                } finally {
                    protocolLog += "db-job-ended"
                }
            }

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertTrue(probeCleared, "the outgoing runtime-owned ViewModelStore must be CLEARED")
            val jobIndex = protocolLog.indexOf("db-job-ended")
            val closeIndex = protocolLog.indexOf("close")
            assertTrue(jobIndex in 0 until closeIndex, "DB job joined BEFORE close: $protocolLog")
        }

    @Test
    fun `unjoinable outgoing job after PONR - Fatal, the database is never closed`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val never = CompletableDeferred<Unit>()
            genOne.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                withContext(NonCancellable) { never.await() } // an unjoinable DB-bound job
            }

            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            advanceTimeBy(5_000)
            runCurrent()

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, transaction.await())
            assertFalse(protocolLog.contains("close"), "never close under an unjoined job")
            assertFalse(protocolLog.contains("swap"))
            never.complete(Unit)
            runCurrent()
        }

    @Test
    fun `candidate dispose close failure stops the ladder FATAL - no rollback rename`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            closeFailureIndices += 1 // the first CANDIDATE's close will throw during disposal
            preflightOutcomes += StartupOutcome.RouteToRecovery // the candidate fails preflight

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(
                1,
                protocolLog.count { it == "swap" },
                "the ladder stopped: only the primary swap ran, never a rollback rename",
            )
        }

    @Test
    fun `orphaned candidate close failure after graphFactory throw - Fatal, ladder stopped`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            graphFactoryFailures += 1 // the candidate's graph build throws
            closeFailureIndices += 1 // and the orphan's close throws too

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(1, protocolLog.count { it == "swap" }, "no rollback rename after")
        }

    @Test
    fun `graphFactory failure with clean orphan close - ladder recovers by rollback`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            graphFactoryFailures += 1 // candidate #1 fails; its db closes cleanly

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
        }

    @Test
    fun `candidate jobs are joined before the candidate database closes`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            preflightAction = { generation ->
                if (preflightCalls == 1) {
                    generation.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                        try {
                            awaitCancellation()
                        } finally {
                            // The unwinding job TOUCHES the candidate database: the mock records
                            // "db-touch-1", so an out-of-order teardown would close the handle
                            // this access still needs.
                            touchedDatabases += generation.database.toString()
                            protocolLog += "candidate-job-ended"
                        }
                    }
                    // A nested unconfined launch queues on the thread-local event loop; yield so
                    // the job genuinely STARTS (enters its try) before this preflight returns.
                    kotlinx.coroutines.yield()
                }
            }
            preflightOutcomes += StartupOutcome.RouteToRecovery // candidate #1 fails preflight

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            // closes: [0]=outgoing, [1]=candidate #1's dispose-close.
            val candidateClose = closeIndices()[1]
            assertTrue(
                protocolLog.indexOf("candidate-job-ended") in 0 until candidateClose,
                "the candidate's DB-bound job must JOIN before its database closes: $protocolLog",
            )
            assertTrue(
                protocolLog.indexOf("db-touch-1") in 0 until candidateClose,
                "the job's finally used the candidate db BEFORE the close: $protocolLog",
            )
            assertEquals(
                listOf("AppDatabase#1"),
                touchedDatabases,
                "the job touched the CANDIDATE's database, not the outgoing one",
            )
        }

    @Test
    fun `a partially constructed generation joins its jobs before closing the orphan database`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            graphFactoryFailures += 1 // candidate #1's graph build throws AFTER the action below
            graphFactoryAction = { index, database, lifetime ->
                if (index == 1) {
                    // A partially constructed graph already handed the lifetime to a consumer
                    // that started a DB-bound job. Real dispatcher: this job's unwinding runs
                    // inside buildGeneration's own runBlocking join, not on the test scheduler.
                    lifetime.childScope(Dispatchers.Unconfined).launch {
                        try {
                            awaitCancellation()
                        } finally {
                            touchedDatabases += database.toString()
                            protocolLog += "orphan-job-ended"
                        }
                    }
                }
            }

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            // closes: [0]=outgoing, [1]=the ORPHANED candidate database.
            val orphanClose = closeIndices()[1]
            assertTrue(
                protocolLog.indexOf("orphan-job-ended") in 0 until orphanClose,
                "the partial generation JOINED its jobs before closing the orphan: $protocolLog",
            )
            assertTrue(
                protocolLog.indexOf("db-touch-1") in 0 until orphanClose,
                "the job's finally used the orphan db BEFORE the close: $protocolLog",
            )
            assertEquals(
                listOf("AppDatabase#1"),
                touchedDatabases,
                "the job touched the ORPHANED database, not the outgoing one",
            )
        }

    // ------------------------------------------------------------------------------------------
    // Mandate 7 — admission.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `unreleased lease aborts pre-PONR - rejection, admission reopens, retry works`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val lease = runtime.awaitBackupWorkLease()

            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            advanceTimeBy(3_000)
            runCurrent()

            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                transaction.await(),
            )
            assertTrue("$rejected".contains("lease"), "reason names the lease: $rejected")
            assertTrue(protocolLog.isEmpty(), "nothing irreversible happened")
            assertSame(genOne, runtime.currentGeneration)
            lease.release()
            val retry =
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile("second.db")))
            assertInstanceOf(ReplacementOutcome.Completed::class.java, retry)
        }

    @Test
    fun `worker suspended in the closed window binds to the NEW generation`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            runCurrent() // quiesce closed admission; the transaction parked at preflight
            // UNCONFINED acquirer: the reopen's StateFlow write resumes it SYNCHRONOUSLY inside
            // the reopen call — so this test is ORDER-SENSITIVE: had the machine reopened
            // admission before publishing the successor, the lease would bind the OUTGOING
            // (closing) generation and the assertion below would fail.
            val leaseCall = async(UnconfinedTestDispatcher(testScheduler)) {
                runtime.awaitBackupWorkLease()
            }
            runCurrent()
            assertFalse(leaseCall.isCompleted, "admission is CLOSED during the window")

            gate.complete(Unit)
            runCurrent()

            val genTwo = (transaction.await() as ReplacementOutcome.Completed).generation!!
            assertSame(genTwo.graph, leaseCall.await().deps, "bound to the SUCCESSOR atomically")
        }

    @Test
    fun `late ui attach after the zero observation is REFUSED - the retired id never reopens`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            runCurrent() // zero was observed; gen 1's id was retired atomically with it

            runtime.onUiGenerationAttached(genOne.id) // the late attach — must not pass
            assertEquals(0, runtime.uiAttachmentCount(genOne.id), "refused, not counted")

            gate.complete(Unit)
            runCurrent()
            assertInstanceOf(ReplacementOutcome.Completed::class.java, transaction.await())
        }

    @Test
    fun `attach BEFORE the zero observation blocks the transition until disposed`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            runtime.onUiGenerationAttached(genOne.id)

            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            runCurrent()
            assertTrue(transaction.isActive, "the attached region gates the whole machine")
            assertTrue(protocolLog.isEmpty(), "nothing irreversible while the UI holds on")

            runtime.onUiGenerationDisposed(genOne.id)
            runCurrent()
            assertInstanceOf(ReplacementOutcome.Completed::class.java, transaction.await())
        }

    @Test
    fun `aborted transition reopens ui admission for the outgoing id`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val lease = runtime.awaitBackupWorkLease()
            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            advanceTimeBy(3_000)
            runCurrent()
            assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                transaction.await(),
            )

            runtime.onUiGenerationAttached(genOne.id)
            assertEquals(1, runtime.uiAttachmentCount(genOne.id), "un-retired on abort")
            runtime.onUiGenerationDisposed(genOne.id)
            lease.release()
        }

    @Test
    fun `committed handover advances the snackbar epoch - an abort never does`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val lease = runtime.awaitBackupWorkLease()
            val aborted = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            advanceTimeBy(3_000)
            runCurrent()
            assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                aborted.await(),
            )
            assertEquals(0, epochAdvances, "abort preserves the queued snackbar models")

            lease.release()
            runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile("second.db")))
            assertEquals(1, epochAdvances, "commit discards the outgoing generation's queue")
        }

    // ------------------------------------------------------------------------------------------
    // Mandates 6 + 8 — single-flight and Fatal under concurrency.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `simultaneous same-operation submissions share ONE transaction and one staging`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val source = sourceFile()
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val first = async { runtime.replace(ReplacementOperation.RestoreFromSnapshot(source)) }
            runCurrent()
            val second = async { runtime.replace(ReplacementOperation.RestoreFromSnapshot(source)) }
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertSame(first.await(), second.await(), "one transaction, one outcome object")
            assertEquals(1, preflightCalls)
        }

    @Test
    fun `different operation gets its OWN serialized result - never the other operation's`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // No preserved file exists → the rollback op must reject on ITS OWN terms. It is
            // submitted FIRST because a committed restore legitimately CREATES the undo slot
            // (its reservation is promoted there), which would hide the rejection under it.
            val rollback = async {
                runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup())
            }
            val restore = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            runCurrent()

            assertInstanceOf(ReplacementOutcome.Completed::class.java, restore.await())
            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                rollback.await(),
            )
            assertInstanceOf(BackupError.CorruptedBackup::class.java, rejected.error)
        }

    @Test
    fun `A reaches Fatal while B is queued - B performs no validation, close, swap or publish`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            preservedFile()
            closeFailureIndices += 0 // A's outgoing close throws → Fatal (post-PONR)
            // Hold A inside its machine (UI gate) so B queues behind the mutex.
            runtime.onUiGenerationAttached(genOne.id)

            val a = async { runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile())) }
            runCurrent()
            val b = async { runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup()) }
            runCurrent()
            runtime.onUiGenerationDisposed(genOne.id)
            runCurrent()

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, a.await())
            assertInstanceOf(ReplacementOutcome.Fatal::class.java, b.await())
            coVerify(exactly = 0) { provider.getPreRestoreBackupFile() }
            assertEquals(0, protocolLog.count { it == "swap" }, "no swap ran at all")
            assertEquals(RuntimePhase.Fatal, runtime.phases.value, "nothing overwrote Fatal")
            assertTrue(File(tempDir, "pre_restore_backup.db").exists(), "B deleted nothing")
        }

    @Test
    fun `replace after Fatal returns Fatal - never a cleanup-safe rejection`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            closeFailureIndices += 0
            runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            val effects = RecordingEffects()

            val after = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile("late.db")),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, after)
            // Phase-aware Fatal dispatch: this transaction performed NOTHING (it landed on an
            // already-Fatal runtime), so NO compensation runs — `onFatal` would let the caller
            // journal a mutation that never happened; the rejection-compensation would delete
            // the recovery assets the fatal transaction's next process needs.
            assertEquals(emptyList<String>(), effects.calls, "no compensation for a no-op Fatal")
        }

    @Test
    fun `reinitialize after Fatal returns Fatal and publishes nothing`() = runtimeTest { runtime ->
        runtime.currentGeneration
        closeFailureIndices += 0
        runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
        assertEquals(RuntimePhase.Fatal, runtime.phases.value)

        val outcome = runtime.reinitialize()

        assertEquals(ReinitializeOutcome.Fatal, outcome)
        assertEquals(RuntimePhase.Fatal, runtime.phases.value)
    }

    @Test
    fun `Fatal rejects reads and admission loudly - the acquirer never parks forever`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            closeFailureIndices += 0
            runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertThrows<IllegalStateException> { runtime.currentGeneration }
            val lease = async { runCatching { runtime.awaitBackupWorkLease() } }
            runCurrent()
            assertTrue(lease.isCompleted, "the acquirer must fail loud, not park forever")
            assertInstanceOf(IllegalStateException::class.java, lease.await().exceptionOrNull())
        }

    @Test
    fun `restart-process policy - validate, close, swap, no rebuild, no phase change`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            val genOne = runtime.currentGeneration

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertNull(completed.generation, "no in-process successor under RestartProcess")
            assertEquals(1, databases.size, "no rebuild")
            assertEquals(0, preflightCalls)
            assertSame(genOne, (runtime.phases.value as RuntimePhase.Serving).generation)
        }

    @Test
    fun `staging happens in the SUBMISSION frame - before the host coroutine ever runs`() =
        runtimeTest(standardHostDispatcher = true) { runtime ->
            runtime.currentGeneration
            val source = sourceFile()

            // UNDISPATCHED: the caller runs synchronously up to replace()'s await; the STANDARD
            // host dispatcher guarantees the transaction body has NOT started. Ownership must
            // already have transferred — an implementation that staged inside the host body
            // would leave the original file in place here and fail.
            val caller = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(source))
            }
            assertFalse(source.exists(), "the submission frame consumed the original path")
            assertEquals(1, stagedFiles().size, "the runtime owns the staged copy already")

            caller.cancel()
            source.delete() // the dead caller's cleanup — a no-op against the staged copy
            runCurrent()
            val serving = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertEquals(2, serving.generation.id)
            assertTrue(stagedFiles().isEmpty())
        }

    @Test
    fun `terminal effects hold the transition mutex - a successor cannot interleave them`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile()
            val terminalGate = CompletableDeferred<Unit>()
            // T1: a validation-rejected restore whose REJECTION COMPENSATION suspends.
            coEvery { provider.validateSnapshotForRestore(any()) } returns BackupResult.Failure(
                BackupError.CorruptedBackup(reason = "magic mismatch"),
            )
            val t1Effects = object : DatabaseReplacementEffects {
                override val attemptId: String = "t1"

                override suspend fun onRejectedBeforeMutation(error: BackupError) {
                    terminalGate.await()
                }
            }
            val t2Effects = RecordingEffects(attemptId = "t2")

            val t1 = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()), t1Effects)
            }
            runCurrent() // T1 is parked INSIDE its terminal effect, still holding the mutex
            val t2 = async {
                runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup(), t2Effects)
            }
            runCurrent()

            // The seam contract: T2 must not enter the machine (no beforeMutation) while T1's
            // compensation is pending — otherwise T1's cleanup could erase T2's crash-safety
            // marker or delete the preserved snapshot mid-swap.
            assertEquals(emptyList<String>(), t2Effects.calls, "T2 blocked behind T1's terminal")
            assertFalse(t2.isCompleted)

            terminalGate.complete(Unit)
            runCurrent()
            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, t1.await())
            assertInstanceOf(ReplacementOutcome.Completed::class.java, t2.await())
            assertTrue(t2Effects.calls.first() == "beforeMutation")
        }

    @Test
    fun `rollback mechanics failure - Fatal with fatal effects`() = runtimeTest { runtime ->
        runtime.currentGeneration
        preservedFile()
        coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
            protocolLog += "swap"
            BackupResult.Failure(BackupError.Io(IOException("disk full")))
        }
        val effects = RecordingEffects()

        val outcome =
            runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()), effects)

        assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
        assertEquals(listOf("beforeMutation", "fatal"), effects.calls)
        assertEquals(RuntimePhase.Fatal, runtime.phases.value)
    }

    private companion object {
        /** The bytes a reserved rollback snapshot carries — the pre-attempt database stand-in. */
        const val RESERVATION_CONTENT = "res"
    }
}
