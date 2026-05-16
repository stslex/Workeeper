// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupWorkInfo
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.worker.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

@ExtendWith(RobolectricExtension::class)
@Config(application = BackupWorkerTest.TestApplication::class, sdk = [33])
internal class BackupWorkerTest {

    class TestApplication : Application()

    private val backupStorage = mockk<BackupStorage>(relaxed = true)
    private val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val preferences = mockk<BackupPreferencesRepository>(relaxed = true)
    private val notificationHelper = mockk<BackupNotificationHelper>(relaxed = true)
    private val autoBackupController = mockk<AutoBackupController>(relaxed = true).apply {
        coEvery { observePeriodicStatus() } returns emptyFlow<List<AutoBackupWorkInfo>>()
        coEvery { observeOneTimeStatus() } returns emptyFlow<List<AutoBackupWorkInfo>>()
    }

    private fun makeWorker(): BackupWorker = TestListenableWorkerBuilder<BackupWorker>(
        ApplicationProvider.getApplicationContext(),
    ).setWorkerFactory(
        WorkerTestFactory(
            backupStorage = backupStorage,
            snapshotProvider = snapshotProvider,
            preferences = preferences,
            autoBackupController = autoBackupController,
            notificationHelper = notificationHelper,
        ),
    ).build()

    @BeforeEach
    fun setUp() {
        coEvery { snapshotProvider.captureSnapshot(any()) } answers {
            firstArg<File>().writeText("snapshot")
            BackupResult.Success(Unit)
        }
        coEvery { snapshotProvider.currentSchemaVersion() } returns 5
    }

    @Test
    fun `success path writes lastSuccess null error and cancels paused notification`() = runBlocking {
        coEvery { backupStorage.uploadBackup(any(), any()) } returns BackupResult.Success(
            BackupRef(
                remoteId = "id-1",
                manifest = BackupManifest(
                    appVersion = "1.0.0",
                    dbSchemaVersion = 5,
                    createdAtEpochMs = 0L,
                    dbFileSizeBytes = 10L,
                    deviceModel = "test",
                ),
            ),
        )

        val result = makeWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { preferences.setLastAttempt(any()) }
        coVerify { preferences.setLastSuccess(any()) }
        coVerify { preferences.setLastError(null) }
        coVerify { notificationHelper.cancelAuthPaused() }
    }

    @Test
    fun `AuthRevoked cancels periodic shows notification sets error and returns failure`() =
        runBlocking {
            coEvery { backupStorage.uploadBackup(any(), any()) } returns
                BackupResult.Failure(BackupError.AuthRevoked)

            val result = makeWorker().doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            coVerify { preferences.setLastError(BackupErrorCode.AuthRevoked) }
            coVerify { autoBackupController.cancelPeriodic() }
            coVerify { notificationHelper.showAuthPaused() }
            coVerify(exactly = 0) { preferences.setLastSuccess(any()) }
        }

    @Test
    fun `NetworkUnavailable sets error code and returns retry`() = runBlocking {
        coEvery { backupStorage.uploadBackup(any(), any()) } returns
            BackupResult.Failure(BackupError.NetworkUnavailable)

        val result = makeWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify { preferences.setLastError(BackupErrorCode.NetworkUnavailable) }
        coVerify(exactly = 0) { autoBackupController.cancelPeriodic() }
        coVerify(exactly = 0) { notificationHelper.showAuthPaused() }
    }

    @Test
    fun `StorageQuotaExceeded sets error code and returns failure without paused notification`() =
        runBlocking {
            coEvery { backupStorage.uploadBackup(any(), any()) } returns
                BackupResult.Failure(BackupError.StorageQuotaExceeded)

            val result = makeWorker().doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            coVerify { preferences.setLastError(BackupErrorCode.StorageQuotaExceeded) }
            coVerify(exactly = 0) { notificationHelper.showAuthPaused() }
            coVerify(exactly = 0) { autoBackupController.cancelPeriodic() }
        }

    @Test
    fun `Io failure returns retry with error code persisted`() = runBlocking {
        coEvery { backupStorage.uploadBackup(any(), any()) } returns
            BackupResult.Failure(BackupError.Io(RuntimeException("disk")))

        val result = makeWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify { preferences.setLastError(BackupErrorCode.Io) }
    }

    @Test
    fun `snapshot capture failure surfaces as retry without upload`() = runBlocking {
        coEvery { snapshotProvider.captureSnapshot(any()) } returns
            BackupResult.Failure(BackupError.Io(RuntimeException("snap")))

        val result = makeWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { backupStorage.uploadBackup(any(), any()) }
        coVerify { preferences.setLastError(BackupErrorCode.Io) }
    }
}
