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
import io.github.stslex.workeeper.recovery.RestoreDialogChoiceObserver
import io.github.stslex.workeeper.recovery.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.recovery.StartupMigrationCoordinator
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
        handleRecoveryPreflightChain()
        cleanupOrphanedImageTempFiles()
        bootstrapAppDialogObserver()
        PerformanceMetricsRecorder.process(RecordAction.AppCreated)
    }

    /**
     * Runs the two recovery pre-flights in the order required by
     * `documentation/feature-specs/backup-recovery.md`:
     *
     * 1. **Scenario 1** (post-restart restore migration). If the
     *    `restore_in_progress` flag is set, the coordinator either publishes
     *    a `RestoreSuccess` dialog and returns `RestoreSucceeded` (continue
     *    to MainActivity), or rolls back the live db and returns
     *    `RestoreRolledBack` (caller restarts — this method never returns).
     *    `NoOp` means there was no restore in progress; fall through.
     * 2. **Scenario 2** (startup migration failure / developer error).
     *    Only runs after Scenario 1 was a no-op. Reads the live db's
     *    schema via a Room-free SQLite peek and decides whether to
     *    `Proceed` (MainActivity opens normally) or `RouteToRecovery`
     *    (MainActivity reads `coordinator.lastDecision` and finishes
     *    itself, launching `RecoveryActivity`).
     *
     * Both checks run under `runBlocking` because the alternative —
     * dispatching on a background coroutine after `setContent` — would
     * briefly show MainActivity content before recovery routing decides.
     * The work is bounded: a DataStore read, one SQLite version peek, and
     * (on failure) one file copy. Steady-state cost on a healthy install
     * is ~one DataStore read and one peek.
     */
    private fun handleRecoveryPreflightChain() {
        val recoveryEntryPoint = EntryPointAccessors.fromApplication(
            this,
            RecoveryEntryPoint::class.java,
        )
        val restoreOutcome = runBlocking {
            recoveryEntryPoint.restoreRecoveryCoordinator().handlePostRestoreLaunch()
        }
        if (restoreOutcome == RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack) {
            recoveryEntryPoint.restoreRecoveryCoordinator().restartApp()
            return
        }
        if (restoreOutcome == RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded) {
            // The Scenario 1 success path leaves `pre_restore_backup.db` on
            // disk for the user's undo slot, and Room will open the
            // freshly-restored db on first DAO access. Scenario 2 has
            // nothing to add — skip.
            return
        }
        // Scenario 1 was a no-op (no restore in progress). Run Scenario 2.
        runBlocking {
            recoveryEntryPoint.startupMigrationCoordinator().checkAndRouteOrProceed()
        }
        // The result is cached on `StartupMigrationCoordinator.lastDecision`;
        // MainActivity reads it on its own onCreate to decide whether to
        // finish + launch RecoveryActivity.
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

    /**
     * Eagerly construct the `@Singleton` cross-feature dialog reactor so its
     * `init { observer.observeUserActions()...launchIn(scope) }` registers a
     * subscriber on the SharedFlow BEFORE MainActivity.onCreate runs. Lazy
     * @Singleton construction would mean the first user dispatch fires on
     * zero subscribers and is lost (same failure class as the rehydrate bug
     * we're explicitly avoiding). The return value is intentionally
     * discarded — the side-effect of construction is what we want.
     *
     * Same EntryPoint pattern as [RecoveryEntryPoint] and
     * [ImageStorageEntryPoint]; see those for the established convention.
     */
    private fun bootstrapAppDialogObserver() {
        EntryPointAccessors.fromApplication(
            this,
            AppDialogObserverBootstrapEntryPoint::class.java,
        ).restoreDialogChoiceObserver()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ImageStorageEntryPoint {
        fun imageStorage(): ImageStorage
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface RecoveryEntryPoint {
        fun restoreRecoveryCoordinator(): RestoreRecoveryCoordinator
        fun startupMigrationCoordinator(): StartupMigrationCoordinator
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface AppDialogObserverBootstrapEntryPoint {
        fun restoreDialogChoiceObserver(): RestoreDialogChoiceObserver
    }
}
