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
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndo
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.di.AppGraph
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

/** Replacement transaction regression tests: ownership, ordering, recovery, terminal truth. */
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

    /** Recording effects double: one label per phase; [calls] may share the protocol log. */
    private class RecordingEffects(
        override val attemptId: RestoreOwnerId,
        private val onBefore: suspend () -> Unit = {},
        private val onMutationCommittedBody: suspend () -> Unit = {},
        private val onBeforeCompensationBody: suspend () -> Unit = {},
        private val onCompensationCommittedBody: suspend () -> Unit = {},
        private val onCommittedBody: suspend () -> Unit = {},
        private val onRejectedBody: suspend () -> Unit = {},
        private val onRecoveredBody: suspend () -> Unit = {},
    ) : DatabaseReplacementEffects {

        var calls: MutableList<String> = mutableListOf()
            private set

        fun recordInto(target: MutableList<String>): RecordingEffects = apply { calls = target }

        var undoRef: UndoRef? = null
            private set
        var restoreSourceRef: RestoreSourceRef? = null
            private set
        var compensationOwner: RestoreOwnerId? = null
            private set
        var compensationRef: UndoRef? = null
            private set

        override suspend fun onBeforeMutation(
            undoRef: UndoRef,
            restoreSourceRef: RestoreSourceRef?,
        ) {
            this.undoRef = undoRef
            this.restoreSourceRef = restoreSourceRef
            calls += "beforeMutation"
            onBefore()
        }

        override suspend fun onMutationCommitted() {
            calls += "mutationCommitted"
            onMutationCommittedBody()
        }

        override suspend fun onBeforeCompensation(
            rollbackOwner: RestoreOwnerId,
            appliedRef: UndoRef,
        ) {
            compensationOwner = rollbackOwner
            compensationRef = appliedRef
            calls += "beforeCompensation"
            onBeforeCompensationBody()
        }

        override suspend fun onCompensationCommitted(rollbackOwner: RestoreOwnerId) {
            compensationOwner = rollbackOwner
            calls += "compensationCommitted"
            onCompensationCommittedBody()
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
            onRecoveredBody()
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

    private val touchedDatabases = mutableListOf<String>()

    private val provider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val restoreStateRepository = mockk<RestoreStateRepository>(relaxed = true)

    private val restoreSources = mutableMapOf<RestoreSourceRef, File>()
    private val undoFiles = mutableMapOf<UndoRef, File>()
    private val appliedRestoreRefs = mutableListOf<RestoreSourceRef>()
    private val appliedUndoRefs = mutableListOf<UndoRef>()
    private var nextOwnerSerial = 10L

    private var preflightCalls = 0
    private val preflightOutcomes = ArrayDeque<StartupOutcome>()
    private var preflightAction: (suspend (RuntimeGeneration) -> Unit)? = null
    private var preflightGate: CompletableDeferred<Unit>? = null

    private var epochAdvances = 0
    private var processRestarts = 0

    private fun sourceFile(name: String = "restore_source.db"): File =
        File(tempDir, name).apply { writeText("snapshot-bytes") }

    private fun ownedUndoFile(
        ref: UndoRef = DEFAULT_UNDO_REF,
        content: String = "preserved-bytes",
    ): File = File(tempDir, "undo_${ref.owner}.db").apply {
        writeText(content)
        undoFiles[ref] = this
    }

    private fun stagedRestoreFile(ref: RestoreSourceRef): File =
        File(tempDir, "staged_restore_${ref.owner}.db")

    private fun newOwner(): RestoreOwnerId = RestoreOwnerId(
        "10000000-0000-4000-8000-${nextOwnerSerial++.toString(16).padStart(12, '0')}",
    )

    private fun restoreOperation(
        source: File,
        owner: RestoreOwnerId = newOwner(),
    ): ReplacementOperation.RestoreFromSnapshot =
        ReplacementOperation.RestoreFromSnapshot(source, owner)

    private fun rollbackOperation(
        sourceRef: UndoRef = DEFAULT_UNDO_REF,
        owner: RestoreOwnerId = newOwner(),
    ): ReplacementOperation.RollbackFromUndo =
        ReplacementOperation.RollbackFromUndo(sourceRef, owner)

    private suspend fun AppRuntime.replace(operation: ReplacementOperation): ReplacementOutcome {
        val owner = when (operation) {
            is ReplacementOperation.RestoreFromSnapshot -> operation.owner
            is ReplacementOperation.RollbackFromUndo -> operation.owner
        }
        return replace(operation, RecordingEffects(owner))
    }

    private suspend fun AppRuntime.restoreFromSnapshot(source: File): DatabaseReplacementResult {
        val effects = RecordingEffects(newOwner())
        return restoreFromSnapshot(source, effects)
    }

    private fun runtimeTest(
        replacementPolicy: ReplacementPolicy = ReplacementPolicy.RebuildInProcess,
        standardHostDispatcher: Boolean = false,
        mainDispatcherOverride: kotlinx.coroutines.CoroutineDispatcher? = null,
        restartProcessOverride: (() -> Unit)? = null,
        body: suspend TestScope.(AppRuntime) -> Unit,
    ) = runTest {
        coEvery { provider.stageRestoreSource(any(), any()) } coAnswers {
            val source = firstArg<File>()
            val ref = secondArg<RestoreSourceRef>()
            if (!source.exists()) {
                BackupResult.Failure(BackupError.Io(IOException("missing caller source")))
            } else {
                val staged = stagedRestoreFile(ref)
                source.copyTo(staged, overwrite = false)
                source.delete()
                restoreSources[ref] = staged
                BackupResult.Success(staged)
            }
        }
        every { provider.getRestoreSourceFile(any()) } answers {
            restoreSources[firstArg<RestoreSourceRef>()]?.takeIf(File::exists)
        }
        coEvery { provider.validateRestoreSource(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.checkRestoreCapacity(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.createUndo(any()) } coAnswers {
            val ref = firstArg<UndoRef>()
            val undo = File(tempDir, "undo_${ref.owner}.db")
            if (undo.exists()) {
                BackupResult.Failure(BackupError.CorruptedBackup("immutable undo already exists"))
            } else {
                undo.writeText(IMMUTABLE_UNDO_CONTENT)
                undoFiles[ref] = undo
                BackupResult.Success(undo)
            }
        }
        every { provider.getUndoFile(any()) } answers {
            undoFiles[firstArg<UndoRef>()]?.takeIf(File::exists)
        }
        coEvery { provider.validateUndo(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.checkRollbackCapacity(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.replaceLiveDatabaseFromRestore(any()) } coAnswers {
            appliedRestoreRefs += firstArg<RestoreSourceRef>()
            protocolLog += "swap"
            BackupResult.Success(Unit)
        }
        coEvery { provider.replaceLiveDatabaseFromUndo(any()) } coAnswers {
            appliedUndoRefs += firstArg<UndoRef>()
            protocolLog += "swap"
            BackupResult.Success(Unit)
        }
        coEvery { provider.deleteRestoreSource(any()) } coAnswers {
            protocolLog += "deleteRestoreSource"
            restoreSources.remove(firstArg<RestoreSourceRef>())?.delete() ?: false
        }
        coEvery { provider.deleteUndo(any()) } coAnswers {
            protocolLog += "deleteUndo"
            undoFiles.remove(firstArg<UndoRef>())?.delete() ?: false
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = AppRuntime(
            applicationContext = context,
            dbFactory = {
                val index = databases.size
                val db = mockk<AppDatabase>(relaxed = true)
                // DB-touch probe: `toString()`, not a DAO read — this classpath has no room3.
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
                    every { restoreStateRepository } returns this@AppRuntimeReplacementTest
                        .restoreStateRepository
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
                restartProcess = restartProcessOverride ?: { processRestarts += 1 },
                mainDispatcher = mainDispatcherOverride ?: dispatcher,
                hostDispatcher = if (standardHostDispatcher) {
                    kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
                } else {
                    dispatcher
                },
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        body(runtime)
    }

    private fun stagedFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("staged_restore_") }

    private fun closeIndices(): List<Int> =
        protocolLog.withIndex().filter { it.value == "close" }.map { it.index }

    // Mandate 1 — staged source ownership at submission.

    @Test
    fun `a restore submitted from inside a transaction is rejected, never deadlocked`() =
        runtimeTest { runtime ->
            // No caller does this today. The guard exists because `transitionMutex` is not
            // reentrant, so without it the alternative to a typed rejection is a permanent hang:
            // the nested submission waits on a lock its own caller is holding.
            val result = withContext(ReplacementTransaction(nextDbGeneration = 2)) {
                runtime.restoreFromSnapshot(File(tempDir, "nested.db"))
            }

            assertInstanceOf(DatabaseReplacementResult.RejectedBeforeMutation::class.java, result)
            assertFalse(protocolLog.contains("swap"), "a rejected restore must not mutate")
        }

    @Test
    fun `restore transfers source ownership and swaps the exact staged ref`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val source = sourceFile()
            val operation = restoreOperation(source)

            val outcome = runtime.replace(operation)

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertFalse(source.exists(), "ownership transferred: the original path was consumed")
            assertEquals(listOf(operation.sourceRef), appliedRestoreRefs)
            assertEquals(operation.sourceRef, restoreSources.keys.single())
            assertTrue(stagedFiles().single().exists(), "persisted-state GC owns final deletion")
        }

    @Test
    fun `cancelled caller deleting its temp path cannot strand the transaction - it commits`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val source = sourceFile()
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val caller = launch { runtime.replace(restoreOperation(source)) }
            runCurrent()
            caller.cancel()
            source.delete()
            runCurrent()

            gate.complete(Unit)
            runCurrent()

            val serving = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertEquals(2, serving.generation.id, "the transaction completed despite the caller")
            assertEquals(1, stagedFiles().size, "the runtime-owned source survives for state GC")
        }

    @Test
    fun `staging failure rejects before anything - no validation, compensation runs`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val missing = File(tempDir, "never_created.db")
            val effects = RecordingEffects(newOwner())

            val outcome = runtime.replace(
                restoreOperation(missing, effects.attemptId),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
            coVerify(exactly = 0) { provider.validateRestoreSource(any()) }
            coVerify(exactly = 0) { provider.createUndo(any()) }
            assertEquals(listOf("rejected"), effects.calls)
        }

    // Mandate 2 — typed effects, runtime-owned compensation, exactly once.

    @Test
    fun `lease timeout rejects before journal claim even if the caller dies`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val lease = checkNotNull(runtime.awaitBackupWorkLease()) // the quiesce will time out on it
            var markerWritten = false
            val effects = RecordingEffects(newOwner(), onBefore = { markerWritten = true })
            val source = sourceFile()

            val caller = launch {
                runtime.replace(restoreOperation(source, effects.attemptId), effects)
            }
            runCurrent()
            assertFalse(markerWritten, "quiescence must succeed before Prepared can be persisted")
            caller.cancel() // the initiator dies mid-transaction
            advanceTimeBy(3_000)
            runCurrent()

            assertEquals(
                listOf("rejected"),
                effects.calls,
                "the transaction reports rejection without ever owning a journal",
            )
            assertEquals(1, stagedFiles().size, "rejection cleanup is persisted-state GC owned")
            val serving = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertEquals(1, serving.generation.id)
            lease.release()
        }

    @Test
    fun `validation failure - rejection with compensation, nothing irreversible`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            coEvery { provider.validateRestoreSource(any()) } returns BackupResult.Failure(
                BackupError.BackupTooNew(backupSchemaVersion = 9, appSchemaVersion = 5),
            )
            val effects = RecordingEffects(newOwner())

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), effects.attemptId),
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
    fun `capacity rejection creates no undo journal close or swap and keeps generation serving`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val owner = newOwner()
            val sourceRef = RestoreSourceRef(owner)
            val error = BackupError.InsufficientLocalStorage(
                requiredBytes = 101L,
                availableBytes = 100L,
            )
            coEvery { provider.checkRestoreCapacity(sourceRef) } returns
                BackupResult.Failure(error)
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), owner),
                effects,
            )

            val rejected =
                assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
            assertEquals(error, rejected.error)
            assertEquals(listOf("rejected"), effects.calls, "onBeforeMutation never journals")
            coVerify(exactly = 1) { provider.checkRestoreCapacity(sourceRef) }
            coVerify(exactly = 0) { provider.createUndo(any()) }
            coVerify(exactly = 0) { provider.replaceLiveDatabaseFromRestore(any()) }
            coVerify(exactly = 0) { provider.replaceLiveDatabaseFromUndo(any()) }
            assertTrue(undoFiles.isEmpty())
            assertTrue(appliedRestoreRefs.isEmpty())
            assertTrue(protocolLog.isEmpty(), "no database close or live swap")
            assertSame(genOne, runtime.currentGeneration)
            val admitted = requireNotNull(runtime.admitUiGeneration(genOne.id))
            runtime.releaseUiGeneration(admitted)
        }

    @Test
    fun `journal claim failure after quiesce is Fatal and seals replacement admission`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val effects = RecordingEffects(
                newOwner(),
                onBefore = { error("marker write failed") },
            )

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), effects.attemptId),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            assertEquals(listOf("beforeMutation", "fatal"), effects.calls)
            assertTrue(protocolLog.isEmpty())
        }

    @Test
    fun `restart policy treats an ambiguous journal claim failure as PONR`() = runtimeTest(
        replacementPolicy = ReplacementPolicy.RestartProcess,
    ) { runtime ->
        runtime.currentGeneration
        val effects = RecordingEffects(
            newOwner(),
            onBefore = { error("DataStore returned after an ambiguous write failure") },
        )

        val outcome = runtime.replace(
            restoreOperation(sourceFile(), effects.attemptId),
            effects,
        )

        assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
        assertEquals(RuntimePhase.Fatal, runtime.phases.value)
        assertEquals(listOf("beforeMutation", "fatal"), effects.calls)
        assertEquals(1, processRestarts, "cold recovery must inspect any persisted Prepared state")
        assertTrue(protocolLog.isEmpty())
    }

    @Test
    fun `committed effects failure is SURFACED - never a silently clean commit`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val effects = RecordingEffects(
                newOwner(),
                onCommittedBody = { error("dialog write failed") },
            )

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), effects.attemptId),
                effects,
            )

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertNotNull(completed.effectsError, "the failed commit-effects must surface")
            assertNotNull(completed.generation, "the operation itself DID commit")
        }

    // Spec §27 — immutable attempt-owned undo and durable commit record.

    @Test
    fun `the immutable undo is created inside the transaction after validation`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            coEvery { provider.validateRestoreSource(any()) } returns BackupResult.Failure(
                BackupError.CorruptedBackup(reason = "magic mismatch"),
            )
            val rejectedOwner = newOwner()
            val rejectedEffects = RecordingEffects(attemptId = rejectedOwner)

            val rejected = runtime.replace(
                restoreOperation(sourceFile("first.db"), rejectedOwner),
                rejectedEffects,
            )

            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, rejected)
            coVerify(exactly = 0) { provider.createUndo(any()) }
            assertNull(rejectedEffects.undoRef, "no undo was created before validation")
            assertTrue(undoFiles.isEmpty())

            coEvery { provider.validateRestoreSource(any()) } returns BackupResult.Success(Unit)
            val committedOwner = newOwner()
            val committedEffects = RecordingEffects(attemptId = committedOwner)

            val committed = runtime.replace(
                restoreOperation(sourceFile("second.db"), committedOwner),
                committedEffects,
            )

            assertInstanceOf(ReplacementOutcome.Completed::class.java, committed)
            val expectedUndo = UndoRef(committedOwner)
            coVerify(exactly = 1) { provider.createUndo(expectedUndo) }
            assertEquals(
                expectedUndo,
                committedEffects.undoRef,
                "onBeforeMutation persists this attempt's opaque undo ref",
            )
            assertEquals(RestoreSourceRef(committedOwner), committedEffects.restoreSourceRef)
        }

    @Test
    fun `a refused journal claim seals runtime and preserves both undo assets`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "OLD")
            // Rejected after immutable N exists: the journal claim refuses the attempt.
            val owner = newOwner()
            val effects = RecordingEffects(
                attemptId = owner,
                onBefore = { error("journal slot already owned") },
            )

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), owner),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            val ownedRef = UndoRef(owner)
            coVerify(exactly = 1) { provider.createUndo(ownedRef) }
            assertEquals(listOf("beforeMutation", "fatal"), effects.calls)
            assertEquals("OLD", previous.readText(), "the previous active undo is untouched")
            assertTrue(checkNotNull(undoFiles[ownedRef]).exists())
        }

    @Test
    fun `a committed restore keeps immutable new undo without overwriting the previous one`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "OLD")
            val owner = newOwner()
            val ownedRef = UndoRef(owner)
            var undoAtRecordTime: Boolean? = null
            val effects = RecordingEffects(
                attemptId = owner,
                onMutationCommittedBody = {
                    undoAtRecordTime = undoFiles[ownedRef]?.exists()
                },
            )

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), owner),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals("OLD", previous.readText(), "immutable P is never overwritten by N")
            assertEquals(true, undoAtRecordTime, "N exists when Committed becomes durable")
            assertEquals(IMMUTABLE_UNDO_CONTENT, checkNotNull(undoFiles[ownedRef]).readText())
        }

    @Test
    fun `durable rollback commit is recorded while exact source remains finalizer-owned`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val source = ownedUndoFile()
            // ONE shared list: the effects' labels interleave with the provider's own calls.
            val owner = newOwner()
            val effects = RecordingEffects(attemptId = owner).recordInto(protocolLog)

            val outcome = runtime.replace(
                rollbackOperation(owner = owner),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            val recorded = protocolLog.indexOf("mutationCommitted")
            assertTrue(recorded >= 0, "the durable commit record must run: $protocolLog")
            coVerify(exactly = 0) { provider.deleteUndo(DEFAULT_UNDO_REF) }
            assertTrue(source.exists(), "state finalization owns exact-source deletion")
        }

    @Test
    fun `a requested rollback whose record persistently fails ends FATAL - every asset preserved`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val source = ownedUndoFile(content = "UNDO")
            val owner = newOwner()
            val effects = RecordingEffects(
                attemptId = owner,
                onMutationCommittedBody = { error("durable journal write failed") },
            )

            val outcome = runtime.replace(
                rollbackOperation(owner = owner),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertFalse(
                effects.calls.contains("committed"),
                "the committed terminal must NOT run over a non-durable commit: ${effects.calls}",
            )
            coVerify(exactly = 0) { provider.deleteUndo(DEFAULT_UNDO_REF) }
            assertEquals("UNDO", source.readText(), "the next launch can finalize/recover")
            assertEquals(
                listOf("beforeMutation", "mutationCommitted", "mutationCommitted", "fatal"),
                effects.calls,
                "one primary record try, one bounded retry, then the Fatal terminal",
            )
        }

    @Test
    fun `terminal compensation failure is surfaced on the outcome`() = runtimeTest { runtime ->
        runtime.currentGeneration
        coEvery { provider.validateRestoreSource(any()) } returns BackupResult.Failure(
            BackupError.BackupTooNew(backupSchemaVersion = 9, appSchemaVersion = 5),
        )
        val effects = RecordingEffects(
            newOwner(),
            onRejectedBody = { error("compensation write failed") },
        )

        val outcome = runtime.replace(
            restoreOperation(sourceFile(), effects.attemptId),
            effects,
        )

        val rejected =
            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
        assertInstanceOf(BackupError.BackupTooNew::class.java, rejected.error)
        assertNotNull(
            rejected.effectsError,
            "a throwing compensation leaves durable state disagreeing with the outcome",
        )
        assertEquals(listOf("rejected"), effects.calls)
    }

    // Mandates 3 + 4 — result truth and asset preservation.

    @Test
    fun `restore swap failure with successful rollback - RecoveredByRollback, never Completed`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val swapError = BackupError.Io(IOException("rename failed"))
            coEvery { provider.replaceLiveDatabaseFromRestore(any()) } coAnswers {
                protocolLog += "swap"
                BackupResult.Failure(swapError)
            }
            coEvery { provider.replaceLiveDatabaseFromUndo(any()) } coAnswers {
                appliedUndoRefs += firstArg<UndoRef>()
                protocolLog += "swap"
                BackupResult.Success(Unit)
            }
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), owner),
                effects,
            )

            val recovered =
                assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertSame(swapError, recovered.error)
            assertEquals(2, recovered.generation.id, "a successor serves the PRE-operation data")
            assertEquals(
                listOf(
                    "beforeMutation",
                    "beforeCompensation",
                    "compensationCommitted",
                    "recovered",
                ),
                effects.calls,
                """
                the ladder must replace the restore owner with an exact rollback owner and durably
                commit that rollback before reporting recovery; the caller restore itself is never
                recorded Committed over the rolled-back live file
                """.trimIndent(),
            )
            assertEquals(listOf(UndoRef(owner)), appliedUndoRefs)
            assertEquals(UndoRef(owner), effects.compensationRef)
            assertNotNull(effects.compensationOwner)
        }

    @Test
    fun `inline scenario-1 rollback - the outer RESTORE reports RecoveredByRollback`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val restoreOwner = newOwner()
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = { _ ->
                if (preflightCalls == 1) {
                    inlineResult = runtime.rollbackFromUndo(
                        UndoRef(restoreOwner),
                        RecordingEffects(newOwner()),
                    )
                }
            }
            preflightOutcomes += StartupOutcome.RestartRequired // after the inline rollback

            val outcome = runtime.replace(restoreOperation(sourceFile(), restoreOwner))

            // The inline caller REQUESTED a rollback — its result is honestly Committed.
            assertInstanceOf(
                DatabaseReplacementResult.Committed::class.java,
                inlineResult,
                "inline result: $inlineResult",
            )
            // The outer caller requested a RESTORE that ended rolled back — never Completed.
            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(2, preflightCalls, "the retry attempt ran over the rolled-back file")
        }

    @Test
    fun `rollback operation commits - Completed is the requested-op truth`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            ownedUndoFile()
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(
                rollbackOperation(owner = owner),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(listOf("beforeMutation", "mutationCommitted", "committed"), effects.calls)
        }

    @Test
    fun `restart-process swap failure - FailedAfterMutation, journal effects, assets preserved`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            coEvery { provider.replaceLiveDatabaseFromRestore(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("rename failed")),
            )
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), owner),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            assertEquals(listOf("beforeMutation", "failedAfterMutation"), effects.calls)
            assertTrue(checkNotNull(undoFiles[UndoRef(owner)]).exists(), "N is preserved")
        }

    @Test
    fun `restart-process close throw - FailedAfterMutation, never rejected, no rename`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            closeFailureIndices += 0
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), owner),
                effects,
            )

            // Close INVOCATION = PONR (mandate 5): a throwing close is post-PONR unknown state.
            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            assertEquals(0, protocolLog.count { it == "swap" }, "never rename after a failed close")
            assertEquals(listOf("beforeMutation", "failedAfterMutation"), effects.calls)
            assertTrue(checkNotNull(undoFiles[UndoRef(owner)]).exists(), "N is preserved")
        }

    // Mandate 5 — PONR at the start of every irreversible action.

    @Test
    fun `outgoing close failure is FATAL - no rename, no republished generation`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            closeFailureIndices += 0
            val effects = RecordingEffects(newOwner())

            val outcome = runtime.replace(
                restoreOperation(sourceFile(), effects.attemptId),
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

            val outcome = runtime.replace(restoreOperation(sourceFile()))

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
                runtime.replace(restoreOperation(sourceFile()))
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
            ownedUndoFile()
            closeFailureIndices += 1 // the first CANDIDATE's close will throw during disposal
            preflightOutcomes += StartupOutcome.RouteToRecovery // the candidate fails preflight

            val outcome = runtime.replace(restoreOperation(sourceFile()))

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
            ownedUndoFile()
            graphFactoryFailures += 1 // the candidate's graph build throws
            closeFailureIndices += 1 // and the orphan's close throws too

            val outcome = runtime.replace(restoreOperation(sourceFile()))

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(1, protocolLog.count { it == "swap" }, "no rollback rename after")
        }

    @Test
    fun `graphFactory failure with clean orphan close - ladder recovers by rollback`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            ownedUndoFile()
            graphFactoryFailures += 1 // candidate #1 fails; its db closes cleanly

            val outcome = runtime.replace(restoreOperation(sourceFile()))

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
        }

    @Test
    fun `candidate jobs are joined before the candidate database closes`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            ownedUndoFile()
            preflightAction = { generation ->
                if (preflightCalls == 1) {
                    generation.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                        try {
                            awaitCancellation()
                        } finally {
                            touchedDatabases += generation.database.toString()
                            protocolLog += "candidate-job-ended"
                        }
                    }
                    // A nested unconfined launch queues on the event loop; yield so the job starts.
                    kotlinx.coroutines.yield()
                }
            }
            preflightOutcomes += StartupOutcome.RouteToRecovery // candidate #1 fails preflight

            val outcome = runtime.replace(restoreOperation(sourceFile()))

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
            ownedUndoFile()
            graphFactoryFailures += 1 // candidate #1's graph build throws AFTER the action below
            graphFactoryAction = { index, database, lifetime ->
                if (index == 1) {
                    // A partial graph already gave the lifetime to a consumer with a DB-bound job.
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

            val outcome = runtime.replace(restoreOperation(sourceFile()))

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
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

    // Mandate 7 — admission.

    @Test
    fun `unreleased lease aborts pre-PONR - rejection, admission reopens, retry works`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val lease = checkNotNull(runtime.awaitBackupWorkLease())

            val transaction = async {
                runtime.replace(restoreOperation(sourceFile()))
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
                runtime.replace(restoreOperation(sourceFile("second.db")))
            assertInstanceOf(ReplacementOutcome.Completed::class.java, retry)
        }

    @Test
    fun `worker suspended in the closed window binds to the NEW generation`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val transaction = async {
                runtime.replace(restoreOperation(sourceFile()))
            }
            runCurrent() // quiesce closed admission; the transaction parked at preflight
            // ORDER-SENSITIVE: the unconfined acquirer resumes synchronously inside reopen.
            val leaseCall = async(UnconfinedTestDispatcher(testScheduler)) {
                checkNotNull(runtime.awaitBackupWorkLease())
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
                runtime.replace(restoreOperation(sourceFile()))
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
                runtime.replace(restoreOperation(sourceFile()))
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
            val lease = checkNotNull(runtime.awaitBackupWorkLease())
            val transaction = async {
                runtime.replace(restoreOperation(sourceFile()))
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
            val lease = checkNotNull(runtime.awaitBackupWorkLease())
            val aborted = async {
                runtime.replace(restoreOperation(sourceFile()))
            }
            advanceTimeBy(3_000)
            runCurrent()
            assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                aborted.await(),
            )
            assertEquals(0, epochAdvances, "abort preserves the queued snackbar models")

            lease.release()
            runtime.replace(restoreOperation(sourceFile("second.db")))
            assertEquals(1, epochAdvances, "commit discards the outgoing generation's queue")
        }

    // Mandates 6 + 8 — owner-unambiguous serialization and Fatal under concurrency.

    @Test
    fun `simultaneous callers keep distinct owners refs and terminal callbacks`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val firstSource = sourceFile("first-owner.db")
            val secondSource = sourceFile("second-owner.db")
            val firstOwner = newOwner()
            val secondOwner = newOwner()
            val firstEffects = RecordingEffects(firstOwner)
            val secondEffects = RecordingEffects(secondOwner)
            coEvery { restoreStateRepository.readProtocol() } returns RestoreProtocolRead.Current(
                RestoreProtocolState(
                    installEpoch = InstallEpoch(
                        RestoreOwnerId("20000000-0000-4000-8000-000000000001"),
                    ),
                    attempt = null,
                    activeUndo = null,
                    terminalOutbox = null,
                ),
            )
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val first = async {
                runtime.replace(restoreOperation(firstSource, firstOwner), firstEffects)
            }
            runCurrent()
            val second = async {
                runtime.replace(restoreOperation(secondSource, secondOwner), secondEffects)
            }
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertInstanceOf(ReplacementOutcome.Completed::class.java, first.await())
            assertInstanceOf(ReplacementOutcome.Completed::class.java, second.await())
            assertEquals(2, preflightCalls, "each owner executes its own transaction")
            assertEquals(UndoRef(firstOwner), firstEffects.undoRef)
            assertEquals(RestoreSourceRef(firstOwner), firstEffects.restoreSourceRef)
            assertEquals(UndoRef(secondOwner), secondEffects.undoRef)
            assertEquals(RestoreSourceRef(secondOwner), secondEffects.restoreSourceRef)
            assertEquals(
                listOf("beforeMutation", "mutationCommitted", "committed"),
                firstEffects.calls,
            )
            assertEquals(
                listOf("beforeMutation", "mutationCommitted", "committed"),
                secondEffects.calls,
            )
            coVerify(exactly = 1) {
                provider.sweepRecoveryFiles(any())
            }
        }

    @Test
    fun `different operation gets its OWN serialized result - never the other operation's`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // Rollback goes first: a committed restore creates the undo slot and would hide it.
            val rollback = async {
                runtime.replace(rollbackOperation())
            }
            val restore = async {
                runtime.replace(restoreOperation(sourceFile()))
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
            ownedUndoFile()
            closeFailureIndices += 0 // A's outgoing close throws → Fatal (post-PONR)
            // Hold A inside its machine (UI gate) so B queues behind the mutex.
            val holdToken = requireNotNull(runtime.admitUiGeneration(genOne.id))

            val a = async { runtime.replace(restoreOperation(sourceFile())) }
            runCurrent()
            val b = async { runtime.replace(rollbackOperation()) }
            runCurrent()
            runtime.releaseUiGeneration(holdToken)
            runCurrent()

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, a.await())
            assertInstanceOf(ReplacementOutcome.Fatal::class.java, b.await())
            coVerify(exactly = 0) { provider.getUndoFile(DEFAULT_UNDO_REF) }
            assertEquals(0, protocolLog.count { it == "swap" }, "no swap ran at all")
            assertEquals(RuntimePhase.Fatal, runtime.phases.value, "nothing overwrote Fatal")
            assertTrue(checkNotNull(undoFiles[DEFAULT_UNDO_REF]).exists(), "B deleted nothing")
        }

    @Test
    fun `replace after Fatal returns Fatal - never a cleanup-safe rejection`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            closeFailureIndices += 0
            runtime.replace(restoreOperation(sourceFile()))
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            val effects = RecordingEffects(newOwner())

            val after = runtime.replace(
                restoreOperation(sourceFile("late.db"), effects.attemptId),
                effects,
            )

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, after)
            assertEquals(emptyList<String>(), effects.calls, "no compensation for a no-op Fatal")
        }

    @Test
    fun `reinitialize after Fatal returns Fatal and publishes nothing`() = runtimeTest { runtime ->
        runtime.currentGeneration
        closeFailureIndices += 0
        runtime.replace(restoreOperation(sourceFile()))
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
            runtime.replace(restoreOperation(sourceFile()))

            assertThrows<IllegalStateException> { runtime.currentGeneration }
            val lease = async { runCatching { runtime.awaitBackupWorkLease() } }
            runCurrent()
            assertTrue(lease.isCompleted, "the acquirer must fail loud, not park forever")
            assertInstanceOf(IllegalStateException::class.java, lease.await().exceptionOrNull())
        }

    @Test
    fun `restart-process policy keeps UI and worker admission closed until restart`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            val genOne = runtime.currentGeneration

            val outcome = runtime.replace(restoreOperation(sourceFile()))

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertNull(completed.generation, "no in-process successor under RestartProcess")
            assertEquals(1, databases.size, "no rebuild")
            assertEquals(0, preflightCalls)
            assertEquals(RuntimePhase.Transitioning, runtime.phases.value)
            assertEquals(1, processRestarts, "the runtime owns the terminal restart")
            assertNull(runtime.admitUiGeneration(genOne.id), "the old UI cannot observe active P")
            val worker = async { runtime.awaitBackupWorkLease() }
            runCurrent()
            assertFalse(worker.isCompleted, "DB-bound admission stays closed until process restart")
            worker.cancel()
        }

    @Test
    fun `restart-process PONR terminally rejects a replacement already queued behind it`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            val terminalGate = CompletableDeferred<Unit>()
            val firstOwner = newOwner()
            val secondOwner = newOwner()
            val firstEffects = RecordingEffects(
                attemptId = firstOwner,
                onCommittedBody = { terminalGate.await() },
            )
            val secondEffects = RecordingEffects(attemptId = secondOwner)

            val first = async {
                runtime.replace(
                    restoreOperation(sourceFile("restart-first.db"), firstOwner),
                    firstEffects,
                )
            }
            runCurrent()
            val second = async {
                runtime.replace(
                    restoreOperation(sourceFile("restart-second.db"), secondOwner),
                    secondEffects,
                )
            }
            runCurrent()
            assertFalse(second.isCompleted, "the second transaction is queued behind terminal effects")

            terminalGate.complete(Unit)
            runCurrent()

            assertInstanceOf(ReplacementOutcome.Completed::class.java, first.await())
            assertInstanceOf(ReplacementOutcome.Fatal::class.java, second.await())
            assertEquals(emptyList<String>(), secondEffects.calls)
            assertEquals(1, protocolLog.count { it == "swap" })
            assertEquals(1, processRestarts)
            assertEquals(RuntimePhase.Transitioning, runtime.phases.value)
        }

    @Test
    fun `restart failure is Fatal and keeps later replacement admission sealed`() = runtimeTest(
        replacementPolicy = ReplacementPolicy.RestartProcess,
        restartProcessOverride = { throw IOException("restart unavailable") },
    ) { runtime ->
        runtime.currentGeneration
        val first = runtime.replace(restoreOperation(sourceFile("restart-failure.db")))

        val fatal = assertInstanceOf(ReplacementOutcome.Fatal::class.java, first)
        assertNotNull(fatal.effectsError)
        assertEquals(RuntimePhase.Fatal, runtime.phases.value)
        val secondEffects = RecordingEffects(newOwner())
        val second = runtime.replace(
            restoreOperation(sourceFile("after-restart-failure.db"), secondEffects.attemptId),
            secondEffects,
        )
        assertInstanceOf(ReplacementOutcome.Fatal::class.java, second)
        assertEquals(emptyList<String>(), secondEffects.calls)
        assertEquals(1, protocolLog.count { it == "swap" })
    }

    @Test
    fun `restart-process caller cancellation cannot reopen committed old generation`() =
        runtimeTest(
            replacementPolicy = ReplacementPolicy.RestartProcess,
            standardHostDispatcher = true,
        ) { runtime ->
            val genOne = runtime.currentGeneration
            val source = sourceFile("restart-cancel.db")
            val caller = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                runtime.replace(restoreOperation(source))
            }
            assertFalse(source.exists(), "source ownership transferred before caller cancellation")

            caller.cancel()
            runCurrent()

            assertTrue(protocolLog.contains("swap"), "host-owned replacement still committed")
            assertEquals(1, processRestarts, "caller cancellation cannot cancel the restart")
            assertEquals(RuntimePhase.Transitioning, runtime.phases.value)
            assertNull(runtime.admitUiGeneration(genOne.id))
            val worker = async { runtime.awaitBackupWorkLease() }
            runCurrent()
            assertFalse(worker.isCompleted)
            worker.cancel()
        }

    @Test
    fun `staging happens in the SUBMISSION frame - before the host coroutine ever runs`() =
        runtimeTest(standardHostDispatcher = true) { runtime ->
            runtime.currentGeneration
            val source = sourceFile()

            // UNDISPATCHED + a standard host dispatcher: the transaction body has not started.
            val caller = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                runtime.replace(restoreOperation(source))
            }
            assertFalse(source.exists(), "the submission frame consumed the original path")
            assertEquals(1, stagedFiles().size, "the runtime owns the staged copy already")

            caller.cancel()
            source.delete() // the dead caller's cleanup — a no-op against the staged copy
            runCurrent()
            val serving = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertEquals(2, serving.generation.id)
            assertEquals(1, stagedFiles().size, "persisted-state GC owns staged-source deletion")
        }

    @Test
    fun `terminal effects hold the transition mutex - a successor cannot interleave them`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            ownedUndoFile()
            val terminalGate = CompletableDeferred<Unit>()
            coEvery { provider.validateRestoreSource(any()) } returns BackupResult.Failure(
                BackupError.CorruptedBackup(reason = "magic mismatch"),
            )
            val t1Owner = newOwner()
            val t1Effects = object : DatabaseReplacementEffects {
                override val attemptId: RestoreOwnerId = t1Owner

                override suspend fun onBeforeMutation(
                    undoRef: UndoRef,
                    restoreSourceRef: RestoreSourceRef?,
                ) = Unit

                override suspend fun onMutationCommitted() = Unit

                override suspend fun onBeforeCompensation(
                    rollbackOwner: RestoreOwnerId,
                    appliedRef: UndoRef,
                ) = Unit

                override suspend fun onCompensationCommitted(rollbackOwner: RestoreOwnerId) = Unit

                override suspend fun onRejectedBeforeMutation(error: BackupError) {
                    terminalGate.await()
                }
            }
            val t2Owner = newOwner()
            val t2Effects = RecordingEffects(attemptId = t2Owner)

            val t1 = async {
                runtime.replace(restoreOperation(sourceFile(), t1Owner), t1Effects)
            }
            runCurrent() // T1 is parked INSIDE its terminal effect, still holding the mutex
            val t2 = async {
                runtime.replace(rollbackOperation(owner = t2Owner), t2Effects)
            }
            runCurrent()

            assertEquals(emptyList<String>(), t2Effects.calls, "T2 blocked behind T1's terminal")
            assertFalse(t2.isCompleted)

            terminalGate.complete(Unit)
            runCurrent()
            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, t1.await())
            assertInstanceOf(ReplacementOutcome.Completed::class.java, t2.await())
            assertTrue(t2Effects.calls.first() == "beforeMutation")
        }

    @Test
    fun `the ladder rolls back onto THIS attempt's undo not the older active ref`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "PREVIOUS-UNDO")
            val owner = newOwner()
            val expected = UndoRef(owner)
            coEvery { provider.replaceLiveDatabaseFromRestore(any()) } coAnswers {
                appliedRestoreRefs += firstArg<RestoreSourceRef>()
                protocolLog += "swap"
                BackupResult.Failure(BackupError.Io(IOException("rename failed")))
            }
            coEvery { provider.replaceLiveDatabaseFromUndo(any()) } coAnswers {
                appliedUndoRefs += firstArg<UndoRef>()
                protocolLog += "swap"
                BackupResult.Success(Unit)
            }

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner))

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(listOf(expected), appliedUndoRefs)
            assertEquals("PREVIOUS-UNDO", previous.readText(), "P is never substituted or changed")
        }

    @Test
    fun `the immutable undo exists before its Prepared journal claim`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val owner = newOwner()
            val expected = UndoRef(owner)
            var existedAtClaim = false
            val effects = RecordingEffects(
                attemptId = owner,
                onBefore = { existedAtClaim = undoFiles[expected]?.exists() == true },
            )

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertTrue(existedAtClaim, "Prepared can name only a completely published immutable N")
            assertEquals(IMMUTABLE_UNDO_CONTENT, checkNotNull(undoFiles[expected]).readText())
        }

    @Test
    fun `a record failure leaves previous active undo byte-for-byte intact`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "D0-PREVIOUS-UNDO-IMAGE")
            val owner = newOwner()
            val journal = FakeJournal()
            val effects = JournalEffects(
                attemptId = owner,
                journal = journal,
                kind = "Restore",
                log = protocolLog,
                failRecordTimes = Int.MAX_VALUE,
            )

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            assertEquals("D0-PREVIOUS-UNDO-IMAGE", previous.readText())
            assertEquals("Prepared", journal.phase)
            assertEquals(owner, journal.id)
            assertTrue(checkNotNull(undoFiles[UndoRef(owner)]).exists())
        }

    @Test
    fun `finalization pending after durable commit refuses candidate publication`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preflightOutcomes += StartupOutcome.FinalizationPending
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            assertEquals(
                listOf("beforeMutation", "mutationCommitted", "fatal"),
                effects.calls,
            )
            assertTrue(checkNotNull(undoFiles[UndoRef(owner)]).exists())
            assertEquals(0, epochAdvances, "candidate publication never happened")
        }

    @Test
    fun `post-finalization arm failure retries restored database without compensation`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val owner = newOwner()
            val undoRef = UndoRef(owner)
            val terminal = RestoreTerminal.RestoreSucceeded(
                owner = owner,
                restoredAtEpochMs = 123L,
                previousVersionAvailable = true,
            )
            coEvery { restoreStateRepository.readProtocol() } returns RestoreProtocolRead.Current(
                RestoreProtocolState(
                    installEpoch = InstallEpoch(
                        RestoreOwnerId("20000000-0000-4000-8000-000000000001"),
                    ),
                    attempt = null,
                    activeUndo = ActiveUndo(undoRef, originalDataDateEpochMs = 100L),
                    terminalOutbox = terminal,
                ),
            )
            preflightAction = {
                if (preflightCalls == 1) error("injected post-finalization arm failure")
            }
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(2, preflightCalls, "the finalized live DB receives one clean retry")
            assertEquals(listOf(RestoreSourceRef(owner)), appliedRestoreRefs)
            assertEquals(emptyList<UndoRef>(), appliedUndoRefs, "finalization forbids compensation")
            assertEquals(
                listOf("beforeMutation", "mutationCommitted", "committed"),
                effects.calls,
            )
        }

    @Test
    fun `a rollback whose exact source fails validation is rejected without swap`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            val source = ownedUndoFile(content = "TRUNCATED")
            coEvery { provider.validateUndo(DEFAULT_UNDO_REF) } returns BackupResult.Failure(
                BackupError.CorruptedBackup(reason = "database truncated"),
            )
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(rollbackOperation(owner = owner), effects)

            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                outcome,
            )
            assertInstanceOf(BackupError.CorruptedBackup::class.java, rejected.error)
            assertEquals(listOf("rejected"), effects.calls)
            assertFalse(protocolLog.contains("swap"))
            assertTrue(source.exists())
        }

    @Test
    fun `immutable undo creation failure rejects before journal close or swap`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            coEvery { provider.createUndo(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("undo creation failed")),
            )
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
            assertEquals(listOf("rejected"), effects.calls)
            assertTrue(protocolLog.isEmpty(), "no journal callback, close, or swap")
        }

    @Test
    fun `rollback mechanics failure is Fatal with exact compensation ref`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            coEvery { provider.replaceLiveDatabaseFromRestore(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("primary rename failed")),
            )
            coEvery { provider.replaceLiveDatabaseFromUndo(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("disk full")),
            )
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(UndoRef(owner), effects.compensationRef)
            assertEquals(listOf("beforeMutation", "beforeCompensation", "fatal"), effects.calls)
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
        }

    // Source-owner identity and exact-file application.

    @Test
    fun `a missing rollback ref is rejected and a different active ref is never substituted`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "OLDER-ACTIVE-P")
            val missingRef = UndoRef(newOwner())
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(rollbackOperation(missingRef, owner), effects)

            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                outcome,
            )
            assertInstanceOf(BackupError.CorruptedBackup::class.java, rejected.error)
            assertEquals(0, protocolLog.count { it == "swap" })
            assertEquals("OLDER-ACTIVE-P", previous.readText())
            assertEquals(listOf("rejected"), effects.calls)
        }

    @Test
    fun `an exact rollback applies A while unrelated active P survives`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val appliedRef = UndoRef(newOwner())
            val sourceA = ownedUndoFile(appliedRef, "A")
            val previous = ownedUndoFile(DEFAULT_UNDO_REF, "P")
            val owner = newOwner()
            val effects = RecordingEffects(owner)

            val outcome = runtime.replace(rollbackOperation(appliedRef, owner), effects)

            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(listOf(appliedRef), appliedUndoRefs)
            assertTrue(sourceA.exists(), "durable finalization owns deletion of applied A")
            assertEquals("P", previous.readText(), "unrelated active P survives")
            coVerify(exactly = 0) { provider.deleteUndo(DEFAULT_UNDO_REF) }
        }

    @Test
    fun `a rejection whose terminal persistence fails keeps its owned undo`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val lease = checkNotNull(runtime.awaitBackupWorkLease())
            val owner = newOwner()
            val effects = RecordingEffects(
                attemptId = owner,
                onRejectedBody = { error("journal resolve failed") },
            )

            val transaction = async {
                runtime.replace(restoreOperation(sourceFile(), owner), effects)
            }
            advanceTimeBy(3_000)
            runCurrent()

            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                transaction.await(),
            )
            assertNotNull(rejected.effectsError)
            assertNull(effects.undoRef, "a quiesce rejection never claimed Prepared state")
            assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertTrue(checkNotNull(undoFiles[UndoRef(owner)]).exists())
            lease.release()
        }

    @Test
    fun `ladder recovery whose owned undo vanished goes Fatal and never applies P`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "OLDER-P")
            val owner = newOwner()
            val expected = UndoRef(owner)
            coEvery { provider.replaceLiveDatabaseFromRestore(any()) } coAnswers {
                protocolLog += "swap"
                undoFiles.remove(expected)?.delete()
                BackupResult.Failure(BackupError.Io(IOException("rename failed")))
            }
            coEvery { provider.validateUndo(expected) } returns BackupResult.Failure(
                BackupError.CorruptedBackup("owned undo vanished"),
            )

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner))

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(1, protocolLog.count { it == "swap" })
            assertTrue(appliedUndoRefs.isEmpty())
            assertEquals("OLDER-P", previous.readText())
        }

    @Test
    fun `ladder recovery keeps exact owned undo through terminal resolution`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "OLDER-P")
            val owner = newOwner()
            val expected = UndoRef(owner)
            coEvery { provider.replaceLiveDatabaseFromRestore(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("rename failed")),
            )
            var presentAtRecovered = false
            val effects = RecordingEffects(
                attemptId = owner,
                onRecoveredBody = { presentAtRecovered = undoFiles[expected]?.exists() == true },
            )

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertTrue(presentAtRecovered)
            assertEquals(listOf(expected), appliedUndoRefs)
            assertEquals("OLDER-P", previous.readText())
        }

    // Inline rollback shares top-level exact-ref and journal rules.

    @Test
    fun `the inline rollback applies its exact ref and never another active ref`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "P-OLDER-ACTIVE")
            val sourceRef = UndoRef(newOwner())
            val source = ownedUndoFile(sourceRef, "A-INLINE")
            val inlineOwner = newOwner()
            val inlineEffects = RecordingEffects(inlineOwner).recordInto(protocolLog)
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = {
                if (preflightCalls == 1) {
                    inlineResult = runtime.rollbackFromUndo(sourceRef, inlineEffects)
                }
            }
            preflightOutcomes += StartupOutcome.RestartRequired

            val outcome = runtime.replace(restoreOperation(sourceFile()))

            assertInstanceOf(
                DatabaseReplacementResult.Committed::class.java,
                inlineResult,
                "inline result: $inlineResult",
            )
            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(sourceRef, appliedUndoRefs.last())
            assertTrue(source.exists(), "finalization owns exact source deletion")
            assertEquals("P-OLDER-ACTIVE", previous.readText())
        }

    @Test
    fun `the inline rollback with missing exact ref rejects without substitution`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "P-OLDER-ACTIVE")
            val missingRef = UndoRef(newOwner())
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = {
                if (preflightCalls == 1) {
                    inlineResult = runtime.rollbackFromUndo(
                        missingRef,
                        RecordingEffects(newOwner()),
                    )
                }
            }

            val outcome = runtime.replace(restoreOperation(sourceFile()))

            assertInstanceOf(
                DatabaseReplacementResult.RejectedBeforeMutation::class.java,
                inlineResult,
            )
            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals("P-OLDER-ACTIVE", previous.readText())
            assertFalse(appliedUndoRefs.contains(missingRef))
        }

    @Test
    fun `post-commit recovery journals exact compensation before applying N and preserves P`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val previous = ownedUndoFile(content = "P")
            graphFactoryFailures += 1
            val owner = newOwner()
            val effects = RecordingEffects(owner).recordInto(protocolLog)

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            val prepared = protocolLog.indexOf("beforeCompensation")
            val compensationSwap = protocolLog.lastIndexOf("swap")
            assertTrue(prepared in 0 until compensationSwap, "$protocolLog")
            assertEquals(UndoRef(owner), effects.compensationRef)
            assertNotNull(effects.compensationOwner)
            assertEquals("P", previous.readText())
        }

    @Test
    fun `a preflight inline rollback invalidates one candidate then a fresh attempt publishes`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val owner = newOwner()
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = {
                if (preflightCalls == 1) {
                    inlineResult = runtime.rollbackFromUndo(
                        UndoRef(owner),
                        RecordingEffects(newOwner()),
                    )
                }
            }
            preflightOutcomes += StartupOutcome.RestartRequired

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner))

            assertInstanceOf(DatabaseReplacementResult.Committed::class.java, inlineResult)
            val recovered = assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertEquals(2, preflightCalls)
            assertNotNull(recovered.generation)
        }

    @Test
    fun `a generation-1 orphan close-throw is Fatal never retryable rejection`() =
        runtimeTest { runtime ->
            graphFactoryFailures += 0
            closeFailureIndices += 0

            val outcome = runtime.replace(restoreOperation(sourceFile()))

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            assertThrows<IllegalStateException> { runtime.currentGeneration }
        }

    // Rollback replay and bounded-clear liveness pins.

    /** A minimal durable-journal double with the real ownership rules, for composed pins. */
    private class FakeJournal {
        var id: RestoreOwnerId? = null
        var kind: String? = null
        var phase: String? = null
        var ref: UndoRef? = null
        var restoreSourceRef: RestoreSourceRef? = null

        fun begin(
            attemptId: RestoreOwnerId,
            attemptKind: String,
            undoRef: UndoRef,
            sourceRef: RestoreSourceRef?,
        ): Boolean {
            if (id != null && id != attemptId) return false
            id = attemptId
            kind = attemptKind
            phase = "Prepared"
            ref = undoRef
            restoreSourceRef = sourceRef
            return true
        }

        fun beginCompensation(
            restoreOwner: RestoreOwnerId,
            rollbackOwner: RestoreOwnerId,
            appliedRef: UndoRef,
        ): Boolean {
            if (id != restoreOwner || kind != "Restore") return false
            id = rollbackOwner
            kind = "Rollback"
            phase = "Prepared"
            ref = appliedRef
            restoreSourceRef = null
            return true
        }

        fun commit(attemptId: RestoreOwnerId): Boolean {
            if (id != attemptId) return false
            phase = "Committed"
            return true
        }

        fun resolve(attemptId: RestoreOwnerId): Boolean {
            if (id != attemptId) return false
            id = null
            kind = null
            phase = null
            ref = null
            restoreSourceRef = null
            return true
        }
    }

    /** Journal-backed effects double; [failRecordTimes] injects durable-record failure. */
    private inner class JournalEffects(
        override val attemptId: RestoreOwnerId,
        private val journal: FakeJournal,
        private val kind: String,
        private val log: MutableList<String>,
        private var failRecordTimes: Int = 0,
        private var failCompensationRecordTimes: Int = 0,
    ) : DatabaseReplacementEffects {

        var availabilityCleared = false
        var successPublished = false
        var acknowledged = false
        private var terminalOwner = attemptId

        override suspend fun onBeforeMutation(
            undoRef: UndoRef,
            restoreSourceRef: RestoreSourceRef?,
        ) {
            check(journal.begin(attemptId, kind, undoRef, restoreSourceRef)) {
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

        override suspend fun onBeforeCompensation(
            rollbackOwner: RestoreOwnerId,
            appliedRef: UndoRef,
        ) {
            check(journal.beginCompensation(attemptId, rollbackOwner, appliedRef)) {
                "restore journal not owned by $attemptId"
            }
            terminalOwner = rollbackOwner
            log += "journal-compensation-prepared"
        }

        override suspend fun onCompensationCommitted(rollbackOwner: RestoreOwnerId) {
            if (failCompensationRecordTimes > 0) {
                failCompensationRecordTimes--
                log += "journal-compensation-record-FAILED"
                error("durable compensation record failed")
            }
            check(journal.commit(rollbackOwner)) { "rollback journal not owned by $rollbackOwner" }
            log += "journal-compensation-committed"
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
            journal.resolve(terminalOwner)
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
    fun `a requested rollback retry commits as requested operation never compensation`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val source = ownedUndoFile(content = "UNDO")
            val journal = FakeJournal()
            val owner = newOwner()
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFromUndo(DEFAULT_UNDO_REF) } coAnswers {
                appliedUndoRefs += DEFAULT_UNDO_REF
                protocolLog += "swap"
                if (swaps++ == 0) {
                    BackupResult.Failure(BackupError.Io(IOException("transient rename failure")))
                } else {
                    BackupResult.Success(Unit)
                }
            }
            val observedPhases = mutableListOf<String>()
            preflightAction = { observedPhases += "${journal.phase}/${journal.kind}" }
            val effects = JournalEffects(owner, journal, "Rollback", protocolLog)

            val outcome = runtime.replace(rollbackOperation(owner = owner), effects)

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertNull(completed.effectsError)
            assertEquals(listOf(DEFAULT_UNDO_REF, DEFAULT_UNDO_REF), appliedUndoRefs)
            assertEquals(listOf("Committed/Rollback"), observedPhases)
            assertEquals(0, protocolLog.count { it == "journal-compensation-prepared" })
            assertEquals(1, protocolLog.count { it == "terminal-committed" })
            assertNull(journal.id)
            assertNotNull(completed.generation)
            assertTrue(source.exists(), "state finalization owns source deletion")
        }

    @Test
    fun `a retry whose record persistently fails never runs committed terminal`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val source = ownedUndoFile(content = "UNDO")
            val journal = FakeJournal()
            val owner = newOwner()
            val effects = JournalEffects(
                attemptId = owner,
                journal = journal,
                kind = "Rollback",
                log = protocolLog,
                failRecordTimes = Int.MAX_VALUE,
            )

            val outcome = runtime.replace(rollbackOperation(owner = owner), effects)

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(0, protocolLog.count { it == "terminal-committed" })
            assertFalse(effects.availabilityCleared)
            assertFalse(effects.successPublished)
            assertFalse(effects.acknowledged)
            assertEquals("Prepared", journal.phase)
            assertEquals(owner, journal.id)
            coVerify(exactly = 0) { provider.deleteUndo(DEFAULT_UNDO_REF) }
            assertEquals("UNDO", source.readText())
        }

    @Test
    fun `the inline rollback disposes candidate through one teardown before exact-ref swap`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val sourceRef = UndoRef(newOwner())
            ownedUndoFile(sourceRef, "A-INLINE-SOURCE")
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
                    runtime.rollbackFromUndo(
                        sourceRef,
                        RecordingEffects(newOwner()).recordInto(protocolLog),
                    )
                }
            }
            preflightOutcomes += StartupOutcome.RestartRequired

            val outcome = runtime.replace(restoreOperation(sourceFile()))

            assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertTrue(vmCleared)
            val candidateClose = closeIndices()[1]
            val inlineSwap = protocolLog.lastIndexOf("swap")
            assertTrue(protocolLog.indexOf("candidate-vm-cleared") in 0 until candidateClose)
            assertTrue(protocolLog.indexOf("candidate-job-ended") in 0 until candidateClose)
            assertTrue(candidateClose < inlineSwap)
            assertEquals(sourceRef, appliedUndoRefs.last())
        }

    @Test
    fun `an unjoinable candidate stops inline rollback Fatal with zero later renames`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val sourceRef = UndoRef(newOwner())
            val source = ownedUndoFile(sourceRef, "A-INLINE-SOURCE")
            val never = CompletableDeferred<Unit>()
            var inlineResult: DatabaseReplacementResult? = null
            preflightAction = { generation ->
                if (preflightCalls == 1) {
                    generation.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                        withContext(NonCancellable) { never.await() }
                    }
                    kotlinx.coroutines.yield()
                    inlineResult = runtime.rollbackFromUndo(
                        sourceRef,
                        RecordingEffects(newOwner()).recordInto(protocolLog),
                    )
                }
            }

            val transaction = async { runtime.replace(restoreOperation(sourceFile())) }
            advanceTimeBy(10_000)
            runCurrent()

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, transaction.await())
            assertInstanceOf(DatabaseReplacementResult.FailedAfterMutation::class.java, inlineResult)
            assertEquals(1, protocolLog.count { it == "swap" })
            assertTrue(source.exists())
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            assertEquals(0, epochAdvances)
            assertNull(runtime.admitUiGeneration(1))
            never.complete(Unit)
            runCurrent()
        }

    @Test
    fun `a clear that is queued but never runs cannot hang the machine`() {
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
            runtime.currentGeneration

            val transaction = async {
                runtime.replace(restoreOperation(sourceFile()))
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

            // The wedged dispatcher finally runs its queue: the late clear changes nothing.
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
    fun `a restore record failure has no committed terminal before compensation is durable`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val journal = FakeJournal()
            val owner = newOwner()
            val restoreEffects = JournalEffects(
                attemptId = owner,
                journal = journal,
                kind = "Restore",
                log = protocolLog,
                failRecordTimes = 1,
            )

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), restoreEffects)

            val recoveredOutcome =
                assertInstanceOf(ReplacementOutcome.RecoveredByRollback::class.java, outcome)
            assertNull(recoveredOutcome.effectsError)
            assertEquals(0, protocolLog.count { it == "terminal-committed" })
            assertEquals(1, protocolLog.count { it == "terminal-recovered" })
            val prepared = protocolLog.indexOf("journal-compensation-prepared")
            val durable = protocolLog.indexOf("journal-compensation-committed")
            val terminal = protocolLog.indexOf("terminal-recovered")
            assertTrue(prepared in 0 until durable && durable < terminal, "$protocolLog")
            assertFalse(restoreEffects.successPublished)
            assertNull(journal.id)
        }

    @Test
    fun `one-shot requested rollback record failure commits once without stale terminal`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            ownedUndoFile(content = "UNDO")
            val journal = FakeJournal()
            val owner = newOwner()
            val observedPhases = mutableListOf<String>()
            preflightAction = { observedPhases += "${journal.phase}/${journal.kind}" }
            val effects = JournalEffects(
                attemptId = owner,
                journal = journal,
                kind = "Rollback",
                log = protocolLog,
                failRecordTimes = 1,
            )

            val outcome = runtime.replace(rollbackOperation(owner = owner), effects)

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
    fun `restart-process record failure keeps Prepared and immutable undo`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            val journal = FakeJournal()
            val owner = newOwner()
            val effects = JournalEffects(
                attemptId = owner,
                journal = journal,
                kind = "Restore",
                log = protocolLog,
                failRecordTimes = Int.MAX_VALUE,
            )

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            assertEquals("Prepared", journal.phase, "the journal survives at Prepared")
            assertEquals(UndoRef(owner), journal.ref)
            assertTrue(checkNotNull(undoFiles[UndoRef(owner)]).exists())
            assertEquals(
                0,
                protocolLog.count { it == "terminal-committed" },
                "no committed terminal, no success, no availability: $protocolLog",
            )
            assertFalse(effects.successPublished)
            assertFalse(effects.availabilityCleared)
        }

    @Test
    fun `compensation record failure cannot erase Prepared exact rollback journal`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            graphFactoryFailures += 1
            val journal = FakeJournal()
            val owner = newOwner()
            val restoreEffects = JournalEffects(
                attemptId = owner,
                journal = journal,
                kind = "Restore",
                log = protocolLog,
                failCompensationRecordTimes = Int.MAX_VALUE,
            )

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), restoreEffects)

            assertInstanceOf(ReplacementOutcome.Fatal::class.java, outcome)
            assertEquals(
                0,
                protocolLog.count { it == "terminal-committed" },
                "the recovery's committed terminal must never run over its failed record: " +
                    "$protocolLog",
            )
            assertEquals("Prepared", journal.phase, "the re-claimed attempt stays Prepared")
            assertEquals("Rollback", journal.kind)
            assertEquals(UndoRef(owner), journal.ref)
            assertTrue(checkNotNull(undoFiles[UndoRef(owner)]).exists())
        }

    @Test
    fun `file mutation alone never selects committed terminal durable phase is load-bearing`() =
        runtimeTest(replacementPolicy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            val owner = newOwner()
            val effects = RecordingEffects(
                attemptId = owner,
                onMutationCommittedBody = { error("durable journal write failed") },
            )

            val outcome = runtime.replace(restoreOperation(sourceFile(), owner), effects)

            assertTrue(protocolLog.contains("swap"), "the file mutation genuinely happened")
            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            assertEquals(
                listOf("beforeMutation", "mutationCommitted", "failedAfterMutation"),
                effects.calls,
                "durable phase, not file mutation, selects the terminal: ${effects.calls}",
            )
            assertTrue(checkNotNull(undoFiles[UndoRef(owner)]).exists())
        }

    private companion object {
        /** The bytes an immutable attempt-owned undo carries in this mechanics fixture. */
        const val IMMUTABLE_UNDO_CONTENT = "undo"
        val DEFAULT_UNDO_REF = UndoRef(
            RestoreOwnerId("00000000-0000-4000-8000-000000000011"),
        )
    }
}
