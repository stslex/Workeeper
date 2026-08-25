// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndo
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndoTransition
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreGarbageCollectionReport
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
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

/** Real use-case effects composed with the runtime's exact-owner replacement transaction. */
internal class RestoreTransactionIntegrationTest {

    private class FakeRestoreStateRepository : RestoreStateRepository {
        private val epoch = InstallEpoch(
            RestoreOwnerId("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"),
        )
        private val activeFlow = MutableStateFlow<ActiveUndo?>(null)

        var attempt: RestoreAttempt? = null
            private set
        var activeUndo: ActiveUndo?
            get() = activeFlow.value
            private set(value) {
                activeFlow.value = value
            }
        var terminal: RestoreTerminal? = null
            private set

        override suspend fun readProtocol(): RestoreProtocolRead = RestoreProtocolRead.Current(
            RestoreProtocolState(epoch, attempt, activeUndo, terminal),
        )

        override suspend fun installLegacyState(
            epoch: InstallEpoch,
            attempt: RestoreAttempt?,
            activeUndo: ActiveUndo?,
        ): Boolean = false

        override suspend fun beginAttempt(attempt: RestoreAttempt): Boolean {
            val current = this.attempt
            if (current != null && current != attempt) return false
            this.attempt = attempt
            return true
        }

        override suspend fun recordAttemptCommitted(attemptId: RestoreOwnerId): Boolean {
            val current = attempt ?: return false
            if (current.id != attemptId) return false
            attempt = when (current) {
                is RestoreAttempt.Restore -> current.copy(phase = RestoreAttempt.Phase.Committed)
                is RestoreAttempt.Rollback -> current.copy(phase = RestoreAttempt.Phase.Committed)
            }
            return true
        }

        override suspend fun beginCompensation(
            restoreAttemptId: RestoreOwnerId,
            rollback: RestoreAttempt.Rollback,
        ): Boolean {
            val restore = attempt as? RestoreAttempt.Restore ?: return false
            if (restore.id != restoreAttemptId || restore.undoRef != rollback.sourceRef) return false
            attempt = rollback
            return true
        }

        override suspend fun discardPreparedAttempt(attemptId: RestoreOwnerId): Boolean {
            val current = attempt ?: return false
            if (current.id != attemptId || current.phase != RestoreAttempt.Phase.Prepared) return false
            attempt = null
            return true
        }

        override suspend fun finalizeAttempt(
            attemptId: RestoreOwnerId,
            activeUndoTransition: ActiveUndoTransition,
            terminal: RestoreTerminal,
        ): Boolean {
            val current = attempt ?: return false
            if (current.id != attemptId || current.phase != RestoreAttempt.Phase.Committed) return false
            when (activeUndoTransition) {
                is ActiveUndoTransition.Replace -> activeUndo = activeUndoTransition.activeUndo
                is ActiveUndoTransition.ClearIf -> {
                    if (activeUndo?.ref == activeUndoTransition.appliedRef) activeUndo = null
                }
            }
            this.terminal = terminal
            attempt = null
            return true
        }

        override suspend fun acknowledgeTerminal(owner: RestoreOwnerId): Boolean {
            if (terminal?.owner != owner) return false
            terminal = null
            return true
        }

        override suspend fun abandonInterruptedAttempt(attemptId: RestoreOwnerId): Boolean {
            val restore = attempt as? RestoreAttempt.Restore ?: return false
            if (restore.id != attemptId) return false
            attempt = null
            activeUndo = null
            terminal = null
            return true
        }

        override fun observeActiveUndo(): Flow<ActiveUndo?> = activeFlow

        fun seedActiveUndo(activeUndo: ActiveUndo) {
            this.activeUndo = activeUndo
        }
    }

    @TempDir
    lateinit var tempDir: File

    private val context = mockk<Context>(relaxed = true)
    private val provider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val backupStorage = mockk<BackupStorage>(relaxed = true)
    private val restoreState = FakeRestoreStateRepository()
    private val restoreSources = mutableMapOf<RestoreSourceRef, File>()
    private val undoFiles = mutableMapOf<UndoRef, File>()
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
                    every { restoreStateRepository } returns restoreState
                }
            },
            preflight = {
                preflightGate?.await()
                finalizeVerifiedAttempt()
                StartupOutcome.Proceed
            },
            closeDatabase = {},
            replacementPolicy = ReplacementPolicy.RebuildInProcess,
            policy = RuntimeTransitionPolicy(
                mainDispatcher = dispatcher,
                hostDispatcher = dispatcher,
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        coEvery { provider.currentSchemaVersion() } returns 5
        every { provider.hasMigrationPath(any(), any()) } returns true
        coEvery { provider.stageRestoreSource(any(), any()) } coAnswers {
            val source = firstArg<File>()
            val ref = secondArg<RestoreSourceRef>()
            val staged = File(tempDir, "staged_restore_${ref.owner}.db")
            source.copyTo(staged, overwrite = false)
            source.delete()
            restoreSources[ref] = staged
            BackupResult.Success(staged)
        }
        every { provider.getRestoreSourceFile(any()) } answers {
            restoreSources[firstArg<RestoreSourceRef>()]?.takeIf(File::exists)
        }
        coEvery { provider.validateRestoreSource(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.checkRestoreCapacity(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.createUndo(any()) } coAnswers {
            val ref = firstArg<UndoRef>()
            val undo = File(tempDir, "undo_${ref.owner}.db").apply { writeText(PRE_ATTEMPT_DB) }
            undoFiles[ref] = undo
            BackupResult.Success(undo)
        }
        every { provider.getUndoFile(any()) } answers {
            undoFiles[firstArg<UndoRef>()]?.takeIf(File::exists)
        }
        coEvery { provider.validateUndo(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.checkRollbackCapacity(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.replaceLiveDatabaseFromRestore(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.replaceLiveDatabaseFromUndo(any()) } returns BackupResult.Success(Unit)
        coEvery { provider.deleteRestoreSource(any()) } coAnswers {
            restoreSources.remove(firstArg<RestoreSourceRef>())?.delete() ?: false
        }
        coEvery { provider.deleteUndo(any()) } coAnswers {
            undoFiles.remove(firstArg<UndoRef>())?.delete() ?: false
        }
        coEvery { provider.sweepRecoveryFiles(any()) } coAnswers {
            val state = firstArg<RestoreProtocolState>()
            val protectedUndo = buildSet {
                state.activeUndo?.ref?.let(::add)
                when (val attempt = state.attempt) {
                    is RestoreAttempt.Restore -> attempt.undoRef?.let(::add)
                    is RestoreAttempt.Rollback -> add(attempt.sourceRef)
                    null -> Unit
                }
            }
            val protectedSources = buildSet {
                (state.attempt as? RestoreAttempt.Restore)?.sourceRef?.let(::add)
            }
            undoFiles.keys.filter { it !in protectedUndo }.forEach { ref ->
                undoFiles.remove(ref)?.delete()
            }
            restoreSources.keys.filter { it !in protectedSources }.forEach { ref ->
                restoreSources.remove(ref)?.delete()
            }
            RestoreGarbageCollectionReport(emptyList(), emptyList())
        }
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
            databaseReplacement = runtime,
            restoreStateRepository = restoreState,
            tempFileProvider = tempFileProvider,
            dispatcher = dispatcher,
        )
        body(useCase, runtime)
    }

    private suspend fun finalizeVerifiedAttempt() {
        val attempt = restoreState.attempt ?: return
        if (attempt.phase != RestoreAttempt.Phase.Committed) return
        val terminal = when (attempt) {
            is RestoreAttempt.Restore -> {
                val active = attempt.undoRef?.let { ActiveUndo(it, ORIGINAL_DATE) }
                val value = RestoreTerminal.RestoreSucceeded(
                    owner = attempt.id,
                    restoredAtEpochMs = RESTORED_AT,
                    previousVersionAvailable = active != null,
                )
                assertTrue(
                    restoreState.finalizeAttempt(
                        attempt.id,
                        ActiveUndoTransition.Replace(active),
                        value,
                    ),
                )
                value
            }

            is RestoreAttempt.Rollback -> {
                val value = RestoreTerminal.RestoreFailed(attempt.id, BackupErrorCode.Io)
                assertTrue(
                    restoreState.finalizeAttempt(
                        attempt.id,
                        ActiveUndoTransition.ClearIf(attempt.sourceRef),
                        value,
                    ),
                )
                value
            }
        }
        assertTrue(restoreState.acknowledgeTerminal(terminal.owner))
    }

    private fun stagedFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("staged_restore_") }

    private fun callerTempFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter {
            it.name.startsWith("restore_") && it.name.endsWith(".db")
        }

    @Test
    fun `real use case restore finalizes exact active undo before publishing generation`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            val applied = slot<RestoreSourceRef>()

            val result = useCase()

            assertInstanceOf(BackupResult.Success::class.java, result)
            assertNull(restoreState.attempt)
            val active = requireNotNull(restoreState.activeUndo)
            coVerify { provider.replaceLiveDatabaseFromRestore(capture(applied)) }
            assertEquals(applied.captured.owner, active.ref.owner)
            assertEquals(PRE_ATTEMPT_DB, undoFiles.getValue(active.ref).readText())
            assertTrue(stagedFiles().isEmpty(), "finalized staged source is swept")
            assertTrue(callerTempFiles().isEmpty(), "caller temp ownership was transferred")
            assertEquals(2, (runtime.phases.value as RuntimePhase.Serving).generation.id)
        }

    @Test
    fun `caller cancellation cannot strand runtime-owned source or finalization`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val caller = launch { useCase() }
            runCurrent()
            caller.cancel()
            runCurrent()
            assertTrue(callerTempFiles().isEmpty())
            assertEquals(1, stagedFiles().size)

            gate.complete(Unit)
            runCurrent()

            assertEquals(2, (runtime.phases.value as RuntimePhase.Serving).generation.id)
            assertNull(restoreState.attempt)
            assertNotNull(restoreState.activeUndo)
            assertTrue(stagedFiles().isEmpty())
        }

    @Test
    fun `lease timeout rejects before PONR and sweep removes only unowned N assets`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            val lease = checkNotNull(runtime.awaitBackupWorkLease())

            val caller = launch { useCase() }
            runCurrent()
            assertNull(restoreState.attempt)
            assertEquals(1, undoFiles.size)
            assertEquals(1, stagedFiles().size)
            assertEquals(undoFiles.keys.single().owner, restoreSources.keys.single().owner)
            assertTrue(callerTempFiles().isEmpty())
            caller.cancel()
            advanceTimeBy(3_000)
            runCurrent()

            assertNull(restoreState.attempt)
            assertNull(restoreState.activeUndo)
            assertTrue(undoFiles.isEmpty())
            assertTrue(stagedFiles().isEmpty())
            coVerify(exactly = 0) { provider.replaceLiveDatabaseFromRestore(any()) }
            assertEquals(1, (runtime.phases.value as RuntimePhase.Serving).generation.id)
            lease.release()
        }

    @Test
    fun `restore swap failure compensates from N and preserves unrelated active P`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            val previousRef = UndoRef(PREVIOUS_OWNER)
            val previousFile = File(tempDir, "undo_${previousRef.owner}.db")
                .apply { writeText("previous-undo") }
            undoFiles[previousRef] = previousFile
            restoreState.seedActiveUndo(ActiveUndo(previousRef, ORIGINAL_DATE))
            val swapError = BackupError.Io(IOException("rename failed"))
            val compensatedRef = slot<UndoRef>()
            coEvery { provider.replaceLiveDatabaseFromRestore(any()) } returns
                BackupResult.Failure(swapError)
            coEvery { provider.replaceLiveDatabaseFromUndo(capture(compensatedRef)) } returns
                BackupResult.Success(Unit)

            val result = useCase()

            val failure = assertInstanceOf(BackupResult.Failure::class.java, result)
            assertEquals(swapError, failure.error)
            assertNull(restoreState.attempt)
            assertEquals(previousRef, restoreState.activeUndo?.ref)
            assertTrue(previousFile.exists())
            assertTrue(compensatedRef.captured != previousRef)
            assertFalse(undoFiles.containsKey(compensatedRef.captured), "resolved N is swept")
            assertEquals(2, (runtime.phases.value as RuntimePhase.Serving).generation.id)
        }

    @Test
    fun `unrecoverable post-PONR failure keeps exact Prepared refs for next launch`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            coEvery { provider.replaceLiveDatabaseFromRestore(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("rename failed")),
            )
            coEvery { provider.validateUndo(any()) } returns BackupResult.Failure(
                BackupError.CorruptedBackup("owned N is unreadable"),
            )

            val result = useCase()

            assertInstanceOf(BackupResult.Failure::class.java, result)
            val attempt = assertInstanceOf(
                RestoreAttempt.Restore::class.java,
                restoreState.attempt,
            )
            assertEquals(RestoreAttempt.Phase.Prepared, attempt.phase)
            assertTrue(undoFiles.getValue(requireNotNull(attempt.undoRef)).exists())
            assertTrue(restoreSources.getValue(requireNotNull(attempt.sourceRef)).exists())
        }

    private companion object {
        const val PRE_ATTEMPT_DB = "pre-attempt-db"
        const val ORIGINAL_DATE = 1_700_000_000_000L
        const val RESTORED_AT = 1_710_000_000_000L
        val PREVIOUS_OWNER = RestoreOwnerId(
            "ffffffff-ffff-4fff-8fff-ffffffffffff",
        )
    }
}
