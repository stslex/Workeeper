// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.recovery.RestoreRecoveryCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

abstract class BaseApplication : Application(), Configuration.Provider {

    abstract val isDebugLoggingAllow: Boolean

    @Inject
    internal lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlyticsHolder.initialize()
        Log.isLogging = isDebugLoggingAllow
        handleRestoreRecoveryIfNeeded()
        cleanupOrphanedImageTempFiles()
        PerformanceMetricsRecorder.process(RecordAction.AppCreated)
    }

    /**
     * Scenario 1 post-restart pre-flight (see
     * `documentation/feature-specs/backup-recovery.md`). Runs synchronously
     * on the main thread because the alternative — checking on a background
     * coroutine after `setContent` — would let the user briefly see a UI
     * destination before we can roll back a failed restore. The work is
     * strictly bounded: at most one DataStore read, one Room version-query,
     * and (on failure) one file copy + a few DataStore writes. Pre-flight
     * short-circuits to no-op when `restore_in_progress` is `false`, which
     * is the case on every normal launch.
     *
     * On the rolled-back path the coordinator restarts the app — this Method
     * never returns in that case.
     */
    private fun handleRestoreRecoveryIfNeeded() {
        val coordinator = EntryPointAccessors.fromApplication(
            this,
            RestoreRecoveryEntryPoint::class.java,
        ).restoreRecoveryCoordinator()
        val outcome = runBlocking { coordinator.handlePostRestoreLaunch() }
        if (outcome == RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack) {
            coordinator.restartApp()
        }
    }

    private fun cleanupOrphanedImageTempFiles() {
        val imageStorage = EntryPointAccessors.fromApplication(
            this,
            ImageStorageEntryPoint::class.java,
        ).imageStorage()
        // Fire-and-forget on a one-shot IO coroutine — clearing temp files left
        // behind by killed camera-capture flows is best-effort.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            imageStorage.cleanupTempFiles()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ImageStorageEntryPoint {
        fun imageStorage(): ImageStorage
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface RestoreRecoveryEntryPoint {
        fun restoreRecoveryCoordinator(): RestoreRecoveryCoordinator
    }
}
