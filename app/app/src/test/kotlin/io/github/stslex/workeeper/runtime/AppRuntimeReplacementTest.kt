// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.di.AppGraph
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException

/**
 * The §11.3 failure-injection matrix for the runtime-owned database replacement transaction
 * (`kmp-phase-5-startup-processor.md` §8.4/§8.5). Injection seams are the runtime's own
 * factories and the mocked per-generation graph/provider — no hooks, no proxies:
 * quiesce (policy drains), close (mocked db), file replacement + rollback (mocked provider),
 * new-DB construction (dbFactory), migration/preflight (preflight fn).
 */
internal class AppRuntimeReplacementTest {

    private val context = mockk<Context>(relaxed = true)
    private val snapshotSource = File("snapshot_source.db")
    private val preservedFile = File("pre_restore_backup.db")

    private val databases = mutableListOf<AppDatabase>()
    private var dbFactoryFailures = mutableListOf<Int>()
    private val closedDatabases = mutableListOf<AppDatabase>()

    private var preflightOutcomes = ArrayDeque<StartupOutcome>()
    private var preflightAction: (suspend (RuntimeGeneration) -> Unit)? = null
    private var preflightCalls = 0
    private var drainWorkersBlocks = false

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
                mockk<AppGraph>(relaxed = true) {
                    every { databaseSnapshotProvider } returns provider
                }
            },
            preflight = { generation ->
                preflightCalls++
                preflightAction?.invoke(generation)
                preflightOutcomes.removeFirstOrNull() ?: StartupOutcome.Proceed
            },
            closeDatabase = { closedDatabases += it },
            replacementPolicy = policy,
            policy = RuntimeTransitionPolicy(
                drainWorkers = { if (drainWorkersBlocks) awaitCancellation() },
                mainDispatcher = UnconfinedTestDispatcher(testScheduler),
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        body(runtime)
    }

    @Test
    fun `rebuild restore happy path - close, swap, fresh db generation, publish`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val genTwo = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
                .generation!!
            // The R2 unit handed over coherently: NEW db object from the factory, next dbGeneration.
            assertNotSame(genOne.database, genTwo.database)
            assertEquals(genOne.dbGeneration + 1, genTwo.dbGeneration)
            assertEquals(2, databases.size, "exactly one fresh database generation must be built")
            assertTrue(genOne.database in closedDatabases, "outgoing database must be closed")
            coVerify(exactly = 1) { provider.validateSnapshotForRestore(snapshotSource) }
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(snapshotSource) }
            assertFalse(genOne.lifetime.isActive, "outgoing lifetime ends with its generation")
            assertSame(genTwo, runtime.currentGeneration)
            assertSame(genTwo, (runtime.phases.value as RuntimePhase.Serving).generation)
        }

    @Test
    fun `validation failure before close - generation 1 keeps serving, database open`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            coEvery { provider.validateSnapshotForRestore(any()) } returns BackupResult.Failure(
                BackupError.BackupTooNew(backupSchemaVersion = 99, appSchemaVersion = 6),
            )

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val failed = assertInstanceOf(ReplacementOutcome.Failed::class.java, outcome)
            assertInstanceOf(BackupError.BackupTooNew::class.java, failed.error)
            assertFalse(genOne.database in closedDatabases, "database must NOT be closed")
            assertTrue(genOne.lifetime.isActive)
            assertSame(genOne, runtime.currentGeneration)
        }

    @Test
    fun `restart-process policy - validate, close, swap, no rebuild, no phase change`() =
        runtimeTest(policy = ReplacementPolicy.RestartProcess) { runtime ->
            val genOne = runtime.currentGeneration

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val completed = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(null, completed.generation, "RestartProcess publishes no successor")
            assertTrue(genOne.database in closedDatabases, "outgoing database must be closed")
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(snapshotSource) }
            assertEquals(1, databases.size, "no rebuild under RestartProcess")
            assertEquals(0, preflightCalls)
            // Today's shipped shape: the app keeps running on the terminal generation until the
            // caller's process restart lands — the phase does not change.
            assertSame(genOne, (runtime.phases.value as RuntimePhase.Serving).generation)
            // Startable from an already-terminal generation (undo IoFailure re-tap):
            val second = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))
            assertInstanceOf(ReplacementOutcome.Completed::class.java, second)
        }

    @Test
    fun `rollback with no preserved file - CorruptedBackup, nothing closed`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            every { provider.getPreRestoreBackupFile() } returns null

            val outcome = runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup)

            val failed = assertInstanceOf(ReplacementOutcome.Failed::class.java, outcome)
            assertInstanceOf(BackupError.CorruptedBackup::class.java, failed.error)
            assertFalse(genOne.database in closedDatabases, "database must NOT be closed")
            assertSame(genOne, runtime.currentGeneration)
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
            // The ladder's branch (b): rollback mechanics + exactly one fresh-generation attempt.
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(preservedFile) }
            coVerify(exactly = 1) { provider.deletePreRestoreBackup() }
            assertNotSame(genOne, genTwo)
            assertEquals(1, preflightCalls)
        }

    @Test
    fun `rollback mechanics failure after close - Fatal, no generation serving, never re-served`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            coEvery { provider.replaceLiveDatabaseFile(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("disk full")),
            )

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            assertEquals(ReplacementOutcome.Fatal, outcome)
            // The closed generation must never resume serving: the phase STAYS Transitioning.
            assertEquals(RuntimePhase.Transitioning, runtime.phases.value)
            assertEquals(0, preflightCalls, "no candidate may be preflighted on an unreplaced file")
        }

    @Test
    fun `new db construction failure - rollback ladder builds the successor on retry`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            // dbFactory call #1 (index 1) fails — the first candidate build after the swap.
            dbFactoryFailures.add(1)

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            val genTwo = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome).generation!!
            // Ladder: build failed → rollback mechanics → ONE more attempt succeeded.
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(preservedFile) }
            assertEquals(3, databases.size, "gen1 + failed candidate + successful candidate")
            assertSame(genTwo, runtime.currentGeneration)
        }

    @Test
    fun `preflight scenario-1 rollback runs INLINE via the transaction marker, then retry publishes`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preflightOutcomes.addAll(listOf(StartupOutcome.RestartRequired, StartupOutcome.Proceed))
            var inlineResult: BackupResult<Unit>? = null
            preflightAction = { _ ->
                if (preflightCalls == 1) {
                    // The coordinator's failure path, re-entering the seam from INSIDE Preflight —
                    // must inline into the CURRENT transaction, never deadlock on the mutex.
                    inlineResult = runtime.rollbackToPreRestoreBackup()
                }
            }

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            assertEquals(BackupResult.Success(Unit), inlineResult)
            val published = assertInstanceOf(ReplacementOutcome.Completed::class.java, outcome)
            assertEquals(2, preflightCalls, "exactly one bounded retry after the inline rollback")
            // Inline rollback consumed the preserved slot through the production sequence.
            coVerify(exactly = 1) { provider.replaceLiveDatabaseFile(preservedFile) }
            coVerify(exactly = 1) { provider.deletePreRestoreBackup() }
            assertSame(published.generation, runtime.currentGeneration)
        }

    @Test
    fun `preflight failing twice exhausts the ladder - Fatal`() = runtimeTest { runtime ->
        runtime.currentGeneration
        preflightOutcomes.addAll(listOf(StartupOutcome.RouteToRecovery, StartupOutcome.RouteToRecovery))

        val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

        assertEquals(ReplacementOutcome.Fatal, outcome)
        assertEquals(2, preflightCalls, "one attempt + one bounded rollback-recovery attempt")
        assertEquals(RuntimePhase.Transitioning, runtime.phases.value)
    }

    @Test
    fun `same operation coalesces onto the in-flight transaction`() = runtimeTest { runtime ->
        runtime.currentGeneration
        val gate = CompletableDeferred<Unit>()
        preflightAction = { gate.await() }

        val first = async { runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource)) }
        runCurrent()
        val second = async { runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource)) }
        runCurrent()
        gate.complete(Unit)

        val a = first.await()
        val b = second.await()
        assertSame(a, b, "same-operation requests must receive the SAME outcome object")
        assertEquals(1, preflightCalls, "exactly one transaction may run")
    }

    @Test
    fun `different operation queues behind and gets its OWN result - never the other operation's`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            every { provider.getPreRestoreBackupFile() } returns null

            val restore = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))
            val rollback = runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup)

            assertInstanceOf(ReplacementOutcome.Completed::class.java, restore)
            // An undo coalescing onto the restore's success would clear the undo slot and publish
            // UndoRestoreSuccess for a rollback that never ran — the exact silent-failure class
            // review v2 condition 3 exists to kill.
            val failed = assertInstanceOf(ReplacementOutcome.Failed::class.java, rollback)
            assertInstanceOf(BackupError.CorruptedBackup::class.java, failed.error)
        }

    @Test
    fun `quiesce worker-drain timeout aborts before close - generation 1 intact`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            drainWorkersBlocks = true

            val outcome = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotSource))

            assertInstanceOf(ReplacementOutcome.Failed::class.java, outcome)
            assertFalse(genOne.database in closedDatabases, "database must NOT be closed")
            assertTrue(genOne.lifetime.isActive)
            assertSame(genOne, (runtime.phases.value as RuntimePhase.Serving).generation)
        }
}
