// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
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
 * Composed-seam gate: the real [RestoreLatestBackupUseCase] over the real [AppRuntime] transaction
 * and actual temp files. See documentation/feature-specs/kmp-phase-5-startup-processor.md §8.5a.
 */
internal class RestoreTransactionIntegrationTest {

    /** In-memory journal: at most one unresolved attempt, advanced or cleared only by its owner. */
    private class FakeRestoreStateRepository : RestoreStateRepository {
        var attempt: RestoreAttempt? = null
        var preRestoreAvailable: Long? = null

        override suspend fun beginAttempt(attempt: RestoreAttempt): Boolean {
            val owner = this.attempt
            if (owner != null && owner.id != attempt.id) return false
            this.attempt = attempt
            return true
        }

        override suspend fun recordAttemptCommitted(attemptId: String): Boolean {
            val owner = attempt ?: return false
            if (owner.id != attemptId) return false
            attempt = owner.copy(phase = RestoreAttempt.Phase.Committed)
            return true
        }

        override suspend fun resolveAttempt(attemptId: String): Boolean {
            val owner = attempt ?: return false
            if (owner.id != attemptId) return false
            attempt = null
            return true
        }

        override suspend fun getAttempt(): RestoreAttempt? = attempt

        override suspend fun markPreRestoreBackupAvailable(originalDataDateEpochMs: Long) {
            preRestoreAvailable = originalDataDateEpochMs
        }

        override suspend fun clearPreRestoreBackupAvailable() {
            preRestoreAvailable = null
        }

        override fun observePreRestoreBackupAvailable(): Flow<Boolean> =
            MutableStateFlow(preRestoreAvailable != null)

        override suspend fun getPreRestoreOriginalDate(): Long? = preRestoreAvailable
    }

    @TempDir
    lateinit var tempDir: File

    private val context = mockk<Context>(relaxed = true)
    private val provider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val backupStorage = mockk<BackupStorage>(relaxed = true)
    private val restoreState = FakeRestoreStateRepository()

    /** Every rollback snapshot the provider reserved for an attempt, in reservation order. */
    private val reservations = mutableListOf<File>()

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
        coEvery { provider.getPreRestoreBackupFile() } answers {
            File(tempDir, "pre_restore_backup.db").takeIf { it.exists() }
        }
        coEvery { provider.deletePreRestoreBackup() } coAnswers {
            File(tempDir, "pre_restore_backup.db").delete()
        }
        // Per-attempt reservation, promoted onto the undo slot only once the mutation committed.
        coEvery { provider.reserveRollbackSnapshot(any()) } coAnswers {
            val reservation = File(tempDir, "reservation_${firstArg<String>()}.db")
                .apply { writeText(PRE_ATTEMPT_DB) }
            reservations += reservation
            BackupResult.Success(reservation)
        }
        coEvery { provider.promoteRollbackReservation(any()) } coAnswers {
            // Promotion copies; the reservation is deleted after the durable Committed record.
            firstArg<File>().copyTo(File(tempDir, "pre_restore_backup.db"), overwrite = true)
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

    private fun reservationFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("reservation_") }

    private fun callerTempFiles(): List<File> =
        tempDir.listFiles().orEmpty().filter { it.name.startsWith("restore_") && it.name.endsWith(".db") }

    @Test
    fun `real use case restore commits - journal Committed, staged copy consumed and cleaned`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration

            val result = useCase()

            assertInstanceOf(BackupResult.Success::class.java, result)
            val attempt = requireNotNull(restoreState.attempt) {
                "the attempt rode onBeforeMutation into the durable journal"
            }
            assertEquals(RestoreAttempt.Kind.Restore, attempt.kind)
            assertEquals(
                RestoreAttempt.Phase.Committed,
                attempt.phase,
                "only a durably recorded commit may later be read as a success",
            )
            assertEquals(5, attempt.context?.backupSchemaVersion)
            assertEquals(
                reservations.single().absolutePath,
                attempt.rollbackSnapshotPath,
                "the journal names the reservation a crashing launch would need",
            )
            assertEquals(
                PRE_ATTEMPT_DB,
                File(tempDir, "pre_restore_backup.db").readText(),
                "the reservation was promoted onto the undo slot",
            )
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
            val attempt = requireNotNull(restoreState.attempt) {
                "the journal survived the caller"
            }
            assertEquals(
                RestoreAttempt.Phase.Committed,
                attempt.phase,
                "the durable commit record was written on the transaction, not the caller",
            )
            assertTrue(stagedFiles().isEmpty(), "terminal cleanup ran after the commit")
        }

    @Test
    fun `caller killed then lease timeout - transaction-owned compensation with the real effects`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            val lease = runtime.awaitBackupWorkLease() // quiesce will time out on this

            val caller = launch { useCase() }
            runCurrent()
            assertNotNull(restoreState.attempt, "beforeMutation claimed the slot pre-quiesce")
            caller.cancel() // initiator dead before the transaction resolves
            advanceTimeBy(3_000)
            runCurrent()

            // The rejection released the journal slot and discarded only the attempt's reservation.
            assertNull(restoreState.attempt, "onRejectedBeforeMutation resolved the attempt")
            assertTrue(reservationFiles().isEmpty(), "the attempt's reservation was discarded")
            assertFalse(
                File(tempDir, "pre_restore_backup.db").exists(),
                "a rejected attempt never promotes anything onto the undo slot",
            )
            assertTrue(stagedFiles().isEmpty(), "staged copy cleaned on the abort path")
            assertEquals(1, (runtime.phases.value as RuntimePhase.Serving).generation.id)
            lease.release()
        }

    @Test
    fun `swap failure with successful rollback - Failure result, journal resolved, no success lie`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            // An earlier restore's undo slot — what in-process recovery rolls back onto here.
            File(tempDir, "pre_restore_backup.db").writeText("previous-undo-slot")
            val swapError = BackupError.Io(IOException("rename failed"))
            var swaps = 0
            coEvery { provider.replaceLiveDatabaseFile(any()) } coAnswers {
                if (swaps++ == 0) BackupResult.Failure(swapError) else BackupResult.Success(Unit)
            }

            val result = useCase()

            val failure = assertInstanceOf(BackupResult.Failure::class.java, result)
            assertEquals(swapError, failure.error, "restore-FAILURE semantics, never success")
            assertNull(restoreState.attempt, "onRecoveredByRollback resolved the attempt")
            assertNull(restoreState.preRestoreAvailable, "no fake undo availability")
            assertTrue(reservationFiles().isEmpty(), "the failed attempt's reservation is gone")
            assertEquals(
                2,
                (runtime.phases.value as RuntimePhase.Serving).generation.id,
                "a successor serves the PRE-operation data",
            )
        }

    @Test
    fun `post-PONR failure without recovery leaves the attempt unresolved with its reservation`() =
        integrationTest { useCase, runtime ->
            runtime.currentGeneration
            // No undo slot → the ladder cannot roll back → Fatal, and the unresolved `Prepared`
            // attempt is what routes the next launch to recovery.
            coEvery { provider.replaceLiveDatabaseFile(any()) } returns BackupResult.Failure(
                BackupError.Io(IOException("rename failed")),
            )

            val result = useCase()

            assertInstanceOf(BackupResult.Failure::class.java, result)
            val attempt = requireNotNull(restoreState.attempt) {
                "the attempt is PRESERVED — it is what routes the next launch to recovery"
            }
            assertEquals(
                RestoreAttempt.Phase.Prepared,
                attempt.phase,
                "an unprovable mutation must never read as Committed",
            )
            assertNotNull(attempt.context, "the manifest context survives for the recovery UI")
            val reservationPath = requireNotNull(attempt.rollbackSnapshotPath) {
                "the journal names the reservation the recovering launch must roll back onto"
            }
            assertTrue(
                File(reservationPath).exists(),
                "the runtime KEEPS the reservation the journal names: $reservationPath",
            )
        }

    private companion object {
        /** The pre-attempt database stand-in a reserved rollback snapshot carries. */
        const val PRE_ATTEMPT_DB = "pre-attempt-db"
    }
}
