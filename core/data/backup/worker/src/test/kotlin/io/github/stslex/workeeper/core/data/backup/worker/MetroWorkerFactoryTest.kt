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
 * Verification for [MetroWorkerFactory] (Phase 5 R2, first-operation lease admission): the factory
 * dispatch logic is proven on BOTH a known-positive (BackupWorker's class name → a constructed
 * worker) and a known-negative (any other class name → null fallthrough), and — the §8.4/§8.6
 * shape — CONSTRUCTION touches no admission at all. The factory captures nothing
 * generation-scoped; a run binds to a generation only when `doWork` acquires its lease through
 * the typed [BackupWorkerDepsHolder] as the first operation.
 *
 * The test Application implements [BackupWorkerDepsHolder] and RECORDS the leases it mints, so
 * the tests assert against the atomically-captured lease deps rather than lazy holder reads.
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

        // Construction is dependency-free (spec §8.6): WorkManager caches the factory for the
        // process and may construct workers it never starts, so the factory must capture NO
        // admission lease — admission is doWork's first operation, never the factory's.
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

        // null → WorkManager's inherited createWorkerWithDefaultFallback constructs unknown
        // workers via the default reflection factory. The admission gate must NOT be entered on
        // the negative path — a foreign worker must never hold up a replacement transition's
        // lease drain.
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

        // The generation swap AFTER construction but BEFORE the run: a factory that captured
        // deps at construction (the pre-Phase-5 `by lazy`) would pin this run to
        // constructionDeps — serving a terminal generation to every future worker (spec §8.6).
        val doWorkTimeDeps = newWorkerDeps()
        application.deps = doWorkTimeDeps

        worker.doWork()

        // First-op admission bound the run to the deps CURRENT at doWork time, atomically with
        // the lease the quiesce drain awaits.
        val lease = application.acquiredLeases.single()
        assertSame(doWorkTimeDeps, lease.deps)
        assertNotSame(constructionDeps, lease.deps)
        assertEquals(1, lease.releaseCount.get())
    }
}

private fun newWorkerDeps(): BackupWorkerDeps = mockk(relaxed = true) {
    every { backupStorage } returns mockk<BackupStorage>(relaxed = true)
    every { databaseSnapshotProvider } returns mockk<DatabaseSnapshotProvider>(relaxed = true) {
        // A real sealed-type result so a run through these deps takes the early-return failure
        // path (relaxed MockK cannot synthesize a BackupResult branch the exhaustive when knows).
        coEvery { captureSnapshot(any()) } returns
            BackupResult.Failure(BackupError.NetworkUnavailable)
    }
    every { backupPreferencesRepository } returns mockk<BackupPreferencesRepository>(relaxed = true)
    every { autoBackupController } returns mockk<AutoBackupController>(relaxed = true)
    every { backupNotificationHelper } returns mockk<BackupNotificationHelper>(relaxed = true)
    every { snapshotExportRunner } returns mockk<SnapshotExportRunner>(relaxed = true)
}
