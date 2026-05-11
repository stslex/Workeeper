// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
        interactor = BackupInteractorImpl(
            backupAuth = backupAuth,
            backupStorage = backupStorage,
            snapshotProvider = snapshotProvider,
            context = context,
            dispatcher = testDispatcher,
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

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
    fun `restoreLatest with backup schema newer than current returns SchemaTooNew`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 9)
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            val error = (result as BackupResult.Failure).error
            assertTrue(error is BackupError.SchemaTooNew)
            assertEquals(9, (error as BackupError.SchemaTooNew).backupSchemaVersion)
            assertEquals(5, error.appSchemaVersion)
            coVerify(exactly = 0) { backupStorage.downloadBackup(any(), any()) }
            coVerify(exactly = 0) { snapshotProvider.restoreFromSnapshot(any()) }
        }

    @Test
    fun `restoreLatest happy path downloads then restores then cleans temp file`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 4)
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            val downloadCaptured = slot<File>()
            coEvery {
                backupStorage.downloadBackup(any(), capture(downloadCaptured))
            } answers {
                downloadCaptured.captured.writeText("payload")
                BackupResult.Success(ref.manifest)
            }
            coEvery { snapshotProvider.restoreFromSnapshot(any()) } returns BackupResult.Success(Unit)

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Success)
            coVerify(exactly = 1) { backupStorage.downloadBackup(ref, any()) }
            coVerify(exactly = 1) { snapshotProvider.restoreFromSnapshot(any()) }
            assertFalse(downloadCaptured.captured.exists(), "temp file should be deleted")
        }

    @Test
    fun `restoreLatest download failure cleans temp file and does not restore`() =
        runTest(testDispatcher) {
            val ref = makeRef(schema = 4)
            coEvery { backupStorage.listBackups() } returns BackupResult.Success(listOf(ref))
            coEvery { snapshotProvider.currentSchemaVersion() } returns 5
            val downloadCaptured = slot<File>()
            val ioError = BackupError.Io(java.io.IOException("download failed"))
            coEvery {
                backupStorage.downloadBackup(any(), capture(downloadCaptured))
            } returns BackupResult.Failure(ioError)

            val result = interactor.restoreLatest()

            assertTrue(result is BackupResult.Failure)
            assertSame(ioError, (result as BackupResult.Failure).error)
            coVerify(exactly = 0) { snapshotProvider.restoreFromSnapshot(any()) }
            assertFalse(downloadCaptured.captured.exists(), "temp file should be deleted")
        }

    @Test
    fun `completeSignIn maps api Success of Account to Success of Unit`() = runTest(testDispatcher) {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { backupAuth.completeSignIn(intent) } returns BackupResult.Success(
            Account(email = "a@b.com", displayName = "A"),
        )
        val result = interactor.completeSignIn(intent)
        assertTrue(result is BackupResult.Success)
        assertEquals(Unit, (result as BackupResult.Success).data)
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
