// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.feature.settings.domain.usecase.RestoreLatestBackupUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/**
 * THE COMPOSED SEAM GATE (round-2 required test): the REAL [RestoreLatestBackupUseCase] driving
 * the REAL [AppRuntime] transaction over ACTUAL temp files — no mocked seam in between. Proves
 * end-to-end: source-ownership transfer at submission (the caller's `finally { tempFile
 * .delete() }` — which genuinely runs on cancellation — can never destroy the staged copy),
 * the marker riding `onBeforeMutation` inside the transaction, transaction-owned compensation
 * when the initiator is dead, and the truthful RecoveredByRollback → restore-FAILURE mapping.
 */
internal class RestoreTransactionIntegrationTest {

    /** Stateful in-memory repository — observes what the transaction's effects actually did. */
    private class FakeRestoreStateRepository : RestoreStateRepository {
        var context: RestoreInProgressContext? = null
        var preRestoreAvailable: Long? = null
        var mutationInterrupted = false

        override suspend fun markRestoreInProgress(context: RestoreInProgressContext) {
            this.context = context
        }

        override suspend fun getRestoreInProgressContext(): RestoreInProgressContext? = context

        override suspend fun clearRestoreInProgress() {
            context = null
            mutationInterrupted = false
        }

        override suspend fun markPreRestoreBackupAvailable(originalDataDateEpochMs: Long) {
            preRestoreAvailable = originalDataDateEpochMs
        }

        override suspend fun clearPreRestoreBackupAvailable() {
            preRestoreAvailable = null
        }

        override fun observePreRestoreBackupAvailable(): Flow<Boolean> =
            MutableStateFlow(preRestoreAvailable != null)

        override suspend fun getPreRestoreOriginalDate(): Long? = preRestoreAvailable

        override suspend fun markRestoreMutationInterrupted() {
            mutationInterrupted = true
        }

        override suspend fun isRestoreMutationInterrupted(): Boolean = mutationInterrupted
    }

    @TempDir
    lateinit var tempDir: File

    private val context = mockk<Context>(relaxed = true)
    private val provider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val backupStorage = mockk<BackupStorage>(relaxed = true)
    private val restoreState = FakeRestoreStateRepository()

    private var preflightGate: CompletableDeferred<Unit>? = null

    private fun backupRef() = BackupRef(
        remoteId = "backup-1",
        manifest = BackupManifest(
            appVersion = "1.0.0",
            dbSchemaVersion = 5,
            createdAtEpochMs = 1_700_000_000_000L,
            dbFileSizeBytes = 42L,
            deviceModel = "test",
        ),
    )

    private fun integrationTest(
        body: suspend TestScope.(RestoreLatestBackupUseCase, AppRuntime) -> Unit,
    ) = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = AppRuntime(
            applicationContext = context,
            dbFactory = { mockk<AppDatabase>(relaxed = true) },
            imageStorageFactory = { mockk<ImageStorage>(relaxed = true) },
            graphFactory = { _, _, _, _, _ ->
                mockk<AppGraph>(relaxed = true) {
                    every { databaseSnapshotProvider } returns provider
                }
            },
            preflight = {
                preflightGate?.await()
                StartupOutcome.Proceed
            },
            closeDatabase = {},
            replacementPolicy = ReplacementPolicy.RebuildInProcess,
            policy = RuntimeTransitionPolicy(
                mainDispatcher = dispatcher,
                hostDispatcher = dispatcher,
                stagingDirectory = { tempDir },
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        // Shared provider stubs — the SAME provider serves the use case and the runtime graph.
        coEvery { provider.currentSchemaVersion() } returns 5
        coEvery { provider.preserveCurrentDb() } coAnswers {
            BackupResult.Success(
                File(tempDir, "pre_restore_backup.db").apply { writeText("preserved") },
            )
        }
        coEvery { provider.getPreRestoreBackupFile() } answers {
            File(tempDir, "pre_restore_backup.db").takeIf { it.exists() }
        }
        coEvery { provider.deletePreRestoreBackup() } coAnswers {
            File(tempDir, "pre_restore_backup.db").delete()
            BackupResult.Success(Unit)
        }
        coEvery { provider.validateSnapshotForRestore(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.replaceLiveDatabaseFile(any()) } returns BackupResult.Success(Unit)
        // Storage: one backup; the download writes REAL bytes into the caller's temp file.
        coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(backupRef()))
        coEvery { backupStorage.downloadBackup(any(), any()) } coAnswers {
            secondArg<File>().writeText("downloaded-snapshot")
            BackupResult.Success(backupRef().manifest)
        }
        val tempFileProvider = mockk<TempFileProvider> {
            every { createTempFile(any(), any()) } answers {
                File.createTempFile(firstArg(), secondArg(), tempDir)
            }
        }
        val useCase = RestoreLatestBackupUseCase(
            backupStorage = backupStorage,
            snapshotProvider = provider,
            databaseReplacement = runtime, // the REAL transaction seam
            restoreStateRepository = restoreState,
            tempFileProvider = tempFileProvider,
            dispatcher = dispatcher,
        )
        body(useCase, runtime)
    }

    private fun stagedFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("staged_restore_") }

    private fun callerTempFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("restore_") && it.name.endsWith(".db") }

    @Test
    fun `real use case restore commits - marker inside the txn, staged copy consumed and cleaned`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration

            val result = useCase()

            assertInstanceOf(BackupResult.Success::class.java, result)
            assertNotNull(restoreState.context, "the marker rode onBeforeMutation into DataStore")
            assertEquals(5, restoreState.context?.backupSchemaVersion)
            assertFalse(restoreState.mutationInterrupted)
            assertTrue(stagedFiles().isEmpty(), "the runtime cleaned its staged copy")
            assertTrue(callerTempFiles().isEmpty(), "the caller's temp path was consumed/cleaned")
            val swapped = slot<File>()
            coVerify { provider.replaceLiveDatabaseFile(capture(swapped)) }
            assertTrue(swapped.captured.name.startsWith("staged_restore_"))
            assertEquals(
                2,
                (runtime.phases.value as RuntimePhase.Serving).generation.id,
                "the real transaction published the successor generation",
            )
        }

    @Test
    fun `caller cancelled mid-transaction - its ACTUAL finally-delete cannot strand the commit`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val caller = launch { useCase() }
            runCurrent() // downloaded, submitted, staged; the transaction parked at preflight
            caller.cancel() // the REAL use case's finally { tempFile.delete() } runs NOW
            runCurrent()
            assertTrue(callerTempFiles().isEmpty(), "the caller cleaned its own (moved) path")
            assertEquals(1, stagedFiles().size, "the runtime's staged copy SURVIVED the caller")
            assertTrue(stagedFiles().single().readText().isNotEmpty())

            gate.complete(Unit)
            runCurrent()

            assertEquals(2, (runtime.phases.value as RuntimePhase.Serving).generation.id)
            assertNotNull(restoreState.context, "the marker survived — the restore committed")
            assertTrue(stagedFiles().isEmpty(), "terminal cleanup ran after the commit")
        }

    @Test
    fun `caller killed then lease timeout - transaction-owned compensation with the real effects`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            val lease = runtime.awaitBackupWorkLease() // quiesce will time out on this

            val caller = launch { useCase() }
            runCurrent()
            assertNotNull(restoreState.context, "beforeMutation wrote the marker pre-quiesce")
            caller.cancel() // initiator dead before the transaction resolves
            advanceTimeBy(3_000)
            runCurrent()

            // The REAL RestoreTransactionEffects ran on the transaction: pre-mutation rejection
            // compensated the marker and the preserved snapshot; the staged copy was deleted.
            assertNull(restoreState.context, "onRejectedBeforeMutation cleared the marker")
            assertFalse(File(tempDir, "pre_restore_backup.db").exists(), "preserved slot cleaned")
            assertTrue(stagedFiles().isEmpty(), "staged copy cleaned on the abort path")
            assertEquals(1, (runtime.phases.value as RuntimePhase.Serving).generation.id)
            lease.release()
        }

    @Test
    fun `swap failure with successful rollback - Failure result, marker compensated, no success lie`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            val swapError = BackupError.Io(IOException("rename failed"))
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                if (swaps++ == 0) BackupResult.Failure(swapError) else BackupResult.Success(Unit)
            }

            val result = useCase()

            val failure = assertInstanceOf(BackupResult.Failure::class.java, result)
            assertEquals(swapError, failure.error, "restore-FAILURE semantics, never success")
            assertNull(restoreState.context, "onRecoveredByRollback compensated the marker")
            assertNull(restoreState.preRestoreAvailable, "no fake undo availability")
            assertFalse(restoreState.mutationInterrupted, "recovery completed — no journal flag")
            assertEquals(
                2,
                (runtime.phases.value as RuntimePhase.Serving).generation.id,
                "a successor serves the PRE-operation data",
            )
        }

    @Test
    fun `post-PONR failure without recovery journals the interrupted mutation for the next launch`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            // Swap fails AND the preserved file is gone → the ladder cannot roll back → Fatal;
            // the use case's onFatal effects journal the interrupted mutation durably.
            coEvery { provider.replaceLiveDatabaseFile(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("rename failed")),
            )
            coEvery { provider.preserveCurrentDb() } returns BackupResult.Success(
                File(tempDir, "pre_restore_backup.db"), // reported, but never materialized
            )

            val result = useCase()

            assertInstanceOf(BackupResult.Failure::class.java, result)
            assertTrue(
                restoreState.mutationInterrupted,
                "the journal entry routes the next launch to the failure path",
            )
            assertNotNull(restoreState.context, "the marker is PRESERVED for the next launch")
        }
}
