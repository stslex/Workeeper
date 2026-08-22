// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import io.github.stslex.workeeper.core.core.images.ImageStorage
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The REQUEST_CHANGES matrix for the runtime-owned replacement transaction: submission
 * ownership under caller cancellation (finding 1), closed lease admission (finding 2), the
 * id-bound UI gate (finding 3), phase-aware outcomes (finding 4), the staged failure ladder +
 * Fatal discipline (findings 5/6a), and atomic same-operation single-flight (finding 6b).
 * Injection seams are the runtime's own factories and the mocked per-generation
 * graph/provider — no hooks, no proxies.
 */
internal class AppRuntimeReplacementTest {

    private val context = mockk<Context>(relaxed = true)
    private val snapshotSource = File("snapshot_source.db")
    private val preservedFile = File("pre_restore_backup.db")

    private val databases = mutableListOf<AppDatabase>()
    private var dbFactoryFailures = mutableListOf<Int>()
    private val closedDatabases = mutableListOf<AppDatabase>()
    private var closeFailures = mutableListOf<AppDatabase>()
    private var graphFactoryFailures = mutableListOf<Int>()
    private var builtGraphs = 0

    private var preflightOutcomes = ArrayDeque<StartupOutcome>()
    private var preflightAction: (suspend (RuntimeGeneration) -> Unit)? = null
    private var preflightCalls = 0

    /** One shared provider mock — every generation's graph returns it (file mechanics are global). */
    private val provider = mockk<DatabaseSnapshotProvider> {
        coEvery { validateSnapshotForRestore(any()) } returns BackupResult.Success(Unit)
        coEvery { replaceLiveDatabaseFile(any()) } returns BackupResult.Success(Unit)
        every { getPreRestoreBackupFile() } returns preservedFile
        coEvery { deletePreRestoreBackup() } returns Unit
    }

    private fun runtimeTest(
        policy: ReplacementPolicy = ReplacementPolicy.RebuildInProcess,
        body: suspend kotlinx.coroutines.test.TestScope.(AppRuntime) -> Unit,
    ) = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = AppRuntime(
            applicationContext = context,
            dbFactory = {
                val index = databases.size
                if (index in dbFactoryFailures) {
                    databases += mockk<AppDatabase>(relaxed = true)
                    error("injected db construction failure #$index")
                }
                mockk<AppDatabase>(relaxed = true).also { databases += it }
            },
            imageStorageFactory = { mockk<ImageStorage>(relaxed = true) },
            graphFactory = { _, _, _, _, _ ->
                if (builtGraphs++ in graphFactoryFailures) error("injected graph construction failure")
                mockk<AppGraph>(relaxed = true) {
                    every { databaseSnapshotProvider } returns provider
                }
            },
            preflight = { generation ->
                preflightCalls++
                preflightAction?.invoke(generation)
                preflightOutcomes.removeFirstOrNull() ?: StartupOutcome.Proceed
            },
            closeDatabase = { database ->
                closedDatabases += database
                if (database in closeFailures) error("injected close failure")
            },
            replacementPolicy = policy,
            policy = RuntimeTransitionPolicy(
                mainDispatcher = dispatcher,
                hostDispatcher = dispatcher,
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        body(runtime)
    }

    // ------------------------------------------------------------------ happy paths / phases --

    @Test
    fun `rebuild restore happy path - close, swap, fresh db generation, publish`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val genTwo = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
                .generation!!
            assertNotSame(genOne.database, genTwo.database)
            assertEquals(genOne.dbGeneration + 1, genTwo.dbGeneration)
            assertTrue(genOne.database in closedDatabases, "outgoing database must be closed")
            coVerify(exactly = 1) { provider.validateSnapshotForRestore(snapshotSource) }
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(snapshotSource) }
            assertFalse(genOne.lifetime.isActive, "outgoing lifetime ends before close")
            assertSame(genTwo, runtime.currentGeneration)
            assertSame(genTwo, (runtime.phases.value as RuntimePhase.Serving).generation)
        }

    @Test
    fun `validation failure - RejectedBeforeMutation, nothing closed, generation 1 intact`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            coEvery { provider.validateSnapshotForRestore(any()) } returns BackupResult.Failure(
                BackupError.BackupTooNew(backupSchemaVersion = 99, appSchemaVersion = 6),
            )

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                outcome,
            )
            assertInstanceOf(BackupError.BackupTooNew::class.java, rejected.error)
            assertTrue(closedDatabases.isEmpty())
            assertTrue(genOne.lifetime.isActive)
            assertSame(genOne, runtime.currentGeneration)
        }

    @Test
    fun `beforeMutation hook failure - RejectedBeforeMutation, nothing closed or swapped`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(snapshotSource),
                ReplacementHooks(beforeMutation = { error("marker write failed") }),
            )

            assertInstanceOf(ReplacementOutcome.RejectedBeforeMutation::class.java, outcome)
            assertTrue(closedDatabases.isEmpty())
            coVerify(exactly = 0) { provider.replaceLiveDatabaseFile(any()) }
            assertSame(genOne, runtime.currentGeneration)
        }

    // ---------------------------------------------------- finding 1: submission ownership -----

    @Test
    fun `settings-store cancellation - the transaction completes despite the dead caller`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightAction = { gate.await() }
            var callerCancelled = false

            // The real initiator shape: a Store-scope coroutine the Transitioning disposal kills.
            val callerJob = launch {
                try {
                    runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))
                } catch (e: CancellationException) {
                    callerCancelled = true
                    throw e
                }
            }
            runCurrent()
            callerJob.cancel()
            runCurrent()
            assertTrue(callerCancelled, "the caller's await must be cancelled")

            gate.complete(Unit)
            runCurrent()

            // The transaction was NOT cancelled and NOT stranded: a successor generation serves.
            val serving = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertEquals(2, serving.generation.id, "the transaction must have published gen 2")
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(snapshotSource) }
        }

    @Test
    fun `undo initiator inside the outgoing lifetime - no deadlock, hook runs, txn completes`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            var hookRan = false
            var initiatorCancelled = false
            val initiatorDone = CompletableDeferred<Unit>()

            // The real undo shape: the initiator's scope is a CHILD of the outgoing lifetime —
            // the transaction's own quiesce cancels it mid-await. The cancel is what breaks the
            // await↔join cycle; the join completes; the transaction proceeds; the post-commit
            // hook runs under TRANSACTION ownership.
            genOne.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                try {
                    runtime.rollbackToPreRestoreBackup(onCommitted = { hookRan = true })
                } catch (e: CancellationException) {
                    initiatorCancelled = true
                    initiatorDone.complete(Unit)
                    throw e
                }
                initiatorDone.complete(Unit)
            }
            runCurrent()
            initiatorDone.await()

            assertTrue(initiatorCancelled, "the initiator dies with its generation's lifetime")
            assertTrue(hookRan, "post-commit effects must run under transaction ownership")
            val serving = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertEquals(2, serving.generation.id)
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(preservedFile) }
            coVerify(exactly = 1) { provider.deletePreRestoreBackup() }
        }

    // ------------------------------------------------------- finding 6b: atomic single-flight --

    @Test
    fun `simultaneous same-operation submissions share ONE transaction and outcome`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightAction = { gate.await() }

            val first = async { runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource)) }
            val second = async { runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource)) }
            runCurrent()
            gate.complete(Unit)

            val a = first.await()
            val b = second.await()
            assertSame(a, b, "same-operation requests must receive the SAME outcome object")
            assertEquals(1, preflightCalls, "exactly one transaction may run")
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(snapshotSource) }
        }

    @Test
    fun `different operation gets its OWN serialized result - never the other operation's`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            every { provider.getPreRestoreBackupFile() } returns null

            val restore = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))
            val rollback = runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup)

            assertInstanceOf(ReplacementOutcome.Completed::class.java, restore)
            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                rollback,
            )
            assertInstanceOf(BackupError.CorruptedBackup::class.java, rejected.error)
        }

    // -------------------------------------------------------- finding 2: closed admission -----

    @Test
    fun `unreleased worker lease aborts the replacement before close - assets intact`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val lease = runtime.acquireBackupWorkLease()

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                outcome,
            )
            assertTrue(rejected.error.toString().contains("lease"), "reason: ${rejected.error}")
            assertTrue(closedDatabases.isEmpty(), "never close after a failed lease drain")
            coVerify(exactly = 0) { provider.replaceLiveDatabaseFile(any()) }
            assertSame(genOne, runtime.currentGeneration)
            assertTrue(genOne.lifetime.isActive, "the abort precedes the lifetime join")

            // Admission reopened: after releasing, the next replacement succeeds.
            lease.release()
            val second = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))
            assertInstanceOf(ReplacementOutcome.Completed::class.java, second)
        }

    @Test
    fun `worker admitted during the closed window binds to the NEW generation`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightAction = { gate.await() }

            val transition = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))
            }
            runCurrent() // transaction parked in preflight; admission is CLOSED

            // A real WorkManager thread parks in acquire until admission reopens at publish.
            val acquired = CountDownLatch(1)
            var leaseDeps: Any? = null
            val workerThread = thread {
                val lease = runtime.acquireBackupWorkLease()
                leaseDeps = lease.deps
                lease.release()
                acquired.countDown()
            }
            // The thread must be BLOCKED while admission is closed.
            assertFalse(acquired.await(150, TimeUnit.MILLISECONDS), "admission must be closed")

            gate.complete(Unit)
            runCurrent()
            val genTwo = assertInstanceOf(ReplacementOutcome.Completed::class.java, transition.await())
                .generation!!
            assertTrue(acquired.await(5, TimeUnit.SECONDS), "acquire must resume after publish")
            workerThread.join(5_000)
            assertSame(
                genTwo.graph,
                leaseDeps,
                "a worker admitted during the window must bind to the NEW generation",
            )
        }

    // ------------------------------------------------------------- finding 3: UI gate ---------

    @Test
    fun `ui gate - wrong and stale ids never release it, all attachments must detach`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            runtime.onUiGenerationAttached(genOne.id)
            runtime.onUiGenerationAttached(genOne.id) // overlapping composition (recreation)

            val transition = async {
                runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))
            }
            runCurrent()
            assertEquals(RuntimePhase.Transitioning, runtime.phases.value)
            assertTrue(transition.isActive, "gated on UI detachment")

            runtime.onUiGenerationDisposed(genOne.id + 41) // wrong id — must NOT release
            runCurrent()
            assertTrue(transition.isActive, "a wrong id must never release the gate")

            runtime.onUiGenerationDisposed(genOne.id) // first of two attachments
            runCurrent()
            assertTrue(transition.isActive, "every attachment must detach before the gate opens")

            runtime.onUiGenerationDisposed(genOne.id) // second — gate opens
            runCurrent()
            assertInstanceOf(ReplacementOutcome.Completed::class.java, transition.await())

            // A LATE dispose for the dead generation is harmless bookkeeping.
            runtime.onUiGenerationDisposed(genOne.id)
        }

    // ------------------------------------------- findings 4 + 5: phases, ladder, Fatal --------

    @Test
    fun `restart-process policy - validate, close, swap, no rebuild, no phase change`() =
        runtimeTest(policy = ReplacementPolicy.RestartProcess) { runtime ->
            val genOne = runtime.currentGeneration

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(null, completed.generation, "RestartProcess publishes no successor")
            assertTrue(genOne.database in closedDatabases)
            assertEquals(1, databases.size, "no rebuild under RestartProcess")
            assertEquals(0, preflightCalls)
            assertSame(genOne, (runtime.phases.value as RuntimePhase.Serving).generation)
            // Startable from an already-terminal generation (undo IoFailure re-tap):
            val second = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))
            assertInstanceOf(ReplacementOutcome.Completed::class.java, second)
        }

    @Test
    fun `restart-process post-close failure - FailedAfterMutation, runtime deletes no assets`() =
        runtimeTest(policy = ReplacementPolicy.RestartProcess) { runtime ->
            runtime.currentGeneration
            coEvery { provider.replaceLiveDatabaseFile(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("rename failed")),
            )

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            assertInstanceOf(ReplacementOutcome.FailedAfterMutation::class.java, outcome)
            // The runtime deleted nothing: the preserved file is the recovery path.
            coVerify(exactly = 0) { provider.deletePreRestoreBackup() }
        }

    @Test
    fun `close failure - never rename, RejectedBeforeMutation, generation keeps serving`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            closeFailures.add(genOne.database)

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val rejected = assertInstanceOf(
                ReplacementOutcome.RejectedBeforeMutation::class.java,
                outcome,
            )
            assertTrue(rejected.error.toString().contains("close"), "reason: ${rejected.error}")
            coVerify(exactly = 0) { provider.replaceLiveDatabaseFile(any()) }
            assertSame(genOne, (runtime.phases.value as RuntimePhase.Serving).generation)
        }

    @Test
    fun `file replacement failure after close - rollback plus one fresh generation`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            coEvery { provider.replaceLiveDatabaseFile(snapshotSource) } returns BackupResult.Failure(
                BackupError.Io(IOException("atomic rename failed")),
            )
            coEvery { provider.replaceLiveDatabaseFile(preservedFile) } returns BackupResult.Success(Unit)

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val genTwo = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome).generation!!
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(preservedFile) }
            coVerify(exactly = 1) { provider.deletePreRestoreBackup() }
            assertNotSame(genOne, genTwo)
            assertEquals(1, preflightCalls)
        }

    @Test
    fun `graphFactory failure after db creation - orphan db closed, ladder recovers`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // Candidate #1's graph build fails AFTER its database was built: the orphan database
            // must be closed before the ladder's rollback replaces the file under it (finding 5).
            graphFactoryFailures.add(1)
            coEvery { provider.replaceLiveDatabaseFile(preservedFile) } returns BackupResult.Success(Unit)

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val published = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            val orphanDb = databases[1]
            assertTrue(orphanDb in closedDatabases, "the orphaned candidate database must be closed")
            assertTrue(published.generation!!.database !== orphanDb)
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(preservedFile) }
        }

    @Test
    fun `rollback op - primary swap marks rolledBack BEFORE consuming, candidate retry allowed`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // First candidate fails; because the PRIMARY rollback swap already rolled the file
            // back, the ladder takes the allowed follow-up attempt instead of Fatal (finding 6a).
            preflightOutcomes.addAll(listOf(StartupOutcome.RouteToRecovery, StartupOutcome.Proceed))

            val outcome = runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup)

            val published = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(2, preflightCalls, "one failed attempt + the allowed follow-up")
            // Exactly ONE swap (the primary) and ONE consumption — no spurious second rollback.
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(preservedFile) }
            coVerify(exactly = 1) { provider.deletePreRestoreBackup() }
            assertSame(published.generation, runtime.currentGeneration)
        }

    @Test
    fun `preflight scenario-1 rollback runs INLINE via the transaction marker, then retry publishes`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preflightOutcomes.addAll(listOf(StartupOutcome.RestartRequired, StartupOutcome.Proceed))
            var inlineResult: Any? = null
            preflightAction = { _ ->
                if (preflightCalls == 1) {
                    inlineResult = runtime.rollbackToPreRestoreBackup()
                }
            }

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            assertInstanceOf(DatabaseReplacementResult.Committed::class.java, inlineResult)
            val published = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(2, preflightCalls, "exactly one bounded retry after the inline rollback")
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(preservedFile) }
            coVerify(exactly = 1) { provider.deletePreRestoreBackup() }
            assertSame(published.generation, runtime.currentGeneration)
        }

    @Test
    fun `rollback mechanics failure - Fatal - holders and admission reject all reads`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            coEvery { provider.replaceLiveDatabaseFile(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("disk full")),
            )

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            assertEquals(ReplacementOutcome.Fatal, outcome)
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            // No closed generation is ever exposed (finding 5): both entry points THROW.
            assertThrows(IllegalStateException::class.java) { runtime.currentGeneration }
            assertThrows(IllegalStateException::class.java) { runtime.acquireBackupWorkLease() }
            assertEquals(0, preflightCalls, "no candidate may be preflighted on an unreplaced file")
        }

    @Test
    fun `preflight failing twice exhausts the ladder - Fatal`() = runtimeTest { runtime ->
        runtime.currentGeneration
        preflightOutcomes.addAll(listOf(StartupOutcome.RouteToRecovery, StartupOutcome.RouteToRecovery))

        val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

        assertEquals(ReplacementOutcome.Fatal, outcome)
        assertEquals(2, preflightCalls, "one attempt + one bounded rollback-recovery attempt")
        assertEquals(RuntimePhase.Fatal, runtime.phases.value)
    }

    @Test
    fun `committed hook runs on the transaction after publish - its failure is contained`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            var hookGenerationId: Int? = null

            val outcome = runtime.replace(
                ReplacementOperation.RestoreFromSnapshot(snapshotSource),
                ReplacementHooks(onCommitted = {
                    hookGenerationId = (runtime.phases.value as RuntimePhase.Serving).generation.id
                    error("hook failure must be contained")
                }),
            )

            // The hook observed the PUBLISHED successor; its failure did not poison the outcome.
            assertEquals(2, hookGenerationId)
            assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
        }
}
