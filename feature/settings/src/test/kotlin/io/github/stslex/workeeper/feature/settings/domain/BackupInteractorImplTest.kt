// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import android.content.Intent
import android.content.IntentSender
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolution
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolutionOutcome
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import io.github.stslex.workeeper.feature.settings.domain.usecase.RestoreLatestBackupUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

internal class BackupInteractorImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val backupAuth = mockk<BackupAuth>(relaxed = true)
    private val backupStorage = mockk<BackupStorage>(relaxed = true)
    private val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val databaseReplacement = mockk<DatabaseReplacement>(relaxed = true)
    private val restoreStateRepository = mockk<RestoreStateRepository>(relaxed = true)
    private val snapshotExportRunner = mockk<SnapshotExportRunner>(relaxed = true)
    private val tempFileProvider = mockk<TempFileProvider>(relaxed = true)
    private val platformInfo = mockk<PlatformInfoProvider>(relaxed = true)

    @TempDir
    lateinit var cacheDir: File

    private lateinit var interactor: BackupInteractorImpl

    /**
     * GUARD: [Log.isLogging] must be off here — kermit's Logcat sink throws
     * `UnsatisfiedLinkError` on the JVM. Same idiom as `SnackbarManagerTest`.
     */
    private var wasLogging = true

    @BeforeEach
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
        Dispatchers.setMain(testDispatcher)
        every { tempFileProvider.createTempFile(any(), any()) } answers {
            File.createTempFile("test", ".db", cacheDir)
        }
        every { platformInfo.appVersionName() } returns "1.2.3"
        every { platformInfo.deviceModel() } returns "Pixel"
        every { backupAuth.state } returns MutableStateFlow(AuthState.SignedOut)
        interactor = BackupInteractorImpl(
            backupAuth = backupAuth,
            backupStorage = backupStorage,
            snapshotProvider = snapshotProvider,
            restoreLatestBackup = RestoreLatestBackupUseCase(
                backupStorage = backupStorage,
                snapshotProvider = snapshotProvider,
                databaseReplacement = databaseReplacement,
                restoreStateRepository = restoreStateRepository,
                tempFileProvider = tempFileProvider,
                dispatcher = testDispatcher,
            ),
            snapshotExportRunner = snapshotExportRunner,
            platformInfo = platformInfo,
            tempFileProvider = tempFileProvider,
            dispatcher = testDispatcher,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        Log.isLogging = wasLogging
    }

    @Test
    fun `createBackup returns the binary result even when the snapshot runner throws`() =
        runTest(testDispatcher) {
            val captured = slot<File>()
            coEvery { snapshotProvider.captureSnapshot(capture(captured)) } answers {
                captured.captured.writeText("data")
                BackupResult.Success(Unit)
            }
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            coEvery { backupStorage.uploadBackup(any(), any()) } returns BackupResult.Success(makeRef())
            every { snapshotExportRunner.runIfEligible() } throws RuntimeException("runner blew up")

            val result = interactor.createBackup()

            assertTrue(result is BackupResult.Success, "binary result must be unaffected, got $result")
        }

    @Test
    fun `requestDriveFileAccess maps NeedsResolution from backupAuth`() = runTest(testDispatcher) {
        val sender = mockk<IntentSender>(relaxed = true)
        coEvery { backupAuth.requestDriveFileAccess() } returns
            SignInResult.NeedsResolution(AuthResolution(sender))

        val outcome = interactor.requestDriveFileAccess()

        assertTrue(outcome is SignInOutcomeDomain.NeedsResolution)
        assertSame(sender, (outcome as SignInOutcomeDomain.NeedsResolution).resolution.platform)
    }

    @Test
    fun `isDriveFileGranted reflects the backupAuth grant flow`() = runTest(testDispatcher) {
        every { backupAuth.observeDriveFileGranted() } returns flowOf(true)

        assertTrue(interactor.isDriveFileGranted())
    }

    @Test
    fun `signIn Success returns SignInOutcomeDomain Success`() = runTest(testDispatcher) {
        coEvery { backupAuth.signIn() } returns SignInResult.Success(
            Account(email = "a@example.com", displayName = null),
        )
        assertEquals(SignInOutcomeDomain.Success, interactor.signIn())
    }

    @Test
    fun `signIn NeedsResolution propagates same intentSender`() = runTest(testDispatcher) {
        val sender = makeIntentSender()
        coEvery { backupAuth.signIn() } returns SignInResult.NeedsResolution(AuthResolution(sender))
        val outcome = interactor.signIn()
        assertTrue(outcome is SignInOutcomeDomain.NeedsResolution)
        assertSame(sender, (outcome as SignInOutcomeDomain.NeedsResolution).resolution.platform)
    }

    @Test
    fun `signIn Failure propagates same BackupError`() = runTest(testDispatcher) {
        val error = BackupError.NetworkUnavailable
        coEvery { backupAuth.signIn() } returns SignInResult.Failure(error)
        val outcome = interactor.signIn()
        assertTrue(outcome is SignInOutcomeDomain.Failure)
        assertSame(error, (outcome as SignInOutcomeDomain.Failure).error)
    }

    @Test
    fun `createBackup happy path captures snapshot uploads and cleans temp file`() =
        runTest(testDispatcher) {
            val captured = slot<File>()
            coEvery { snapshotProvider.captureSnapshot(capture(captured)) } answers {
                captured.captured.writeText("dummy-db-content-of-15-chars")
                BackupResult.Success(Unit)
            }
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            val manifestSlot = slot<BackupManifest>()
            coEvery {
                backupStorage.uploadBackup(any(), capture(manifestSlot))
            } returns BackupResult.Success(makeRef())

            val result = interactor.createBackup()

            assertTrue(result is BackupResult.Success)
            assertEquals("1.2.3", manifestSlot.captured.appVersion)
            assertEquals(5, manifestSlot.captured.dbSchemaVersion)
            assertTrue(manifestSlot.captured.createdAtEpochMs > 0)
            assertEquals(
                "dummy-db-content-of-15-chars".toByteArray().size.toLong(),
                manifestSlot.captured.dbFileSizeBytes,
            )
            assertFalse(captured.captured.exists(), "temp file should be deleted")
        }

    @Test
    fun `createBackup upload failure still cleans temp file`() = runTest(testDispatcher) {
        val captured = slot<File>()
        coEvery { snapshotProvider.captureSnapshot(capture(captured)) } answers {
            captured.captured.writeText("data")
            BackupResult.Success(Unit)
        }
        coEvery { snapshotProvider.currentSchemaVersion() } returns 5
        val ioError = BackupError.Io(IOException("upload failed"))
        coEvery { backupStorage.uploadBackup(any(), any()) } returns BackupResult.Failure(ioError)

        val result = interactor.createBackup()

        assertTrue(result is BackupResult.Failure)
        assertSame(ioError, (result as BackupResult.Failure).error)
        assertFalse(captured.captured.exists(), "temp file should be deleted on failure")
    }

    @Test
    fun `createBackup captureSnapshot failure short-circuits and cleans temp file`() =
        runTest(testDispatcher) {
            val captured = slot<File>()
            val ioError = BackupError.Io(IOException("capture failed"))
            coEvery {
                snapshotProvider.captureSnapshot(capture(captured))
            } returns BackupResult.Failure(ioError)

            val result = interactor.createBackup()

            assertTrue(result is BackupResult.Failure)
            assertSame(ioError, (result as BackupResult.Failure).error)
            coVerify(exactly = 0) { backupStorage.uploadBackup(any(), any()) }
            assertFalse(captured.captured.exists(), "temp file should be deleted")
        }

    @Test
    fun `listLatestBackup returns first ref mapped to summary`() = runTest(testDispatcher) {
        val newer = makeRef(createdAt = 200L, schema = 5, size = 1024L, appVersion = "1.2.3")
        val older = makeRef(createdAt = 100L, schema = 5, size = 512L, appVersion = "1.2.0")
        coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(newer, older))

        val result = interactor.listLatestBackup()

        assertTrue(result is BackupResult.Success)
        assertEquals(
            BackupSummaryDomain(
                createdAtEpochMs = 200L,
                sizeBytes = 1024L,
                appVersion = "1.2.3",
                schemaVersion = 5,
            ),
            (result as BackupResult.Success).data,
        )
    }

    @Test
    fun `listLatestBackup returns Success null when no backups`() = runTest(testDispatcher) {
        coEvery { backupStorage.listBackups() } returns BackupResult.Success(emptyList())
        val result = interactor.listLatestBackup()
        assertTrue(result is BackupResult.Success)
        assertNull((result as BackupResult.Success).data)
    }

    @Test
    fun `listLatestBackup propagates Failure`() = runTest(testDispatcher) {
        val error = BackupError.NetworkUnavailable
        coEvery { backupStorage.listBackups() } returns BackupResult.Failure(error)
        val result = interactor.listLatestBackup()
        assertTrue(result is BackupResult.Failure)
        assertSame(error, (result as BackupResult.Failure).error)
    }

    @Test
    fun `listBackups maps every ref to summary preserving order`() = runTest(testDispatcher) {
        val newer = makeRef(createdAt = 200L, schema = 5, size = 1024L, appVersion = "1.2.3")
        val older = makeRef(createdAt = 100L, schema = 5, size = 512L, appVersion = "1.2.0")
        coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(newer, older))

        val result = interactor.listBackups()

        assertTrue(result is BackupResult.Success)
        val summaries = (result as BackupResult.Success).data
        assertEquals(2, summaries.size)
        assertEquals(200L, summaries[0].createdAtEpochMs)
        assertEquals(100L, summaries[1].createdAtEpochMs)
    }

    @Test
    fun `listBackups returns empty list when no backups`() = runTest(testDispatcher) {
        coEvery { backupStorage.listBackups() } returns BackupResult.Success(emptyList())
        val result = interactor.listBackups()
        assertTrue(result is BackupResult.Success)
        assertTrue((result as BackupResult.Success).data.isEmpty())
    }

    @Test
    fun `listBackups propagates Failure`() = runTest(testDispatcher) {
        val error = BackupError.NetworkUnavailable
        coEvery { backupStorage.listBackups() } returns BackupResult.Failure(error)
        val result = interactor.listBackups()
        assertTrue(result is BackupResult.Failure)
        assertSame(error, (result as BackupResult.Failure).error)
    }

    @Test
    fun `restoreLatest with no backups returns CorruptedBackup and skips download`() =
        runTest(testDispatcher) {
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(emptyList())

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            val error = (result as BackupResult.Failure).error
            assertTrue(error is BackupError.CorruptedBackup)
            coVerify(exactly = 0) { backupStorage.downloadBackup(any(), any()) }
            coVerify(exactly = 0) { databaseReplacement.restoreFromSnapshot(any(), any()) }
        }

    @Test
    fun `restoreLatest with backup schema newer than current returns BackupTooNew`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 9)
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            val error = (result as BackupResult.Failure).error
            assertTrue(error is BackupError.BackupTooNew)
            assertEquals(9, (error as BackupError.BackupTooNew).backupSchemaVersion)
            assertEquals(5, error.appSchemaVersion)
            coVerify(exactly = 0) { backupStorage.downloadBackup(any(), any()) }
            coVerify(exactly = 0) { databaseReplacement.restoreFromSnapshot(any(), any()) }
        }

    @Test
    fun `restoreLatest happy path claims the attempt then records the commit and cleans temp file`() =
        runTest(testDispatcher) {
            val ref = makeRef(
                schema = 4,
                createdAt = 1_700_000_000_000L,
                appVersion = "1.0.0",
            )
            stubRestorableBackup(ref)
            val downloadCaptured = slot<File>()
            coEvery {
                backupStorage.downloadBackup(any(), capture(downloadCaptured))
            } answers {
                downloadCaptured.captured.writeText("payload")
                BackupResult.Success(ref.manifest)
            }
            val attemptSlot = slot<RestoreAttempt>()
            coEvery { restoreStateRepository.beginAttempt(capture(attemptSlot)) } returns true
            coEvery { restoreStateRepository.recordAttemptCommitted(any()) } returns true
            // Real transaction shape: the runtime reserves the snapshot, hands its path to
            // onBeforeMutation, then reports the durable commit through onMutationCommitted.
            coEvery { databaseReplacement.restoreFromSnapshot(any(), any()) } coAnswers {
                val effects = secondArg<DatabaseReplacementEffects>()
                effects.onBeforeMutation("/tmp/res.db")
                effects.onMutationCommitted()
                DatabaseReplacementResult.Committed()
            }

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Success)
            coVerifyOrder {
                backupStorage.downloadBackup(ref, any())
                databaseReplacement.restoreFromSnapshot(any(), any())
            }
            val attempt = attemptSlot.captured
            assertEquals(RestoreAttempt.Kind.Restore, attempt.kind)
            assertEquals(RestoreAttempt.Phase.Prepared, attempt.phase)
            assertEquals("/tmp/res.db", attempt.rollbackSnapshotPath)
            val context = requireNotNull(attempt.context) { "a Restore attempt carries context" }
            assertEquals(4, context.backupSchemaVersion)
            assertEquals(1_700_000_000_000L, context.backupCreatedAtEpochMs)
            assertEquals("1.0.0", context.backupAppVersion)
            assertTrue(context.startedAtEpochMs > 0)
            // The same attempt id that claimed the slot is the one that advances it.
            coVerify(exactly = 1) { restoreStateRepository.recordAttemptCommitted(attempt.id) }
            // On success the post-restart pre-flight owns the Committed attempt.
            coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
            assertFalse(downloadCaptured.captured.exists(), "temp file should be deleted")
        }

    @Test
    fun `restoreLatest never reserves the rollback snapshot itself`() = runTest(testDispatcher) {
        // The transaction reserves it; a second snapshot here would race.
        val ref = makeRef(schema = 4)
        stubRestorableBackup(ref)
        coEvery { backupStorage.downloadBackup(any(), any()) } returns BackupResult.Success(
            ref.manifest,
        )
        coEvery { restoreStateRepository.beginAttempt(any()) } returns true
        coEvery { restoreStateRepository.recordAttemptCommitted(any()) } returns true
        coEvery { databaseReplacement.restoreFromSnapshot(any(), any()) } coAnswers {
            val effects = secondArg<DatabaseReplacementEffects>()
            effects.onBeforeMutation("/tmp/res.db")
            effects.onMutationCommitted()
            DatabaseReplacementResult.Committed()
        }

        val result = interactor.restoreLatest()

        assertTrue(result is BackupResult.Success)
        coVerify(exactly = 0) { snapshotProvider.reserveRollbackSnapshot(any()) }
    }

    @Test
    fun `a second restore is refused while an attempt is unresolved`() = runTest(testDispatcher) {
        // A false from beginAttempt is fatal to this attempt: the effect throws and the runtime
        // turns that into RejectedBeforeMutation, recording nothing against the other attempt.
        val ref = makeRef(schema = 4)
        stubRestorableBackup(ref)
        coEvery { backupStorage.downloadBackup(any(), any()) } returns BackupResult.Success(
            ref.manifest,
        )
        coEvery { restoreStateRepository.beginAttempt(any()) } returns false
        val rejection = BackupError.Io(IOException("pre-mutation persistence failed"))
        var preparation: Result<Unit>? = null
        coEvery { databaseReplacement.restoreFromSnapshot(any(), any()) } coAnswers {
            preparation = runCatching {
                secondArg<DatabaseReplacementEffects>().onBeforeMutation("")
            }
            DatabaseReplacementResult.RejectedBeforeMutation(rejection)
        }

        val result = interactor.restoreLatest()

        assertTrue(
            preparation?.isFailure == true,
            "a refused claim must THROW out of onBeforeMutation, got $preparation",
        )
        assertTrue(result is BackupResult.Failure)
        assertSame(rejection, (result as BackupResult.Failure).error)
        coVerify(exactly = 1) { restoreStateRepository.beginAttempt(any()) }
        coVerify(exactly = 0) { restoreStateRepository.recordAttemptCommitted(any()) }
    }

    @Test
    fun `committed without a durable record is reported as FAILURE, never success`() =
        runTest(testDispatcher) {
            // The swap committed but the journal still reads Prepared, so the next launch rolls
            // this restore back — Success here would be a false success.
            val ref = makeRef(schema = 4)
            stubRestorableBackup(ref)
            coEvery { backupStorage.downloadBackup(any(), any()) } returns BackupResult.Success(
                ref.manifest,
            )
            coEvery { restoreStateRepository.beginAttempt(any()) } returns true
            val bookkeepingError = BackupError.Io(IOException("journal advance failed"))
            coEvery { databaseReplacement.restoreFromSnapshot(any(), any()) } coAnswers {
                secondArg<DatabaseReplacementEffects>().onBeforeMutation("/tmp/res.db")
                DatabaseReplacementResult.Committed(effectsError = bookkeepingError)
            }

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure, "a Committed with effectsError is a failure")
            assertSame(bookkeepingError, (result as BackupResult.Failure).error)
            coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
        }

    @Test
    fun `restoreFromSnapshot RejectedBeforeMutation resolves the attempt`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 4)
            stubRestorableBackup(ref)
            coEvery { backupStorage.downloadBackup(any(), any()) } returns BackupResult.Success(
                ref.manifest,
            )
            val corrupted = BackupError.CorruptedBackup("magic mismatch")
            val effectsSlot = slot<DatabaseReplacementEffects>()
            coEvery {
                databaseReplacement.restoreFromSnapshot(any(), capture(effectsSlot))
            } coAnswers {
                secondArg<DatabaseReplacementEffects>().onRejectedBeforeMutation(corrupted)
                DatabaseReplacementResult.RejectedBeforeMutation(corrupted)
            }

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            assertSame(corrupted, (result as BackupResult.Failure).error)
            // Nothing irreversible happened, so the slot is released by its own attempt id.
            coVerify(exactly = 1) {
                restoreStateRepository.resolveAttempt(effectsSlot.captured.attemptId)
            }
        }

    @Test
    fun `restoreFromSnapshot FailedAfterMutation leaves the attempt unresolved`() =
        runTest(testDispatcher) {
            // Past the point of no return the unresolved `Prepared` attempt IS the recovery
            // path; resolving or deleting anything here destroys it.
            val ref = makeRef(schema = 4)
            stubRestorableBackup(ref)
            coEvery { backupStorage.downloadBackup(any(), any()) } returns BackupResult.Success(
                ref.manifest,
            )
            val postCloseError = BackupError.Io(IOException("rename failed after close"))
            coEvery { databaseReplacement.restoreFromSnapshot(any(), any()) } coAnswers {
                secondArg<DatabaseReplacementEffects>().onFailedAfterMutation(postCloseError)
                DatabaseReplacementResult.FailedAfterMutation(postCloseError)
            }

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            assertSame(postCloseError, (result as BackupResult.Failure).error)
            coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
        }

    @Test
    fun `restoreFromSnapshot RecoveredByRollback resolves the attempt and clears the undo slot`() =
        runTest(testDispatcher) {
            // Canonical-consuming recovery: the canonical file is absent, so the flag clears.
            every { snapshotProvider.getPreRestoreBackupFile() } returns null
            val ref = makeRef(schema = 4)
            stubRestorableBackup(ref)
            coEvery { backupStorage.downloadBackup(any(), any()) } returns BackupResult.Success(
                ref.manifest,
            )
            val recoveredError = BackupError.Io(IOException("swap failed, rolled back"))
            val effectsSlot = slot<DatabaseReplacementEffects>()
            coEvery {
                databaseReplacement.restoreFromSnapshot(any(), capture(effectsSlot))
            } coAnswers {
                secondArg<DatabaseReplacementEffects>().onRecoveredByRollback(recoveredError)
                DatabaseReplacementResult.RecoveredByRollback(recoveredError)
            }

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            assertSame(recoveredError, (result as BackupResult.Failure).error)
            coVerify(exactly = 1) {
                restoreStateRepository.resolveAttempt(effectsSlot.captured.attemptId)
            }
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
        }

    @Test
    fun `a reservation-sourced recovery keeps the previous restore's undo availability`() =
        runTest(testDispatcher) {
            // Reservation-sourced recovery never touches the canonical slot, so the previous
            // restore's undo stays valid and the flag must NOT clear.
            val ref = makeRef(schema = 4)
            stubRestorableBackup(ref)
            coEvery { backupStorage.downloadBackup(any(), any()) } returns BackupResult.Success(
                ref.manifest,
            )
            every { snapshotProvider.getPreRestoreBackupFile() } returns
                java.io.File("pre_restore_backup.db")
            val recoveredError = BackupError.Io(IOException("swap failed, rolled back"))
            val effectsSlot = slot<DatabaseReplacementEffects>()
            coEvery {
                databaseReplacement.restoreFromSnapshot(any(), capture(effectsSlot))
            } coAnswers {
                secondArg<DatabaseReplacementEffects>().onRecoveredByRollback(recoveredError)
                DatabaseReplacementResult.RecoveredByRollback(recoveredError)
            }

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            coVerify(exactly = 1) {
                restoreStateRepository.resolveAttempt(effectsSlot.captured.attemptId)
            }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        }

    @Test
    fun `restoreFromSnapshot FatalNoGeneration surfaces an Io failure and deletes nothing`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 4)
            stubRestorableBackup(ref)
            coEvery { backupStorage.downloadBackup(any(), any()) } returns BackupResult.Success(
                ref.manifest,
            )
            // Terminal runtime: onFatal and nothing else; the attempt stays unresolved.
            coEvery { databaseReplacement.restoreFromSnapshot(any(), any()) } coAnswers {
                secondArg<DatabaseReplacementEffects>().onFatal()
                DatabaseReplacementResult.FatalNoGeneration()
            }

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            assertTrue((result as BackupResult.Failure).error is BackupError.Io)
            coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
        }

    @Test
    fun `restoreLatest with no migration path returns MissingMigrationPath`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 4)
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            every { snapshotProvider.hasMigrationPath(from = 4, to = 5) } returns false

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            val error = (result as BackupResult.Failure).error
            assertTrue(error is BackupError.MissingMigrationPath)
            assertEquals(4, (error as BackupError.MissingMigrationPath).backupSchemaVersion)
            assertEquals(5, error.appSchemaVersion)
            coVerify(exactly = 0) { backupStorage.downloadBackup(any(), any()) }
            coVerify(exactly = 0) { databaseReplacement.restoreFromSnapshot(any(), any()) }
        }

    @Test
    fun `restoreLatest with equal schema versions skips migration path check`() =
        runTest(testDispatcher) {
            // Equal schemas: the `<` guard short-circuits, so the check is never consulted.
            val ref = makeRef(schema = 5)
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            val downloadCaptured = slot<File>()
            coEvery {
                backupStorage.downloadBackup(any(), capture(downloadCaptured))
            } answers {
                downloadCaptured.captured.writeText("payload")
                BackupResult.Success(ref.manifest)
            }
            coEvery { databaseReplacement.restoreFromSnapshot(any(), any()) } returns
                DatabaseReplacementResult.Committed()

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Success)
            verify(exactly = 0) { snapshotProvider.hasMigrationPath(any(), any()) }
        }

    @Test
    fun `restoreLatest download failure returns early with no compensation and cleans temp file`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 4)
            stubRestorableBackup(ref)
            val downloadCaptured = slot<File>()
            val ioError = BackupError.Io(IOException("download failed"))
            coEvery {
                backupStorage.downloadBackup(any(), capture(downloadCaptured))
            } returns BackupResult.Failure(ioError)

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            assertSame(ioError, (result as BackupResult.Failure).error)
            coVerify(exactly = 0) { databaseReplacement.restoreFromSnapshot(any(), any()) }
            // Pre-submission failure: nothing exists yet to compensate.
            coVerify(exactly = 0) { restoreStateRepository.beginAttempt(any()) }
            coVerify(exactly = 0) { restoreStateRepository.resolveAttempt(any()) }
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 0) { restoreStateRepository.clearPreRestoreBackupAvailable() }
            assertFalse(downloadCaptured.captured.exists(), "temp file should be deleted")
        }

    @Test
    fun `completeSignIn maps api Success of Account to Success of Unit`() =
        runTest(testDispatcher) {
            val outcome = AuthResolutionOutcome(mockk<Intent>(relaxed = true))
            val expectedAccount = AccountDomain(email = "a@example.com", displayName = "A")
            coEvery { backupAuth.completeSignIn(outcome) } returns
                BackupResult.Success(Account(email = "a@example.com", displayName = "A"))
            val result = interactor.completeSignIn(outcome)
            assertTrue(result is BackupResult.Success)
            assertEquals(expectedAccount, (result as BackupResult.Success).data)
        }

    @Test
    fun `completeSignIn propagates Failure`() = runTest(testDispatcher) {
        val outcome = AuthResolutionOutcome(mockk<Intent>(relaxed = true))
        val error = BackupError.AuthRevoked
        coEvery { backupAuth.completeSignIn(outcome) } returns BackupResult.Failure(error)
        val result = interactor.completeSignIn(outcome)
        assertTrue(result is BackupResult.Failure)
        assertSame(error, (result as BackupResult.Failure).error)
    }

    @Test
    fun `signOut delegates to backupAuth`() = runTest(testDispatcher) {
        coEvery { backupAuth.signOut() } returns BackupResult.Success(Unit)
        val result = interactor.signOut()
        assertTrue(result is BackupResult.Success)
        coVerify(exactly = 1) { backupAuth.signOut() }
    }

    @Test
    fun `deleteAiExportSnapshots delegates to the snapshot runner`() = runTest(testDispatcher) {
        interactor.deleteAiExportSnapshots()

        coVerify(exactly = 1) { snapshotExportRunner.clearSnapshots() }
    }

    /** The schema gates passed: [ref] is the newest backup and restorable onto schema 5. */
    private fun stubRestorableBackup(ref: BackupRef) {
        coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
        coEvery { snapshotProvider.currentSchemaVersion() } returns 5
        every {
            snapshotProvider.hasMigrationPath(from = ref.manifest.dbSchemaVersion, to = 5)
        } returns true
    }

    private fun makeRef(
        remoteId: String = "remote-id",
        appVersion: String = "1.2.3",
        schema: Int = 5,
        createdAt: Long = 100L,
        size: Long = 256L,
    ): BackupRef = BackupRef(
        remoteId = remoteId,
        manifest = BackupManifest(
            appVersion = appVersion,
            dbSchemaVersion = schema,
            createdAtEpochMs = createdAt,
            dbFileSizeBytes = size,
            deviceModel = "Pixel",
        ),
    )

    private fun makeIntentSender(): IntentSender = mockk(relaxed = true)
}
