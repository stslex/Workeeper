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
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Verification for [MetroWorkerFactory] (Phase 5 R2, closed-admission leases): the factory dispatch
 * logic is proven on BOTH a known-positive (BackupWorker's class name → a constructed worker) and a
 * known-negative (any other class name → null fallthrough), and — the finding-2 shape — every
 * construction ACQUIRES one admission lease through the typed [BackupWorkerDepsHolder], binding the
 * worker's deps to exactly one runtime generation at admission time.
 *
 * The test Application implements [BackupWorkerDepsHolder] and RECORDS the leases it mints, so the
 * tests assert against the atomically-captured lease deps rather than lazy holder reads.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = MetroWorkerFactoryTest.TestApplication::class, sdk = [33])
internal class MetroWorkerFactoryTest {

    class TestApplication : Application(), BackupWorkerDepsHolder {
        /** Swappable — the generation-swap test replaces it mid-test between admissions. */
        var deps: BackupWorkerDeps = newWorkerDeps()

        /** Every lease this holder minted, in acquisition order. */
        val acquiredLeases = mutableListOf<RecordingBackupWorkLease>()

        override fun backupWorkerDeps(): BackupWorkerDeps = deps

        override fun acquireBackupWorkLease(): BackupWorkLease =
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
        factory = MetroWorkerFactory(appContext)
    }

    @Test
    fun `known-positive - BackupWorker class name acquires ONE lease and constructs the worker`() {
        val worker = factory.createWorker(
            appContext = appContext,
            workerClassName = BackupWorker::class.java.name,
            workerParameters = workerParameters,
        )

        // The factory admitted the worker through the holder: one lease, atomically carrying the
        // current generation's deps, before the worker object exists.
        assertInstanceOf(BackupWorker::class.java, worker)
        assertEquals(1, application.acquiredLeases.size)
        assertSame(application.deps, application.acquiredLeases.single().deps)
    }

    @Test
    fun `known-negative - an unknown worker class returns null and touches NO admission`() {
        val worker = factory.createWorker(
            appContext = appContext,
            workerClassName = "com.example.SomeOtherWorker",
            workerParameters = workerParameters,
        )

        // null → WorkManager's inherited createWorkerWithDefaultFallback constructs unknown workers
        // via the default reflection factory. The admission gate must NOT be entered on the
        // negative path (the != short-circuit precedes the acquire) — a foreign worker must never
        // hold up a replacement transition's lease drain.
        assertNull(worker)
        assertTrue(application.acquiredLeases.isEmpty())
    }

    @Test
    fun `per-admission binding - a worker created after a generation swap leases the NEW deps`() {
        val generationOne = application.deps

        factory.createWorker(
            appContext = appContext,
            workerClassName = BackupWorker::class.java.name,
            workerParameters = workerParameters,
        )

        // The generation swap: the holder now admits against a NEW graph's deps. A factory that
        // captured at construction (the pre-Phase-5 `by lazy`) would keep serving generationOne —
        // pinning every future worker to a terminal generation (spec §8.6).
        val generationTwo = newWorkerDeps()
        application.deps = generationTwo

        factory.createWorker(
            appContext = appContext,
            workerClassName = BackupWorker::class.java.name,
            workerParameters = workerParameters,
        )

        // Each lease bound its deps AT ADMISSION: the first worker is coherently generation-1,
        // the second coherently generation-2 — no torn cross-generation worker.
        assertEquals(2, application.acquiredLeases.size)
        assertSame(generationOne, application.acquiredLeases[0].deps)
        assertSame(generationTwo, application.acquiredLeases[1].deps)
    }
}

private fun newWorkerDeps(): BackupWorkerDeps = mockk(relaxed = true) {
    every { backupStorage } returns mockk<BackupStorage>(relaxed = true)
    every { databaseSnapshotProvider } returns mockk<DatabaseSnapshotProvider>(relaxed = true)
    every { backupPreferencesRepository } returns mockk<BackupPreferencesRepository>(relaxed = true)
    every { autoBackupController } returns mockk<AutoBackupController>(relaxed = true)
    every { backupNotificationHelper } returns mockk<BackupNotificationHelper>(relaxed = true)
    every { snapshotExportRunner } returns mockk<SnapshotExportRunner>(relaxed = true)
}
