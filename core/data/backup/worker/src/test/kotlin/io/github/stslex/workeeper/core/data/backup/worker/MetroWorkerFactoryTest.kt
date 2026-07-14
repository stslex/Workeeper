// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.di.AppGraphContract
import io.github.stslex.workeeper.core.di.AppGraphContractHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Verification for [MetroWorkerFactory] (App-Scope Collapse Step 6 — Hilt-free): the factory dispatch
 * logic is proven on BOTH a known-positive (BackupWorker's class name → a constructed worker) and a
 * known-negative (any other class name → null fallthrough).
 *
 * The factory reads its six deps from `appContext.applicationContext.appGraphContract()`, so the test
 * Application implements [AppGraphContractHolder] and returns a mocked [AppGraphContract] — the graph
 * boundary. This isolates the factory's dispatch + construction wiring from real graph assembly.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = MetroWorkerFactoryTest.TestApplication::class, sdk = [33])
internal class MetroWorkerFactoryTest {

    class TestApplication : Application(), AppGraphContractHolder {
        override val appGraphContract: AppGraphContract = mockk(relaxed = true) {
            every { backupStorage } returns mockk<BackupStorage>(relaxed = true)
            every { databaseSnapshotProvider } returns mockk<DatabaseSnapshotProvider>(relaxed = true)
            every { backupPreferencesRepository } returns mockk<BackupPreferencesRepository>(relaxed = true)
            every { autoBackupController } returns mockk<AutoBackupController>(relaxed = true)
            every { backupNotificationHelper } returns mockk<BackupNotificationHelper>(relaxed = true)
            every { snapshotExportRunner } returns mockk<SnapshotExportRunner>(relaxed = true)
        }
    }

    private val workerParameters = mockk<WorkerParameters>(relaxed = true)

    private lateinit var appContext: Context
    private lateinit var factory: MetroWorkerFactory

    @BeforeEach
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        factory = MetroWorkerFactory(appContext)
    }

    @Test
    fun `known-positive - BackupWorker class name constructs a BackupWorker via the graph`() {
        val worker = factory.createWorker(
            appContext = appContext,
            workerClassName = BackupWorker::class.java.name,
            workerParameters = workerParameters,
        )

        // The factory read the six deps from the graph and constructed the real worker.
        assertInstanceOf(BackupWorker::class.java, worker)
        val graph = (appContext.applicationContext as AppGraphContractHolder).appGraphContract
        verify { graph.backupStorage }
        verify { graph.databaseSnapshotProvider }
        verify { graph.backupPreferencesRepository }
        verify { graph.autoBackupController }
        verify { graph.backupNotificationHelper }
        verify { graph.snapshotExportRunner }
    }

    @Test
    fun `known-negative - an unknown worker class returns null for default-factory fallthrough`() {
        val worker = factory.createWorker(
            appContext = appContext,
            workerClassName = "com.example.SomeOtherWorker",
            workerParameters = workerParameters,
        )

        // null → WorkManager's inherited createWorkerWithDefaultFallback constructs unknown workers via
        // the default reflection factory. No DelegatingWorkerFactory needed.
        assertNull(worker)
        // The graph must NOT be touched on the negative path (the != short-circuit precedes the lazy read).
        val graph = (appContext.applicationContext as AppGraphContractHolder).appGraphContract
        verify(exactly = 0) { graph.backupStorage }
    }
}
