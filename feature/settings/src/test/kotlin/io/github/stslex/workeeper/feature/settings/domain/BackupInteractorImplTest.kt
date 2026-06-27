// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
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

internal class BackupInteractorImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val backupAuth = mockk<BackupAuth>(relaxed = true)
    private val backupStorage = mockk<BackupStorage>(relaxed = true)
    private val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val restoreStateRepository = mockk<RestoreStateRepository>(relaxed = true)
    private val snapshotExportRunner = mockk<SnapshotExportRunner>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val packageManager = mockk<android.content.pm.PackageManager>(relaxed = true)
    private val packageInfo = android.content.pm.PackageInfo().apply {
        versionName = "1.2.3"
    }

    @TempDir
    lateinit var cacheDir: File

    private lateinit var interactor: BackupInteractorImpl

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.cacheDir } returns cacheDir
        every { context.packageName } returns "io.github.stslex.workeeper"
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
        every {
            packageManager.getPackageInfo(
                any<String>(),
                any<android.content.pm.PackageManager.PackageInfoFlags>(),
            )
        } returns packageInfo
        every { backupAuth.state } returns MutableStateFlow(AuthState.SignedOut)
        coEvery { snapshotProvider.preserveCurrentDb() } returns BackupResult.Success(
            File(cacheDir, "pre_restore_backup.db"),
        )
        interactor = BackupInteractorImpl(
            backupAuth = backupAuth,
            backupStorage = backupStorage,
            snapshotProvider = snapshotProvider,
            restoreStateRepository = restoreStateRepository,
            snapshotExportRunner = snapshotExportRunner,
            context = context,
            dispatcher = testDispatcher,
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

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
        coEvery { backupAuth.requestDriveFileAccess() } returns SignInResult.NeedsResolution(sender)

        val outcome = interactor.requestDriveFileAccess()

        assertTrue(outcome is SignInOutcomeDomain.NeedsResolution)
        assertSame(sender, (outcome as SignInOutcomeDomain.NeedsResolution).intentSender)
    }

    @Test
    fun `isDriveFileGranted reflects the backupAuth grant flow`() = runTest(testDispatcher) {
        every { backupAuth.observeDriveFileGranted() } returns flowOf(true)

        assertTrue(interactor.isDriveFileGranted())
    }

    @Test
    fun `signIn Success returns SignInOutcomeDomain Success`() = runTest(testDispatcher) {
        coEvery { backupAuth.signIn() } returns SignInResult.Success(
            Account(email = "a@b.com", displayName = null),
        )
        assertEquals(SignInOutcomeDomain.Success, interactor.signIn())
    }

    @Test
    fun `signIn NeedsResolution propagates same intentSender`() = runTest(testDispatcher) {
        val sender = makeIntentSender()
        coEvery { backupAuth.signIn() } returns SignInResult.NeedsResolution(sender)
        val outcome = interactor.signIn()
        assertTrue(outcome is SignInOutcomeDomain.NeedsResolution)
        assertSame(sender, (outcome as SignInOutcomeDomain.NeedsResolution).intentSender)
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
            // File length captured in manifest matches what captureSnapshot wrote.
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
        val ioError = BackupError.Io(java.io.IOException("upload failed"))
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
            val ioError = BackupError.Io(java.io.IOException("capture failed"))
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
            coVerify(exactly = 0) { snapshotProvider.restoreFromSnapshot(any()) }
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
            coVerify(exactly = 0) { snapshotProvider.restoreFromSnapshot(any()) }
        }

    @Test
    fun `restoreLatest happy path preserves then downloads then restores then cleans temp file`() =
        runTest(testDispatcher) {
            val ref = makeRef(
                schema = 4,
                createdAt = 1_700_000_000_000L,
                appVersion = "1.0.0",
            )
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            every { snapshotProvider.hasMigrationPath(from = 4, to = 5) } returns true
            val downloadCaptured = slot<File>()
            coEvery {
                backupStorage.downloadBackup(any(), capture(downloadCaptured))
            } answers {
                downloadCaptured.captured.writeText("payload")
                BackupResult.Success(ref.manifest)
            }
            coEvery { snapshotProvider.restoreFromSnapshot(any()) } returns BackupResult.Success(
                Unit,
            )
            val contextSlot = slot<RestoreInProgressContext>()
            coEvery {
                restoreStateRepository.markRestoreInProgress(capture(contextSlot))
            } returns Unit

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Success)
            // Preserve + mark happen BEFORE download + restore commit.
            coVerifyOrder {
                snapshotProvider.preserveCurrentDb()
                restoreStateRepository.markRestoreInProgress(any())
                backupStorage.downloadBackup(ref, any())
                snapshotProvider.restoreFromSnapshot(any())
            }
            // On success the preserved file is kept for Application pre-flight + undo.
            coVerify(exactly = 0) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 0) { restoreStateRepository.clearRestoreInProgress() }
            assertEquals(4, contextSlot.captured.backupSchemaVersion)
            assertEquals(1_700_000_000_000L, contextSlot.captured.backupCreatedAtEpochMs)
            assertEquals("1.0.0", contextSlot.captured.backupAppVersion)
            assertTrue(contextSlot.captured.startedAtEpochMs > 0)
            assertFalse(downloadCaptured.captured.exists(), "temp file should be deleted")
        }

    @Test
    fun `restoreLatest preserveCurrentDb failure short-circuits before download`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 4)
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            every { snapshotProvider.hasMigrationPath(from = 4, to = 5) } returns true
            val ioError = BackupError.Io(java.io.IOException("disk full"))
            coEvery {
                snapshotProvider.preserveCurrentDb()
            } returns BackupResult.Failure(ioError)

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            assertSame(ioError, (result as BackupResult.Failure).error)
            coVerify(exactly = 0) { backupStorage.downloadBackup(any(), any()) }
            coVerify(exactly = 0) { snapshotProvider.restoreFromSnapshot(any()) }
            coVerify(exactly = 0) { restoreStateRepository.markRestoreInProgress(any()) }
        }

    @Test
    fun `restoreFromSnapshot failure after preserve cleans up preserved file and flag`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 4)
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            every { snapshotProvider.hasMigrationPath(from = 4, to = 5) } returns true
            coEvery { backupStorage.downloadBackup(any(), any()) } returns BackupResult.Success(
                ref.manifest,
            )
            val corrupted = BackupError.CorruptedBackup("magic mismatch")
            coEvery { snapshotProvider.restoreFromSnapshot(any()) } returns BackupResult.Failure(
                corrupted,
            )

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            assertSame(corrupted, (result as BackupResult.Failure).error)
            coVerify(exactly = 1) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 1) { restoreStateRepository.clearRestoreInProgress() }
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
            coVerify(exactly = 0) { snapshotProvider.restoreFromSnapshot(any()) }
        }

    @Test
    fun `restoreLatest with equal schema versions skips migration path check`() =
        runTest(testDispatcher) {
            // backup schema == current: no migration needed, the check should not
            // be consulted at all (the `<` guard short-circuits).
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
            coEvery { snapshotProvider.restoreFromSnapshot(any()) } returns BackupResult.Success(
                Unit,
            )

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Success)
            verify(exactly = 0) { snapshotProvider.hasMigrationPath(any(), any()) }
        }

    @Test
    fun `restoreLatest download failure cleans preserved snapshot and flag and temp file`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 4)
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            every { snapshotProvider.hasMigrationPath(from = 4, to = 5) } returns true
            val downloadCaptured = slot<File>()
            val ioError = BackupError.Io(java.io.IOException("download failed"))
            coEvery {
                backupStorage.downloadBackup(any(), capture(downloadCaptured))
            } returns BackupResult.Failure(ioError)

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            assertSame(ioError, (result as BackupResult.Failure).error)
            coVerify(exactly = 0) { snapshotProvider.restoreFromSnapshot(any()) }
            // Pre-swap failure: live db never changed, so just clean the preserved
            // snapshot + DataStore flag.
            coVerify(exactly = 1) { snapshotProvider.deletePreRestoreBackup() }
            coVerify(exactly = 1) { restoreStateRepository.clearRestoreInProgress() }
            assertFalse(downloadCaptured.captured.exists(), "temp file should be deleted")
        }

    @Test
    fun `completeSignIn maps api Success of Account to Success of Unit`() =
        runTest(testDispatcher) {
            val intent = mockk<Intent>(relaxed = true)
            val expectedAccount = AccountDomain(email = "a@b.com", displayName = "A")
            coEvery { backupAuth.completeSignIn(intent) } returns
                BackupResult.Success(Account(email = "a@b.com", displayName = "A"))
            val result = interactor.completeSignIn(intent)
            assertTrue(result is BackupResult.Success)
            assertEquals(expectedAccount, (result as BackupResult.Success).data)
        }

    @Test
    fun `completeSignIn propagates Failure`() = runTest(testDispatcher) {
        val intent = mockk<Intent>(relaxed = true)
        val error = BackupError.AuthRevoked
        coEvery { backupAuth.completeSignIn(intent) } returns BackupResult.Failure(error)
        val result = interactor.completeSignIn(intent)
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
