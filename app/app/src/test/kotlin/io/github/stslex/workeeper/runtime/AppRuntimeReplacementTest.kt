// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
 * Replacement-transaction pins for the round-2 protocol: submission-owned bodies with staged
 * source ownership, typed effects executed exactly once on the transaction coroutine, PONR at
 * the START of the first irreversible action (teardown / close invocation / rename),
 * close-throw → Fatal, truthful RecoveredByRollback vs Committed, closed admission (leases +
 * atomic UI retire), and Fatal that is terminal under concurrency.
 */
internal class AppRuntimeReplacementTest {

    private class ProbeViewModel(val onClear: () -> Unit = {}) : ViewModel() {
        override fun onCleared() = onClear()
    }

    /** Recording effects double — one label per protocol phase, order-preserving. */
    private class RecordingEffects(
        private val onBefore: suspend () -> Unit = {},
        private val onCommittedBody: suspend () -> Unit = {},
    ) : DatabaseReplacementEffects {
        val calls = mutableListOf<String>()

        override suspend fun onBeforeMutation() {
            calls += "beforeMutation"
            onBefore()
        }

        override suspend fun onRejectedBeforeMutation(error: BackupError) {
            calls += "rejected"
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

    /** Interleaving recorder: closes, job-ends and swaps push labels here. */
    private val protocolLog = mutableListOf<String>()

    private val provider = mockk<DatabaseSnapshotProvider>(relaxed = true)

    private var preflightCalls = 0
    private val preflightOutcomes = ArrayDeque<StartupOutcome>()
    private var preflightAction: (suspend (RuntimeGeneration) -> Unit)? = null
    private var preflightGate: CompletableDeferred<Unit>? = null

    private var epochAdvances = 0

    private fun sourceFile(name: String = "restore_source.db"): File =
        File(tempDir, name).apply { writeText("snapshot-bytes") }

    private fun preservedFile(): File =
        File(tempDir, "pre_restore_backup.db").apply { writeText("preserved-bytes") }

    private fun runtimeTest(
        replacementPolicy: ReplacementPolicy = ReplacementPolicy.RebuildInProcess,
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
            File(tempDir, "pre_restore_backup.db").delete()
            BackupResult.Success(Unit)
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = AppRuntime(
            applicationContext = context,
            dbFactory = {
                val db = mockk<AppDatabase>(relaxed = true)
                databases += db
                db
            },
            imageStorageFactory = { mockk<ImageStorage>(relaxed = true) },
            graphFactory = { _, _, _, _, _ ->
                check(builtGraphs++ !in graphFactoryFailures) { "injected graph construction failure" }
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
                hostDispatcher = dispatcher,
                stagingDirectory = { tempDir },
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        body(runtime)
    }

    private fun stagedFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("staged_restore_") }

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
                listOf("beforeMutation", "recovered"),
                effects.calls,
                "recovered-by-rollback effects — never onCommitted",
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

            val outcome = runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup, effects)

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(listOf("beforeMutation", "committed"), effects.calls)
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

            assertEquals(ReplacementOutcome.Fatal, outcome)
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

            assertEquals(ReplacementOutcome.Fatal, transaction.await())
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

            assertEquals(ReplacementOutcome.Fatal, outcome)
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

            assertEquals(ReplacementOutcome.Fatal, outcome)
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
            val leaseCall = async { runtime.awaitBackupWorkLease() }
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
            // No preserved file exists → the rollback op must reject on ITS OWN terms.
            val restore = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            val rollback = async {
                runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup)
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
            val b = async { runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup) }
            runCurrent()
            runtime.onUiGenerationDisposed(genOne.id)
            runCurrent()

            assertEquals(ReplacementOutcome.Fatal, a.await())
            assertEquals(ReplacementOutcome.Fatal, b.await(), "B did nothing and reported Fatal")
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

            assertEquals(ReplacementOutcome.Fatal, after)
            assertEquals(listOf("fatal"), effects.calls, "no rejection-compensation after Fatal")
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

        assertEquals(ReplacementOutcome.Fatal, outcome)
        assertEquals(listOf("beforeMutation", "fatal"), effects.calls)
        assertEquals(RuntimePhase.Fatal, runtime.phases.value)
    }
}
