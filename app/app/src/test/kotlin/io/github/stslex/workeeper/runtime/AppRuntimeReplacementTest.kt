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

/** Replacement transaction regression tests for ownership, ordering, recovery, and terminal truth. */
internal class AppRuntimeReplacementTest {

    private class ProbeViewModel(val onClear: () -> Unit = {}) : ViewModel() {
        override fun onCleared() = onClear()
    }

    /** Typed factory for [ProbeViewModel] — `Class.cast` keeps it suppression-free. */
    private class ProbeViewModelFactory(
        private val onClear: () -> Unit,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            requireNotNull(modelClass.cast(ProbeViewModel(onClear)))
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
        // The liveness pin substitutes a dispatcher that ACCEPTS but never RUNS the clear.
        mainDispatcherOverride: kotlinx.coroutines.CoroutineDispatcher? = null,
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
            protocolLog += "promote"
            // Mirrors the R4 production semantics: promotion COPIES onto the canonical slot and
            // the reservation SURVIVES — the runtime deletes it only after the durable
            // `Committed` record (the real-file crash-window pins live in
            // DatabaseSnapshotProviderImplTest).
            firstArg<File>().copyTo(File(tempDir, "pre_restore_backup.db"), overwrite = true)
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
                mainDispatcher = mainDispatcherOverride ?: dispatcher,
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

    private fun stagedReservations(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("reservation_") }

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
            // R4: the reservation must still EXIST at the moment the durable commit is
            // recorded — it is the file the still-`Prepared` journal names, and destroying it
            // before `Committed` lands (the old move-based promotion) left a crash window in
            // which recovery was misdirected onto the canonical slot's OLDER snapshot.
            var reservationAtRecordTime: Boolean? = null
            val effects = RecordingEffects(
                onMutationCommittedBody = {
                    reservationAtRecordTime = reservations.single().exists()
                },
            )

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            val reservation = reservations.single()
            assertEquals(
                RESERVATION_CONTENT,
                File(tempDir, "pre_restore_backup.db").readText(),
                "the undo slot holds the db that IMMEDIATELY preceded this restore",
            )
            assertEquals(
                true,
                reservationAtRecordTime,
                "the journal-named reservation must survive the promotion until Committed is durable",
            )
            assertFalse(
                reservation.exists(),
                "the retained reservation is deleted only AFTER the durable record",
            )
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
    fun `a requested rollback whose record persistently fails ends FATAL - every asset preserved`() =
        runtimeTest { runtime ->
            // R4.2: a pre-durable failure is NEVER dispatched as a committed terminal. The
            // primary commit's record fails, the bounded recovery retries the record once, and
            // a persistent failure ends Fatal — journal `Prepared`, canonical retained, the
            // committed terminal (`onCommitted`) never invoked.
            runtime.currentGeneration
            preservedFile(content = "UNDO")
            val effects = RecordingEffects(
                onMutationCommittedBody = { error("durable journal write failed") },
            )

            val outcome = runtime.replace(
                ReplacementOperation.RollbackToPreRestoreBackup(),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertFalse(
                effects.calls.contains("committed"),
                "the committed terminal must NOT run over a non-durable commit: ${effects.calls}",
            )
            coVerify(exactly = 0) { provider.deletePreRestoreBackup() }
            assertEquals(
                "UNDO",
                File(tempDir, "pre_restore_backup.db").readText(),
                "nothing was consumed — the next launch can still roll back",
            )
            assertEquals(
                listOf("beforeMutation", "mutationCommitted", "mutationCommitted", "fatal"),
                effects.calls,
                "one primary record try, one bounded retry, then the Fatal terminal",
            )
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
                listOf("beforeMutation", "recovered"),
                effects.calls,
                """
                the ladder's rollback must NOT record the CALLER's attempt as committed: what
                committed is the rollback, and a `Committed` restore attempt would let the very
                next preflight peek the rolled-back file and publish a false RestoreSuccess
                """.trimIndent(),
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
            val factory = ProbeViewModelFactory(onClear = { probeCleared = true })
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

            val late = runtime.admitUiGeneration(genOne.id) // the late attach — must not pass
            assertNull(late, "a retired generation must refuse admission outright")
            assertEquals(0, runtime.uiAttachmentCount(genOne.id), "refused, not counted")

            gate.complete(Unit)
            runCurrent()
            assertInstanceOf(ReplacementOutcome.Completed::class.java, transaction.await())
        }

    @Test
    fun `attach BEFORE the zero observation blocks the transition until disposed`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val token = requireNotNull(runtime.admitUiGeneration(genOne.id))

            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            runCurrent()
            assertTrue(transaction.isActive, "the admitted region gates the whole machine")
            assertTrue(protocolLog.isEmpty(), "nothing irreversible while the UI holds on")

            runtime.releaseUiGeneration(token)
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

            val readmitted = requireNotNull(runtime.admitUiGeneration(genOne.id))
            assertEquals(1, runtime.uiAttachmentCount(genOne.id), "un-retired on abort")
            runtime.releaseUiGeneration(readmitted)
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
            val holdToken = requireNotNull(runtime.admitUiGeneration(genOne.id))

            val a = async { runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile())) }
            runCurrent()
            val b = async { runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup()) }
            runCurrent()
            runtime.releaseUiGeneration(holdToken)
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
    fun `the ladder rolls back onto THIS attempt's reservation, not the older canonical slot`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // The canonical slot holds a PREVIOUS restore's (older) snapshot; the reservation
            // holds the true pre-attempt database. A failed swap leaves the live file untouched,
            // so applying the canonical slot would revert data the failure never touched.
            File(tempDir, "pre_restore_backup.db").writeText("PREVIOUS-RESTORE-SNAPSHOT")
            val applied = mutableListOf<String>()
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                applied += firstArg<File>().readText()
                if (swaps++ == 0) {
                    BackupResult.Failure(BackupError.Io(IOException("rename failed")))
                } else {
                    BackupResult.Success(Unit)
                }
            }

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(2, applied.size)
            assertEquals(
                "res",
                applied[1],
                "the recovery must apply the attempt's OWN reservation: $applied",
            )
            assertTrue(
                File(tempDir, "pre_restore_backup.db").exists(),
                "the previous restore's undo slot was not the source and must survive",
            )
        }

    @Test
    fun `the reservation is promoted BEFORE the durable commit is recorded`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val effects = RecordingEffects(calls = protocolLog)

            runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()), effects)

            // Recording `Committed` first would let a crash inside the promotion leave a journal
            // that claims success while the undo slot is mid-replacement: the next launch would
            // peek the new database, publish RestoreSuccess and offer an undo that cannot work.
            val promote = protocolLog.indexOf("promote")
            val recorded = protocolLog.indexOf("mutationCommitted")
            assertTrue(promote >= 0 && recorded >= 0, "both steps must run: $protocolLog")
            assertTrue(promote < recorded, "promote must precede the record: $protocolLog")
            assertTrue(
                protocolLog.indexOf("swap") < promote,
                "and the live-file swap must precede both: $protocolLog",
            )
        }

    @Test
    fun `a failed promotion recovers by rollback - the committed terminal NEVER runs`() =
        runtimeTest { runtime ->
            // R4.2: promotion failure = pre-durable failure. The mutation is not provable, so
            // the candidate must not serve it: the bounded recovery rolls back onto the KEPT
            // reservation deterministically, and the outcome is truthful restore-FAILURE —
            // `onCommitted` (which production effects use to resolve/clear/publish) never runs.
            runtime.currentGeneration
            coEvery { provider.promoteRollbackReservation(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("promotion failed")),
            )
            val applied = mutableListOf<String>()
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                applied += firstArg<File>().readText()
                BackupResult.Success(Unit)
            }
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(
                listOf("beforeMutation", "recovered"),
                effects.calls,
                "no durable record was attempted after the failed promotion, and the committed " +
                    "terminal never ran: ${effects.calls}",
            )
            assertEquals(
                RESERVATION_CONTENT,
                applied.last(),
                "the recovery applied the KEPT reservation — the pre-attempt image",
            )
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

    // Source-owner identity and exact-file consumption.

    @Test
    fun `a MISSING journal-named rollback source is a typed rejection - the canonical slot is never substituted`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // Canonical B belongs to another attempt; missing explicit A must not select it.
            preservedFile(content = "OLDER-CANONICAL-B")
            val missingA = File(tempDir, "rollback_reservation_gone.db")
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RollbackToPreRestoreBackup(missingA.absolutePath),
                effects,
            )

            val rejected =
                assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
            assertInstanceOf(BackupError.CorruptedBackup::class.java, rejected.error)
            assertTrue(
                "$rejected".contains("journal-named"),
                "the rejection must name the missing journal source: $rejected",
            )
            assertEquals(0, protocolLog.count { it == "swap" }, "NOTHING may be applied")
            assertEquals(
                "OLDER-CANONICAL-B",
                File(tempDir, "pre_restore_backup.db").readText(),
                "the other attempt's canonical slot is untouched — neither applied nor consumed",
            )
            assertEquals(listOf("rejected"), effects.calls)
        }

    @Test
    fun `an explicit rollback applies and consumes EXACTLY its named source - the canonical survives`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // Distinct sentinels: A is the journal-named reservation, B the canonical slot a
            // PREVIOUS restore left. The committed rollback must apply A, consume A, touch B
            // in no way.
            val sourceA = File(tempDir, "rollback_reservation_A.db").apply { writeText("A") }
            preservedFile(content = "B")
            val applied = mutableListOf<String>()
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                applied += firstArg<File>().readText()
                BackupResult.Success(Unit)
            }
            val effects = RecordingEffects()

            val outcome = runtime.replace(
                ReplacementOperation.RollbackToPreRestoreBackup(sourceA.absolutePath),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(listOf("A"), applied, "the applied bytes are the NAMED source's")
            assertFalse(sourceA.exists(), "the exact applied file was consumed")
            assertEquals(
                "B",
                File(tempDir, "pre_restore_backup.db").readText(),
                "the previous attempt's canonical undo survives byte-for-byte",
            )
            coVerify(exactly = 0) { provider.deletePreRestoreBackup() }
            assertEquals(listOf("beforeMutation", "mutationCommitted", "committed"), effects.calls)
        }

    @Test
    fun `a rejection whose terminal compensation fails KEEPS the reservation the journal names`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val lease = runtime.awaitBackupWorkLease() // the quiesce will time out on it
            // The terminal resolveAttempt THROWS: the journal is left at `Prepared`, still
            // naming this attempt's reservation — deleting the file would strand the next
            // launch's recovery exactly like a failed commit record would (R4 invariant 8).
            val effects = RecordingEffects(onRejectedBody = { error("journal resolve failed") })

            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()), effects)
            }
            advanceTimeBy(3_000)
            runCurrent()

            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                transaction.await(),
            )
            assertNotNull(rejected.effectsError, "the failed compensation is surfaced")
            assertEquals(
                1,
                reservationFiles().size,
                "an unresolved journal may still name this reservation — it must survive",
            )
            lease.release()
        }

    @Test
    fun `ladder recovery whose reservation VANISHED goes Fatal - never the older canonical`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile(content = "OLDER-CANONICAL")
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                if (swaps++ == 0) {
                    // The primary swap fails AND the reservation disappears mid-transaction
                    // (cache eviction) — the one state in which the old code substituted the
                    // canonical slot and silently reverted data this attempt never touched.
                    reservations.single().delete()
                    BackupResult.Failure(BackupError.Io(IOException("rename failed")))
                } else {
                    BackupResult.Success(Unit)
                }
            }

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(1, protocolLog.count { it == "swap" }, "no substitute was ever applied")
            assertEquals(
                "OLDER-CANONICAL",
                File(tempDir, "pre_restore_backup.db").readText(),
                "the other attempt's canonical slot is untouched",
            )
        }

    @Test
    fun `ladder recovery applies the reservation WITHOUT consuming it - the journal still names it until resolve`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile(content = "OLDER-CANONICAL")
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                if (swaps++ == 0) {
                    BackupResult.Failure(BackupError.Io(IOException("rename failed")))
                } else {
                    // At the moment the ROLLBACK swap runs, the reservation being applied must
                    // still exist afterwards: the caller's terminal effects have not resolved
                    // the journal yet, and a crash right here must leave the named file behind.
                    BackupResult.Success(Unit)
                }
            }
            var reservationAtRecoveredTime: Boolean? = null
            val effects = object : DatabaseReplacementEffects {
                override val attemptId: String = "attempt-1"
                override suspend fun onRecoveredByRollback(error: BackupError) {
                    reservationAtRecoveredTime = reservations.single().exists()
                }
            }

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(
                true,
                reservationAtRecoveredTime,
                "the reservation must outlive the rollback until the terminal effects resolved " +
                    "the journal — a crash before then recovers from exactly this file",
            )
            assertTrue(
                reservationFiles().isEmpty(),
                "after the clean terminal effects, the submission frame discards it",
            )
        }

    // Inline rollback shares top-level source and journal rules.

    @Test
    fun `the INLINE rollback honors the journal-named source - another attempt's canonical is never applied`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // Explicit source A is authoritative; canonical B belongs to an earlier attempt.
            preservedFile(content = "B-OLDER-CANONICAL")
            val sourceA = File(tempDir, "rollback_reservation_inline.db")
                .apply { writeText("A-JOURNAL-NAMED") }
            val applied = mutableListOf<String>()
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                applied += firstArg<File>().readText()
                swaps++
                BackupResult.Success(Unit)
            }
            val inlineEffects = RecordingEffects(attemptId = "inline-attempt", calls = protocolLog)
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = { _ ->
                if (preflightCalls == 1) {
                    inlineResult = runtime.rollbackToPreRestoreBackup(
                        sourcePath = sourceA.absolutePath,
                        effects = inlineEffects,
                    )
                }
            }
            preflightOutcomes += StartupOutcome.RestartRequired

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(DatabaseReplacementResult.Committed::class.java, inlineResult)
            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(
                "A-JOURNAL-NAMED",
                applied[1],
                "the inline rollback must apply the SUBMITTED source: $applied",
            )
            assertFalse(sourceA.exists(), "the exact applied file was consumed")
            // By inline time the outer restore's CLEAN COMMIT legally promoted its own
            // reservation onto the canonical slot (overwriting B) — the inline rollback must
            // still neither apply nor consume that slot.
            assertEquals(
                RESERVATION_CONTENT,
                File(tempDir, "pre_restore_backup.db").readText(),
                "the canonical slot (this attempt's own promoted pre-image) is untouched",
            )
            // Journal honesty: the re-claim ran BEFORE the inline mutation, so a death inside
            // it replays the truthful rollback bookkeeping instead of the restore's stale phase.
            val inlineClaim = protocolLog.indexOf("beforeMutation")
            val inlineSwap = protocolLog.lastIndexOf("swap")
            assertTrue(
                inlineClaim in 0 until inlineSwap,
                "the inline rollback must re-claim the journal before mutating: $protocolLog",
            )
        }

    @Test
    fun `the INLINE rollback with a MISSING journal-named source rejects - no substitution`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preservedFile(content = "B-OLDER-CANONICAL")
            val missing = File(tempDir, "rollback_reservation_gone.db")
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = { _ ->
                if (preflightCalls == 1) {
                    inlineResult = runtime.rollbackToPreRestoreBackup(
                        sourcePath = missing.absolutePath,
                        effects = RecordingEffects(attemptId = "inline-attempt"),
                    )
                }
            }

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            val rejected = assertInstanceOf(
                DatabaseReplacementResult.RejectedBeforeMutation::class.java,
                inlineResult,
            )
            assertTrue("$rejected".contains("journal-named"), "typed rejection: $rejected")
            // The restore itself proceeds (its preflight returned Proceed by default).
            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            // The canonical holds the restore's own promoted pre-image — the rejected inline
            // rollback neither applied nor consumed it.
            assertEquals(
                RESERVATION_CONTENT,
                File(tempDir, "pre_restore_backup.db").readText(),
                "the canonical slot was never applied nor consumed",
            )
        }

    @Test
    fun `post-clean-commit recovery durably UN-commits before rolling back and spares the canonical`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // A cleanly committed restore leaves the journal at Committed — the exact
            // false-success record the recovery must retract BEFORE un-doing the data: a death
            // between the rollback swap and the terminal effects would otherwise let the next
            // launch peek the rolled-back file and publish RestoreSuccess (R4 review).
            graphFactoryFailures += 1 // candidate #1 fails AFTER the clean commit
            val effects = RecordingEffects(calls = protocolLog)

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            // Two beforeMutation entries: the restore's claim, then the recovery's UN-commit.
            val claims = protocolLog.withIndex()
                .filter { it.value == "beforeMutation" }
                .map { it.index }
            assertEquals(2, claims.size, "the recovery must re-claim (un-commit): $protocolLog")
            val secondSwap = protocolLog.withIndex()
                .filter { it.value == "swap" }
                .map { it.index }[1]
            assertTrue(
                claims[1] < secondSwap,
                "the un-commit must land BEFORE the rollback swap: $protocolLog",
            )
            assertTrue(
                File(tempDir, "pre_restore_backup.db").exists(),
                "the canonical is applied WITHOUT being consumed — the follow-up honest " +
                    "recovery is what consumes it",
            )
        }

    @Test
    fun `a preflight that re-drives the recovery inline gets ONE fresh attempt - then publishes`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            graphFactoryFailures += 1 // candidate #1 fails after the clean commit
            // Candidate #2's preflight plays the recovering coordinator: it finds the
            // un-committed journal and re-drives the rollback INLINE (consuming the canonical,
            // resolving the journal), which invalidates candidate #2. The machine must then
            // run ONE fresh attempt over the now-empty journal and publish — not go Fatal.
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = { _ ->
                if (preflightCalls == 1) {
                    inlineResult = runtime.rollbackToPreRestoreBackup(
                        sourcePath = null,
                        effects = RecordingEffects(attemptId = "recovery-attempt"),
                    )
                }
            }
            preflightOutcomes += StartupOutcome.RestartRequired // candidate #2's verdict

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(DatabaseReplacementResult.Committed::class.java, inlineResult)
            val recovered =
                assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(2, preflightCalls, "one fresh attempt ran after the inline recovery")
            assertNotNull(recovered.generation, "the fresh attempt PUBLISHED — never Fatal here")
            assertFalse(
                File(tempDir, "pre_restore_backup.db").exists(),
                "the inline recovery consumed the canonical it applied",
            )
        }

    @Test
    fun `a generation-1 orphan close-throw is FATAL - never a retryable rejection`() =
        runtimeTest { runtime ->
            // R4 review: the first build's graph fails AND the orphan database's close throws —
            // an unknown-state handle over the live file. Resolving this to a cleanup-safe
            // rejection would let a retry open a NEW handle beside the leaked one and later
            // rename over it.
            graphFactoryFailures += 0
            closeFailureIndices += 0

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            assertThrows<IllegalStateException> { runtime.currentGeneration }
        }

    // Rollback replay and bounded-clear liveness pins.

    /** A minimal durable-journal double with the real ownership rules, for composed pins. */
    private class FakeJournal {
        var id: String? = null
        var kind: String? = null
        var phase: String? = null
        var path: String? = null

        fun begin(attemptId: String, attemptKind: String, sourcePath: String?): Boolean {
            if (id != null && id != attemptId) return false
            id = attemptId
            kind = attemptKind
            phase = "Prepared"
            path = sourcePath
            return true
        }

        fun commit(attemptId: String): Boolean {
            if (id != attemptId) return false
            phase = "Committed"
            return true
        }

        fun resolve(attemptId: String): Boolean {
            if (id != attemptId) return false
            id = null
            kind = null
            phase = null
            path = null
            return true
        }
    }

    /** Journal-backed effects double; [failRecordTimes] injects durable-record failure. */
    private inner class JournalEffects(
        override val attemptId: String,
        private val journal: FakeJournal,
        private val kind: String,
        private val log: MutableList<String>,
        private var failRecordTimes: Int = 0,
        private val sourcePathOverride: String? = null,
    ) : DatabaseReplacementEffects {

        var availabilityCleared = false
        var successPublished = false
        var acknowledged = false

        override suspend fun onBeforeMutation(rollbackSnapshotPath: String) {
            val path = sourcePathOverride ?: rollbackSnapshotPath.takeIf { it.isNotEmpty() }
            check(journal.begin(attemptId, kind, path)) {
                "journal slot owned by ${journal.id}"
            }
            log += "journal-prepared"
        }

        override suspend fun onMutationCommitted() {
            if (failRecordTimes > 0) {
                failRecordTimes--
                log += "journal-record-FAILED"
                error("durable record failed")
            }
            check(journal.commit(attemptId)) { "journal not owned by $attemptId" }
            log += "journal-committed"
        }

        override suspend fun onCommitted() {
            // The REAL shape: this erases exactly the state conservative recovery needs.
            journal.resolve(attemptId)
            availabilityCleared = true
            successPublished = true
            acknowledged = true
            log += "terminal-committed"
        }

        override suspend fun onRecoveredByRollback(error: BackupError) {
            journal.resolve(attemptId)
            log += "terminal-recovered"
        }

        override suspend fun onFailedAfterMutation(error: BackupError) {
            log += "terminal-failed-after-mutation"
        }

        override suspend fun onFatal() {
            log += "terminal-fatal"
        }
    }

    @Test
    fun `a requested rollback's successful retry COMMITS as the requested operation - never as compensation`() =
        runtimeTest { runtime ->
            // Retry must commit through the original rollback effects.
            runtime.currentGeneration
            preservedFile(content = "UNDO")
            val journal = FakeJournal()
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                if (swaps++ == 0) {
                    BackupResult.Failure(BackupError.Io(IOException("transient rename failure")))
                } else {
                    BackupResult.Success(Unit)
                }
            }
            coEvery { provider.deletePreRestoreBackup() } coAnswers {
                protocolLog += "deletePreRestoreBackup"
                File(tempDir, "pre_restore_backup.db").delete()
            }
            val observedPhases = mutableListOf<String>()
            preflightAction = { _ ->
                observedPhases += "${journal.phase}/${journal.kind}"
                if (journal.phase == "Prepared") {
                    // The production-shaped recovery: an unresolved Prepared attempt would be
                    // re-driven inline — which MUST NOT happen after an honest retry commit.
                    runtime.rollbackToPreRestoreBackup(
                        sourcePath = journal.path,
                        effects = JournalEffects("re-drive", journal, "Rollback", protocolLog),
                    )
                }
            }
            val effects = JournalEffects("undo-1", journal, "Rollback", protocolLog)

            val outcome = runtime.replace(
                ReplacementOperation.RollbackToPreRestoreBackup(),
                effects,
            )

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertNull(
                completed.effectsError,
                "a clean requested-rollback retry is a clean Completed — never Fatal, never " +
                    "RecoveredByRollback: $completed",
            )
            assertEquals(2, protocolLog.count { it == "swap" }, "two swaps, no third: $protocolLog")
            assertEquals(
                listOf("Committed/Rollback"),
                observedPhases,
                "the candidate preflight must observe the RETRY's durable commit",
            )
            val recorded = protocolLog.indexOf("journal-committed")
            val consumed = protocolLog.indexOf("deletePreRestoreBackup")
            assertTrue(
                recorded in 0 until consumed,
                "onMutationCommitted must precede the source consumption: $protocolLog",
            )
            assertEquals(
                1,
                protocolLog.count { it == "terminal-committed" },
                "the original committed terminal effects run exactly once: $protocolLog",
            )
            assertNull(journal.id, "the journal resolved through the ORIGINAL effects")
            assertNotNull(completed.generation, "a Serving successor published")
        }

    @Test
    fun `a retry whose record persistently FAILS never runs the real committed terminal`() =
        runtimeTest { runtime ->
            // A retry with no durable record must retain Prepared state and skip committed effects.
            runtime.currentGeneration
            preservedFile(content = "UNDO")
            val journal = FakeJournal()
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                if (swaps++ == 0) {
                    BackupResult.Failure(BackupError.Io(IOException("transient rename failure")))
                } else {
                    BackupResult.Success(Unit)
                }
            }
            val effects = JournalEffects(
                attemptId = "undo-1",
                journal = journal,
                kind = "Rollback",
                log = protocolLog,
                failRecordTimes = Int.MAX_VALUE,
            )

            val outcome = runtime.replace(
                ReplacementOperation.RollbackToPreRestoreBackup(),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(
                0,
                protocolLog.count { it == "terminal-committed" },
                "the committed terminal must never run over a non-durable commit: $protocolLog",
            )
            assertFalse(effects.availabilityCleared, "availability untouched")
            assertFalse(effects.successPublished, "no success published")
            assertFalse(effects.acknowledged, "the initiating action was never acknowledged")
            assertEquals("Prepared", journal.phase, "the attempt remains Prepared and owned")
            assertEquals("undo-1", journal.id)
            coVerify(exactly = 0) { provider.deletePreRestoreBackup() }
            assertEquals(
                "UNDO",
                File(tempDir, "pre_restore_backup.db").readText(),
                "the exact source is retained for the next recovery step",
            )
        }

    @Test
    fun `the INLINE rollback disposes the candidate through the ONE teardown protocol before the swap`() =
        runtimeTest { runtime ->
            // Candidate store clear and lifetime join must precede close and swap.
            runtime.currentGeneration
            val sourceA = File(tempDir, "rollback_reservation_inline.db")
                .apply { writeText("A-INLINE-SOURCE") }
            val applied = mutableListOf<String>()
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                protocolLog += "swap"
                applied += firstArg<File>().readText()
                BackupResult.Success(Unit)
            }
            var vmCleared = false
            preflightAction = { generation ->
                if (preflightCalls == 1) {
                    ViewModelProvider(
                        generation.viewModelStore,
                        ProbeViewModelFactory(onClear = {
                            vmCleared = true
                            protocolLog += "candidate-vm-cleared"
                        }),
                    )[ProbeViewModel::class.java]
                    generation.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                        try {
                            awaitCancellation()
                        } finally {
                            touchedDatabases += generation.database.toString()
                            protocolLog += "candidate-job-ended"
                        }
                    }
                    kotlinx.coroutines.yield()
                    runtime.rollbackToPreRestoreBackup(
                        sourcePath = sourceA.absolutePath,
                        effects = RecordingEffects(attemptId = "inline-1", calls = protocolLog),
                    )
                }
            }
            preflightOutcomes += StartupOutcome.RestartRequired

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertTrue(vmCleared, "the candidate ViewModelStore must be CLEARED")
            // closes: [0]=outgoing, [1]=the candidate via the disposal. Order: VM clear and the
            // job's finally BEFORE the candidate close, and the close BEFORE the inline swap.
            val candidateClose = closeIndices()[1]
            val inlineSwap = protocolLog.lastIndexOf("swap")
            assertTrue(
                protocolLog.indexOf("candidate-vm-cleared") in 0 until candidateClose,
                "VM clear before the candidate close: $protocolLog",
            )
            assertTrue(
                protocolLog.indexOf("candidate-job-ended") in 0 until candidateClose,
                "the candidate job's finally must JOIN before its database closes: $protocolLog",
            )
            assertTrue(candidateClose < inlineSwap, "close before the swap: $protocolLog")
            assertEquals("A-INLINE-SOURCE", applied.last(), "the exact submitted source applied")
            assertFalse(sourceA.exists(), "and consumed")
        }

    @Test
    fun `an UNJOINABLE candidate stops the inline rollback FATAL - zero renames after admission`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val sourceA = File(tempDir, "rollback_reservation_inline.db")
                .apply { writeText("A-INLINE-SOURCE") }
            val never = CompletableDeferred<Unit>()
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = { generation ->
                if (preflightCalls == 1) {
                    generation.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                        withContext(NonCancellable) { never.await() }
                    }
                    kotlinx.coroutines.yield()
                    inlineResult = runtime.rollbackToPreRestoreBackup(
                        sourcePath = sourceA.absolutePath,
                        effects = RecordingEffects(attemptId = "inline-1", calls = protocolLog),
                    )
                }
            }

            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            advanceTimeBy(10_000)
            runCurrent()

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, transaction.await())
            assertInstanceOf(
                DatabaseReplacementResult.FailedAfterMutation::class.java,
                inlineResult,
            )
            assertEquals(
                1,
                protocolLog.count { it == "swap" },
                "the restore's own swap only — ZERO renames after the failed teardown: $protocolLog",
            )
            assertTrue(sourceA.exists(), "the journal-named source is preserved")
            assertEquals(RuntimePhase.Fatal, runtime.phases.value, "no generation published")
            assertEquals(0, epochAdvances, "the epoch must not advance")
            assertNull(
                runtime.admitUiGeneration(1),
                "UI admission stays retired for the outgoing id",
            )
            never.complete(Unit)
            runCurrent()
        }

    @Test
    fun `a clear that is QUEUED but never RUN cannot hang the machine - Fatal within the drain budget`() {
        // Retain accepted work to prove a late clear cannot change the Fatal verdict.
        val queued = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val queueOnly = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(
                context: kotlin.coroutines.CoroutineContext,
                block: Runnable,
            ) {
                queued += block
            }
        }
        runtimeTest(mainDispatcherOverride = queueOnly) { runtime ->
            // A queued main clear must reach terminal verdict within the drain budget.
            runtime.currentGeneration

            val transaction = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(sourceFile()))
            }
            advanceTimeBy(10_000)
            runCurrent()

            assertTrue(transaction.isCompleted, "the submitted deferred must complete")
            assertInstanceOf(ReplacementOutcome.Fatal::class.java, transaction.await())
            assertEquals(RuntimePhase.Fatal, runtime.phases.value, "no successor published")
            assertEquals(0, epochAdvances, "the epoch must not advance")
            assertNull(runtime.admitUiGeneration(1), "UI admission stays retired")
            val lease = async { runCatching { runtime.awaitBackupWorkLease() } }
            runCurrent()
            assertTrue(lease.isCompleted, "the worker acquirer wakes instead of parking")
            assertInstanceOf(IllegalStateException::class.java, lease.await().exceptionOrNull())

            // The documented residual, EXECUTED: the wedged dispatcher finally runs its queue —
            // the abandoned clear completes late and changes nothing.
            assertTrue(queued.isNotEmpty(), "the clear was genuinely queued, not dropped")
            while (true) {
                val next = queued.poll() ?: break
                next.run()
            }
            runCurrent()
            assertEquals(RuntimePhase.Fatal, runtime.phases.value, "still Fatal after the late clear")
            assertEquals(0, epochAdvances)
            assertNull(runtime.admitUiGeneration(1))
        }
    }

    @Test
    fun `a one-shot record failure - no committed terminal before a LATER durable record lands`() =
        runtimeTest { runtime ->
            // R4.2 mandated proof 2. The restore's record fails ONCE (never durable for the
            // restore); the bounded recovery rolls back onto the kept reservation; the
            // journal-aware production-shaped preflight then observes the still-Prepared
            // attempt and re-drives the recovery inline, whose OWN record lands durably —
            // only THAT recovery's committed terminal runs, exactly once, and the final
            // semantics are truthful restore-FAILURE.
            runtime.currentGeneration
            val journal = FakeJournal()
            val observedPhases = mutableListOf<String>()
            var recoveryEffects: JournalEffects? = null
            preflightAction = { _ ->
                protocolLog += "preflight"
                observedPhases += "${journal.phase}/${journal.kind}"
                if (journal.phase == "Prepared") {
                    val recovery = JournalEffects(
                        attemptId = requireNotNull(journal.id),
                        journal = journal,
                        kind = "Rollback",
                        log = protocolLog,
                        sourcePathOverride = journal.path,
                    )
                    recoveryEffects = recovery
                    runtime.rollbackToPreRestoreBackup(
                        sourcePath = journal.path,
                        effects = recovery,
                    )
                }
            }
            val restoreEffects = JournalEffects(
                attemptId = "restore-1",
                journal = journal,
                kind = "Restore",
                log = protocolLog,
                failRecordTimes = 1,
            )

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                restoreEffects,
            )

            val recoveredOutcome =
                assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertNull(
                recoveredOutcome.effectsError,
                "the truthful recovery is CLEAN — no stale pre-durable error rides the outcome",
            )
            // The runtime's own divert must roll back BEFORE any candidate is built over the
            // unprovable file (vacuity fix from the R4.2 review): the SECOND swap — the
            // recovery applying the kept reservation — precedes the FIRST preflight.
            val swapIndexes = protocolLog.withIndex().filter { it.value == "swap" }.map { it.index }
            val firstPreflight = protocolLog.indexOf("preflight")
            assertTrue(
                swapIndexes.size >= 2 && swapIndexes[1] < firstPreflight,
                "the recovery swap must precede the first candidate preflight: $protocolLog",
            )
            assertEquals(
                listOf("Prepared/Restore", "null/null"),
                observedPhases,
                "the preflight observed the PREPARED attempt, then the resolved journal",
            )
            assertFalse(
                restoreEffects.successPublished,
                "the restore's committed terminal never ran — its record never became durable",
            )
            assertEquals(
                1,
                protocolLog.count { it == "terminal-committed" },
                "exactly ONE committed terminal — the recovery's, after ITS durable record: " +
                    "$protocolLog",
            )
            val recovered = requireNotNull(recoveryEffects)
            assertTrue(recovered.successPublished, "the recovery's own terminal ran")
            val durable = protocolLog.indexOf("journal-committed")
            val committedTerminal = protocolLog.indexOf("terminal-committed")
            assertTrue(
                durable in 0 until committedTerminal,
                "the committed terminal ran only AFTER a durable record landed: $protocolLog",
            )
            assertNull(journal.id, "the journal resolved through the recovery, exactly once")
        }

    @Test
    fun `a one-shot record failure on a requested rollback commits exactly once - never a stale extra terminal`() =
        runtimeTest { runtime ->
            // R4.2 proof 2b — the base-red discriminator for the one-shot family. Pre-R4.2 the
            // stale Completed(effectsError) from the FAILED first record was still dispatched
            // as a committed terminal at transaction end — a SECOND terminal-committed (and a
            // second acknowledge) on top of the recovery's own, fired for a record that never
            // landed. The fix diverts the not-durable commit into the bounded retry, whose OWN
            // durable record then owns the single committed terminal.
            runtime.currentGeneration
            preservedFile(content = "UNDO")
            val journal = FakeJournal()
            val observedPhases = mutableListOf<String>()
            preflightAction = { _ ->
                observedPhases += "${journal.phase}/${journal.kind}"
                if (journal.phase == "Prepared") {
                    runtime.rollbackToPreRestoreBackup(
                        sourcePath = journal.path,
                        effects = JournalEffects(
                            attemptId = requireNotNull(journal.id),
                            journal = journal,
                            kind = "Rollback",
                            log = protocolLog,
                        ),
                    )
                }
            }
            val effects = JournalEffects(
                attemptId = "undo-1",
                journal = journal,
                kind = "Rollback",
                log = protocolLog,
                failRecordTimes = 1,
            )

            val outcome = runtime.replace(
                ReplacementOperation.RollbackToPreRestoreBackup(),
                effects,
            )

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertNull(completed.effectsError, "the healed retry is a CLEAN commit")
            assertEquals(
                1,
                protocolLog.count { it == "terminal-committed" },
                "exactly ONE committed terminal — never a stale extra from the failed first " +
                    "record: $protocolLog",
            )
            assertEquals(
                listOf("Committed/Rollback"),
                observedPhases,
                "the preflight observes the RETRY's durable record, never an unresolved slot",
            )
            val durable = protocolLog.indexOf("journal-committed")
            val committedTerminal = protocolLog.indexOf("terminal-committed")
            assertTrue(
                durable in 0 until committedTerminal,
                "the committed terminal ran only AFTER the durable record: $protocolLog",
            )
            assertNull(journal.id, "resolved exactly once, by the owning terminal")
        }

    @Test
    fun `restart-process promotion failure with production-shaped effects keeps Prepared + reservation`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            // R4.2 mandated proof 3: under the PRODUCTION policy a promotion failure leaves
            // the journal Prepared and the reservation on disk — no restore-success, no undo
            // availability, no committed terminal; the next launch recovers conservatively.
            runtime.currentGeneration
            coEvery { provider.promoteRollbackReservation(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("promotion failed")),
            )
            val journal = FakeJournal()
            val effects = JournalEffects(
                attemptId = "restore-1",
                journal = journal,
                kind = "Restore",
                log = protocolLog,
            )

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            assertEquals("Prepared", journal.phase, "the journal survives at Prepared")
            assertEquals(
                1,
                reservationFiles().size,
                "the journal-named reservation survives for the next launch",
            )
            assertEquals(
                0,
                protocolLog.count { it == "terminal-committed" },
                "no committed terminal, no success, no availability: $protocolLog",
            )
            assertFalse(effects.successPublished)
            assertFalse(effects.availabilityCleared)
        }

    @Test
    fun `an inline recovery whose record fails cannot erase the Prepared journal`() =
        runtimeTest { runtime ->
            // R4.2 mandated proof 4: the restore commits cleanly, its verification "fails"
            // (the production-shaped preflight drives the peek-failed inline recovery), and
            // the recovery's OWN record persistently fails — the ScenarioOne-shaped committed
            // terminal must never run, the journal must stay Prepared/Rollback, and the
            // machine must end in the bounded truthful Fatal with the canonical retained.
            runtime.currentGeneration
            val journal = FakeJournal()
            var recoveryEffects: JournalEffects? = null
            preflightAction = { _ ->
                if (journal.phase != null) {
                    // Committed/Restore (peek failed) or Prepared/Rollback (retry): re-drive.
                    val recovery = recoveryEffects ?: JournalEffects(
                        attemptId = requireNotNull(journal.id),
                        journal = journal,
                        kind = "Rollback",
                        log = protocolLog,
                        failRecordTimes = Int.MAX_VALUE,
                        sourcePathOverride = null,
                    ).also { recoveryEffects = it }
                    runtime.rollbackToPreRestoreBackup(
                        sourcePath = null,
                        effects = recovery,
                    )
                }
            }
            val restoreEffects = JournalEffects(
                attemptId = "restore-1",
                journal = journal,
                kind = "Restore",
                log = protocolLog,
            )

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                restoreEffects,
            )

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(
                0,
                protocolLog.count { it == "terminal-committed" },
                "the recovery's committed terminal must never run over its failed record: " +
                    "$protocolLog",
            )
            assertEquals("Prepared", journal.phase, "the re-claimed attempt stays Prepared")
            assertEquals("Rollback", journal.kind)
            assertTrue(
                File(tempDir, "pre_restore_backup.db").exists(),
                "the canonical recovery source is retained",
            )
        }

    @Test
    fun `the file mutation alone never selects the committed terminal - the durable phase is load-bearing`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            // R4.2 mandated proof 5, focused: the swap RAN (the mutation happened), the durable
            // record did not — terminal dispatch must select onFailedAfterMutation, never
            // onCommitted.
            runtime.currentGeneration
            val effects = RecordingEffects(
                onMutationCommittedBody = { error("durable journal write failed") },
            )

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(sourceFile()),
                effects,
            )

            assertTrue(protocolLog.contains("swap"), "the file mutation genuinely happened")
            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            assertEquals(
                listOf("beforeMutation", "mutationCommitted", "failedAfterMutation"),
                effects.calls,
                "durable phase, not file mutation, selects the terminal: ${effects.calls}",
            )
            assertEquals(1, reservationFiles().size, "the reservation is retained")
        }

    private companion object {
        /** The bytes a reserved rollback snapshot carries — the pre-attempt database stand-in. */
        const val RESERVATION_CONTENT = "res"
    }
}
