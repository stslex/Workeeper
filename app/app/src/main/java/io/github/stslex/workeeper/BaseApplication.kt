// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import android.app.Application
import androidx.work.Configuration
import io.github.stslex.workeeper.core.core.images.buildImageStorage
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.worker.MetroWorkerFactory
import io.github.stslex.workeeper.core.data.database.buildAppDatabase
import io.github.stslex.workeeper.core.di.AppGraphContract
import io.github.stslex.workeeper.core.di.AppGraphContractHolder
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.di.AppGraphOwner
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.feature.app_dialogs.api.AppDialogPublisherHolder
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogInternalsHolder
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The process [Application] base, Hilt-free (App-Scope Collapse Step 6 — the cut). Holds the Metro
 * app-scope [AppGraph] for the whole process and exposes it through the interface seams every consumer
 * reads: [AppGraphOwner] (in-module: `MainActivity`/adopt-back-era readers), [AppGraphContractHolder]
 * (library consumers via `appGraphContract()`), and the two feature-tier holders
 * ([AppDialogPublisherHolder], [AppDialogInternalsHolder]) for the app-dialogs types `core:di` cannot name.
 */
abstract class BaseApplication :
    Application(),
    Configuration.Provider,
    AppGraphOwner,
    AppGraphContractHolder,
    AppDialogPublisherHolder,
    AppDialogInternalsHolder {

    abstract val isDebugLoggingAllow: Boolean

    /**
     * The Metro app-scope graph, held for the whole process. `by lazy` so it is created on first access
     * (a feature Store construction, well after `onCreate`). Constructs the two `create()` roots directly,
     * Hilt-free: [buildAppDatabase] (a cold `Room.databaseBuilder(...).build()` — no SQLite open, so
     * `RecoveryActivity`'s Room-free bootstrap safety holds) and [ImageStorageImpl] via
     * [buildImageStorageOrNull]; `@IODispatcher` is `Dispatchers.IO` directly (the graph is under
     * construction — reading its own dispatcher would cycle; `Dispatchers.IO` is the identical stateless
     * process-singleton the graph's accessor returns).
     */
    @Suppress("EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR_ERROR", "EXPOSED_PROPERTY_TYPE")
    override val appGraph: AppGraph by lazy {
        buildAppGraph(
            applicationContext = applicationContext,
            appDatabase = buildAppDatabase(applicationContext),
            imageStorage = buildImageStorage(applicationContext, Dispatchers.IO),
        )
    }

    override val appGraphContract: AppGraphContract get() = appGraph

    override val appDialogPublisher: AppDialogPublisher get() = appGraph.appDialogPublisher

    override val appDialogRepository: AppDialogRepository get() = appGraph.appDialogRepository

    override val appDialogObserverImpl: AppDialogObserverImpl get() = appGraph.appDialogObserverImpl

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(MetroWorkerFactory(this))
            .build()

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlyticsHolder.initialize()
        Log.isLogging = isDebugLoggingAllow
        onCreateGraphBootstrap()
        PerformanceMetricsRecorder.process(RecordAction.AppCreated)
    }

    /**
     * The graph-touching half of [onCreate], extracted behind an overridable seam (App-Scope Collapse
     * Step 6, Phase 3.3). Every statement here reads [appGraph], which would force the `by lazy` graph to
     * build with the production `create()` roots (file-backed `buildAppDatabase`). The consolidated
     * `:app:app` androidTest harness overrides this to a no-op in its `TestApplication`, so the
     * `MetroTestRule` can install a fresh per-test graph (in-memory / fail-fast DB) BEFORE any graph read.
     * `protected open` keeps the seam `:app:app`-internal — no cross-module visibility change.
     */
    protected open fun onCreateGraphBootstrap() {
        handleRecoveryPreflightChain()
        cleanupOrphanedImageTempFiles()
        bootstrapAppDialogObserver()
    }

    /**
     * Runs the two recovery pre-flights in the order required by
     * `documentation/feature-specs/backup-recovery.md`:
     *
     * 1. **Scenario 1** (post-restart restore migration). If the `restore_in_progress` flag is set, the
     *    coordinator either publishes a `RestoreSuccess` dialog and returns `RestoreSucceeded` (continue
     *    to MainActivity), or rolls back the live db and returns `RestoreRolledBack` (caller restarts —
     *    this method never returns). `NoOp` means there was no restore in progress; fall through.
     * 2. **Scenario 2** (startup migration failure / developer error). Only runs after Scenario 1 was a
     *    no-op. Reads the live db's schema via a Room-free SQLite peek and decides whether to `Proceed`
     *    (MainActivity opens normally) or `RouteToRecovery` (MainActivity reads `coordinator.lastDecision`
     *    and finishes itself, launching `RecoveryActivity`).
     *
     * Both checks run under `runBlocking` because the alternative — dispatching on a background coroutine
     * after `setContent` — would briefly show MainActivity content before recovery routing decides.
     */
    private fun handleRecoveryPreflightChain() {
        val restoreOutcome = runBlocking {
            appGraph.restoreRecoveryCoordinator.handlePostRestoreLaunch()
        }
        if (restoreOutcome == RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack) {
            appGraph.restoreRecoveryCoordinator.restartApp()
            return
        }
        if (restoreOutcome == RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded) {
            // The Scenario 1 success path leaves `pre_restore_backup.db` on disk for the user's undo slot,
            // and Room will open the freshly-restored db on first DAO access. Scenario 2 has nothing to
            // add — skip.
            return
        }
        // Scenario 1 was a no-op (no restore in progress). Run Scenario 2.
        runBlocking {
            appGraph.startupMigrationCoordinator.checkAndRouteOrProceed()
        }
        // The result is cached on `StartupMigrationCoordinator.lastDecision`; MainActivity reads it on its
        // own onCreate to decide whether to finish + launch RecoveryActivity.
    }

    private fun cleanupOrphanedImageTempFiles() {
        val imageStorage = appGraph.imageStorage
        // Fire-and-forget on a one-shot IO coroutine — clearing temp files left behind by killed
        // camera-capture flows is best-effort.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            imageStorage.cleanupTempFiles()
        }
    }

    /**
     * Eagerly construct the cross-feature dialog reactor so its `init { observer.observeUserActions()
     * ...launchIn(scope) }` registers a subscriber on the SharedFlow BEFORE MainActivity.onCreate runs.
     * Lazy construction would mean the first user dispatch fires on zero subscribers and is lost. The
     * return value is intentionally discarded — the side-effect of construction is what we want.
     */
    private fun bootstrapAppDialogObserver() {
        appGraph.recoveryBootstrap
    }
}
