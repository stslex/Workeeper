// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.worker.di.BackupWorkerHiltEntryPoint
import io.github.stslex.workeeper.core.data.backup.worker.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * S5 verification for [MetroWorkerFactory] (App-Scope Collapse Step 2 standup, Design B): the factory
 * dispatch logic is proven on BOTH a known-positive (BackupWorker's class name → a constructed worker)
 * and a known-negative (any other class name → null fallthrough) before it is trusted.
 *
 * The Hilt `SingletonComponent` is not stood up in this unit test, so
 * `EntryPointAccessors.fromApplication` is statically mocked to return a mocked
 * [BackupWorkerHiltEntryPoint] — the bridge boundary. This isolates the factory's own dispatch +
 * construction wiring (the Step-2 deliverable) from Hilt graph assembly, which is exercised at runtime.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = MetroWorkerFactoryTest.TestApplication::class, sdk = [33])
internal class MetroWorkerFactoryTest {

    class TestApplication : Application()

    private val entryPoint = mockk<BackupWorkerHiltEntryPoint>(relaxed = true).apply {
        every { backupStorage() } returns mockk<BackupStorage>(relaxed = true)
        every { databaseSnapshotProvider() } returns mockk<DatabaseSnapshotProvider>(relaxed = true)
        every { backupPreferencesRepository() } returns mockk<BackupPreferencesRepository>(relaxed = true)
        every { autoBackupController() } returns mockk<AutoBackupController>(relaxed = true)
        every { backupNotificationHelper() } returns mockk<BackupNotificationHelper>(relaxed = true)
        every { snapshotExportRunner() } returns mockk<SnapshotExportRunner>(relaxed = true)
    }

    private val workerParameters = mockk<WorkerParameters>(relaxed = true)

    private lateinit var appContext: Context
    private lateinit var factory: MetroWorkerFactory

    @BeforeEach
    fun setUp() {
        // Acquire the context INSIDE setUp (not a field initializer) so RobolectricExtension has
        // already registered the instrumentation — mirrors BackupWorkerTest's makeWorker() timing.
        appContext = ApplicationProvider.getApplicationContext()
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any<Context>(), BackupWorkerHiltEntryPoint::class.java)
        } returns entryPoint
        factory = MetroWorkerFactory(appContext)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(EntryPointAccessors::class)
    }

    @Test
    fun `known-positive - BackupWorker class name constructs a BackupWorker via the bridge`() {
        val worker = factory.createWorker(
            appContext = appContext,
            workerClassName = BackupWorker::class.java.name,
            workerParameters = workerParameters,
        )

        // The factory bridge-read the six deps and constructed the real worker — the exact path the
        // Step-6 flip will exercise once Configuration.Provider routes to this factory.
        assertInstanceOf(BackupWorker::class.java, worker)
        verify { entryPoint.backupStorage() }
        verify { entryPoint.databaseSnapshotProvider() }
        verify { entryPoint.backupPreferencesRepository() }
        verify { entryPoint.autoBackupController() }
        verify { entryPoint.backupNotificationHelper() }
        verify { entryPoint.snapshotExportRunner() }
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
        // The bridge must NOT be touched on the negative path (the != short-circuit precedes the lazy read).
        verify(exactly = 0) { entryPoint.backupStorage() }
    }
}
