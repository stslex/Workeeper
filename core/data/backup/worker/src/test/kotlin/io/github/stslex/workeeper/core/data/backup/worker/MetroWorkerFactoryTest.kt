// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * [MetroWorkerFactory] dispatch on a known-positive and known-negative class name; construction
 * must touch no admission. The test Application records every lease it mints.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = MetroWorkerFactoryTest.TestApplication::class, sdk = [33])
internal class MetroWorkerFactoryTest {

    class TestApplication : Application(), BackupWorkerDepsHolder {
        /** Swappable — the doWork-time binding test replaces it between construction and run. */
        var deps: BackupWorkerDeps = newWorkerDeps()

        /** Every lease this holder minted, in acquisition order. */
        val acquiredLeases = mutableListOf<RecordingBackupWorkLease>()

        override suspend fun awaitBackupWorkLease(): BackupWorkLease =
            RecordingBackupWorkLease(deps).also { acquiredLeases += it }
    }

    private val workerParameters = mockk<WorkerParameters>(relaxed = true)

    private lateinit var appContext: Context
    private lateinit var application: TestApplication
    private lateinit var factory: MetroWorkerFactory

    @BeforeEach
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        application = appContext.applicationContext as TestApplication
        application.acquiredLeases.clear()
        factory = MetroWorkerFactory()
    }

    @Test
    fun `known-positive - BackupWorker class name constructs the worker WITHOUT touching admission`() {
        val worker = factory.createWorker(
            appContext = appContext,
            workerClassName = BackupWorker::class.java.name,
            workerParameters = workerParameters,
        )

        // GUARD: construction must capture NO lease; admission is doWork's first operation.
        assertInstanceOf(BackupWorker::class.java, worker)
        assertTrue(application.acquiredLeases.isEmpty())
    }

    @Test
    fun `known-negative - unknown class returns null and touches no admission`() {
        val worker = factory.createWorker(
            appContext = appContext,
            workerClassName = "com.example.SomeOtherWorker",
            workerParameters = workerParameters,
        )

        // null hands construction to WorkManager's default reflection factory; the negative path
        // must not enter the admission gate or a foreign worker blocks the quiesce drain.
        assertNull(worker)
        assertTrue(application.acquiredLeases.isEmpty())
    }

    @Test
    fun `the run binds the deps current at doWork time, not construction time`() = runBlocking {
        val constructionDeps = application.deps
        val worker = factory.createWorker(
            appContext = appContext,
            workerClassName = BackupWorker::class.java.name,
            workerParameters = workerParameters,
        ) as BackupWorker

        // Swap the generation after construction: a construction-time capture would pin the run.
        val doWorkTimeDeps = newWorkerDeps()
        application.deps = doWorkTimeDeps

        worker.doWork()

        // First-op admission bound the run to the deps current at doWork time.
        val lease = application.acquiredLeases.single()
        assertSame(doWorkTimeDeps, lease.deps)
        assertNotSame(constructionDeps, lease.deps)
        assertEquals(1, lease.releaseCount.get())
    }
}

private fun newWorkerDeps(): BackupWorkerDeps = mockk(relaxed = true) {
    every { backupStorage } returns mockk<BackupStorage>(relaxed = true)
    every { databaseSnapshotProvider } returns mockk<DatabaseSnapshotProvider>(relaxed = true) {
        // Relaxed MockK cannot synthesize a sealed BackupResult branch; stub the failure path.
        coEvery { captureSnapshot(any()) } returns
            BackupResult.Failure(BackupError.NetworkUnavailable)
    }
    every { backupPreferencesRepository } returns mockk<BackupPreferencesRepository>(relaxed = true)
    every { autoBackupController } returns mockk<AutoBackupController>(relaxed = true)
    every { backupNotificationHelper } returns mockk<BackupNotificationHelper>(relaxed = true)
    every { snapshotExportRunner } returns mockk<SnapshotExportRunner>(relaxed = true)
}
