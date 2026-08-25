// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupWorkInfo
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

/**
 * [BackupWorker] under first-operation lease admission: constructed dep-free by the default
 * factory, deps arriving via [BackupWorkerDepsHolder.awaitBackupWorkLease] inside `doWork`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = BackupWorkerTest.TestApplication::class, sdk = [33])
internal class BackupWorkerTest {

    class TestApplication : Application(), BackupWorkerDepsHolder {

        lateinit var deps: BackupWorkerDeps

        val acquiredLeases = mutableListOf<RecordingBackupWorkLease>()

        /** Mirrors a process that routed to recovery: admission is terminally refused. */
        var sealed = false

        override suspend fun awaitBackupWorkLease(): BackupWorkLease? =
            if (sealed) null else RecordingBackupWorkLease(deps).also { acquiredLeases += it }
    }

    private val backupStorage = mockk<BackupStorage>(relaxed = true)
    private val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val preferences = mockk<BackupPreferencesRepository>(relaxed = true)
    private val notificationHelper = mockk<BackupNotificationHelper>(relaxed = true)
    private val autoBackupController = mockk<AutoBackupController>(relaxed = true).apply {
        coEvery { observePeriodicStatus() } returns emptyFlow<List<AutoBackupWorkInfo>>()
        coEvery { observeOneTimeStatus() } returns emptyFlow<List<AutoBackupWorkInfo>>()
    }
    private val snapshotExportRunner = mockk<SnapshotExportRunner>(relaxed = true)

    private val application: TestApplication
        get() = ApplicationProvider.getApplicationContext<Context>().applicationContext
            as TestApplication

    private fun makeWorker(): BackupWorker = TestListenableWorkerBuilder<BackupWorker>(
        ApplicationProvider.getApplicationContext(),
    ).build()

    @BeforeEach
    fun setUp() {
        application.deps = object : BackupWorkerDeps {
            override val backupStorage: BackupStorage = this@BackupWorkerTest.backupStorage
            override val databaseSnapshotProvider: DatabaseSnapshotProvider = snapshotProvider
            override val backupPreferencesRepository: BackupPreferencesRepository = preferences
            override val autoBackupController: AutoBackupController =
                this@BackupWorkerTest.autoBackupController
            override val backupNotificationHelper: BackupNotificationHelper = notificationHelper
            override val snapshotExportRunner: SnapshotExportRunner =
                this@BackupWorkerTest.snapshotExportRunner
        }
        application.acquiredLeases.clear()
        application.sealed = false
        coEvery { snapshotProvider.captureSnapshot(any()) } answers {
            firstArg<File>().writeText("snapshot")
            BackupResult.Success(Unit)
        }
        coEvery { snapshotProvider.currentSchemaVersion() } returns 5
    }

    @Test
    fun `a sealed admission refuses BEFORE any bookkeeping or upload`() = runBlocking {
        // The recovery-routed process cannot prove what its database holds; uploading it would
        // rotate one of the user's three Drive backups away and record a false success.
        application.sealed = true

        val result = makeWorker().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { preferences.setLastAttempt(any()) }
        coVerify(exactly = 0) { preferences.setLastSuccess(any()) }
        coVerify(exactly = 0) { snapshotProvider.captureSnapshot(any()) }
        coVerify(exactly = 0) { backupStorage.uploadBackup(any(), any()) }
        assertTrue(application.acquiredLeases.isEmpty())
    }

    @Test
    fun `binary success result is unaffected when the snapshot runner throws`() = runBlocking {
        coEvery { backupStorage.uploadBackup(any(), any()) } returns BackupResult.Success(
            BackupRef(
                remoteId = "id",
                manifest = BackupManifest(
                    appVersion = "1.0.0",
                    dbSchemaVersion = 5,
                    createdAtEpochMs = 0L,
                    dbFileSizeBytes = 1L,
                    deviceModel = null,
                ),
            ),
        )
        coEvery { snapshotExportRunner.runIfEligibleAwaiting() } throws RuntimeException("runner blew up")

        val result = makeWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `worker awaits the snapshot export rather than firing it and forgetting`() = runBlocking {
        coEvery { backupStorage.uploadBackup(any(), any()) } returns BackupResult.Success(
            BackupRef(
                remoteId = "id",
                manifest = BackupManifest(
                    appVersion = "1.0.0",
                    dbSchemaVersion = 5,
                    createdAtEpochMs = 0L,
                    dbFileSizeBytes = 1L,
                    deviceModel = null,
                ),
            ),
        )

        makeWorker().doWork()

        // The wakelock window means the worker must await the export, never fire-and-forget.
        coVerify(exactly = 1) { snapshotExportRunner.runIfEligibleAwaiting() }
        coVerify(exactly = 0) { snapshotExportRunner.runIfEligible() }
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

    @Test
    fun `admission lease is acquired as the FIRST operation and released exactly once`() =
        runBlocking {
            coEvery { backupStorage.uploadBackup(any(), any()) } returns
                BackupResult.Failure(BackupError.NetworkUnavailable)

            makeWorker().doWork()

            // A transition's quiesce drain awaits this release: one lease, released once.
            assertEquals(1, application.acquiredLeases.size)
            assertEquals(1, application.acquiredLeases.single().releaseCount.get())
        }

    @Test
    fun `admission lease released even when the work body throws`() = runBlocking {
        coEvery { snapshotProvider.captureSnapshot(any()) } throws RuntimeException("body blew up")

        val thrown = runCatching { makeWorker().doWork() }.exceptionOrNull()

        assertNotNull(thrown, "the body's failure must propagate (no swallow)")
        assertEquals(1, application.acquiredLeases.single().releaseCount.get())
    }

    @Test
    fun `a worker constructed but never started acquires NO lease`() {
        // Both construction paths: the default reflection factory and MetroWorkerFactory.
        makeWorker()
        MetroWorkerFactory().createWorker(
            appContext = ApplicationProvider.getApplicationContext(),
            workerClassName = BackupWorker::class.java.name,
            workerParameters = mockk<WorkerParameters>(relaxed = true),
        )

        // A constructed-but-never-started worker must hold nothing a transition would await.
        assertTrue(application.acquiredLeases.isEmpty())
    }
}
