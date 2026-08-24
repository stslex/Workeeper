// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import android.app.ActivityManager
import android.app.Application
import androidx.work.Configuration
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.app.common.di.AppRootDepsHolder
import io.github.stslex.workeeper.core.core.images.buildImageStorage
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.utils.CommonExt
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDeps
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDepsHolder
import io.github.stslex.workeeper.core.data.backup.worker.MetroWorkerFactory
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.buildAppDatabase
import io.github.stslex.workeeper.core.data.database.refreshQueryPlannerStatistics
import io.github.stslex.workeeper.core.ui.mvi.di.AppDepsHolder
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.di.AppGraphOwner
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDeps
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDepsHolder
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.StartupCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The process [Application] base, Hilt-free (App-Scope Collapse Step 6 — the cut). Holds the Metro
 * app-scope [AppGraph] for the whole process and exposes it through the interface seams every consumer
 * reads: [AppGraphOwner] (in-module: `MainActivity` and other `:app:app` readers), [AppDepsHolder]
 * (the 13 feature-side readers via `context.appDeps<XxxGraph.Factory>()`), and the typed
 * [RecoveryDepsHolder] / [BackupWorkerDepsHolder] (the two framework readers that must not depend on
 * `core:ui:mvi`).
 *
 * Two `Context`-cast seams used to sit alongside them and are both gone. `AppDialogInternalsHolder`
 * handed app-dialogs/impl its own app-scoped singletons because no dep interface could name those
 * impl-owned types; `AppDialogPublisherHolder` exposed the publisher to cross-module producers. The
 * contributed extensions inherit both from [AppGraph], and producers (settings / recovery) take
 * `AppDialogPublisher` as an ordinary constructor dep.
 */
abstract class BaseApplication :
    Application(),
    Configuration.Provider,
    AppGraphOwner,
    AppDepsHolder,
    RecoveryDepsHolder,
    BackupWorkerDepsHolder,
    AppRootDepsHolder {

    abstract val isDebugLoggingAllow: Boolean

    /**
     * Held rather than inlined into [appGraph] only so [warmQueryPlanner] can reach it: the graph
     * takes it as a `create()` bound instance and exposes no accessor for it, and giving it one
     * would widen the graph's surface for a startup chore. Still the same single cold
     * `Room.databaseBuilder(...).build()` — constructing it opens no SQLite file.
     */
    private val appDatabase: AppDatabase by lazy { buildAppDatabase(applicationContext) }

    /**
     * The Metro app-scope graph, held for the whole process. `by lazy` so it is created on first access.
     * In production that first access is DURING `onCreate` — [onCreateGraphBootstrap] →
     * [handleRecoveryPreflightChain] reads `appGraph` to run the recovery/startup-migration pre-flight;
     * only the test override defers/skips that, so under test the graph is created on a later access.
     * Constructs the two `create()` roots directly, Hilt-free: [appDatabase] (a cold
     * `Room.databaseBuilder(...).build()` — no SQLite open, so `RecoveryActivity`'s Room-free bootstrap
     * safety holds) and [ImageStorageImpl] via [buildImageStorage]; `@IODispatcher` is `Dispatchers.IO`
     * directly (the graph is under
     * construction — reading its own dispatcher would cycle; `Dispatchers.IO` is the identical stateless
     * process-singleton the graph's accessor returns).
     */
    @Suppress("EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR_ERROR", "EXPOSED_PROPERTY_TYPE")
    override val appGraph: AppGraph by lazy {
        buildAppGraph(
            applicationContext = applicationContext,
            appDatabase = appDatabase,
            imageStorage = buildImageStorage(applicationContext, Dispatchers.IO),
        )
    }

    // Feature-side readers acquire their own contributed `XxxGraph.Factory` via `context.appDeps<T>()`.
    // Every `@ContributesTo(AppScope::class)` extension factory is merged into `appGraph`, so the
    // accessor's `as T` cast is safe by construction.
    override fun appDeps(): Any = appGraph

    // God-object split (typed point-acquisition): the framework-instantiated RecoveryActivity reads
    // its 2 deps through this typed holder instead of `context.appDeps<T>()` — it uses no core:ui:mvi
    // symbols, so it must not gain a parasitic mvi edge just to reach the mvi-homed accessor. `appGraph`
    // implements RecoveryDeps, so returning it typed as RecoveryDeps is a compile-checked upcast.
    override fun recoveryDeps(): RecoveryDeps = appGraph

    // God-object split (typed point-acquisition): MetroWorkerFactory (core:data:backup:worker, DATA
    // layer) reads its 6 deps through this typed holder — it MUST NOT depend on core:ui:mvi (data→ui
    // inversion), so it cannot use `context.appDeps<T>()`. `appGraph` implements BackupWorkerDeps, so
    // returning it typed as BackupWorkerDeps is a compile-checked upcast.
    override fun backupWorkerDeps(): BackupWorkerDeps = appGraph

    // Typed point-acquisition, same shape as the two above: App() lives in app:common, which
    // `:app:app` depends on — so it sits below the graph and cannot name `AppGraph` or
    // `AppGraphOwner` at all. `appGraph` implements AppRootDeps, so returning it typed as AppRootDeps
    // is a compile-checked upcast, and the `as AppRootDepsHolder` cast at the App() call site is safe
    // by construction because this class implements the holder.
    override fun appRootDeps(): AppRootDeps = appGraph

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(MetroWorkerFactory(this))
            .build()

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlyticsHolder.initialize()
        Log.isLogging = isDebugLoggingAllow
        CommonExt.isTraceExecutionEnabled = isDebugLoggingAllow
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
        warmQueryPlanner()
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
     * Refreshes SQLite's planner statistics, off the main thread and best-effort.
     *
     * Without them the planner drives the live-workout PR read from `session_table.state` — an index
     * over two distinct values — and walks every finished session the user has ever logged instead
     * of the thirteen exercises actually asked about. Measured, and the fix changes no SQL: see
     * `refreshQueryPlannerStatistics`.
     *
     * **After the pre-flight chain and never on the recovery path.** `ANALYZE` writes `sqlite_stat1`,
     * which means opening the database — the one thing a `RouteToRecovery` start must not do, since
     * that decision exists precisely because the schema is not in a state Room can open. The
     * pre-flight has already cached its decision by the time this runs.
     *
     * **Fire-and-forget, and deliberately NOT coordinated with the first read that benefits.** A
     * launch with no statistics yet can serve a query before this finishes, on the plan the
     * statistics exist to replace; making that read wait would cost far more than the plan does,
     * and the statistics are durable, so the exposure is that one launch. Both sides of the trade
     * are measured in `documentation/feature-specs/v3-redesign-spec.md` §27, "A BAD QUERY PLAN CAN
     * BE A MISSING FACT".
     *
     * **And never on a low-RAM device.** Overlapping this write with the first screen's reads is
     * free only under WAL, where readers do not block on a writer. Room's default journal mode is
     * `AUTOMATIC`, and `AUTOMATIC` resolves to `TRUNCATE` exactly on `isLowRamDevice` hardware — so
     * on those devices the overlap is not free and the safety argument does not hold. The cost of
     * this line is that the planner keeps guessing there; the alternative, forcing
     * `WRITE_AHEAD_LOGGING` in `AppDatabaseFactory`, would override a platform-aware Room default
     * for every query in the app to buy one startup chore, on the devices least able to afford
     * WAL's memory. Skipping is the smaller trade, and it is recorded as a trade.
     *
     * Caught rather than propagated, unlike its sibling above: this one touches the database, and a
     * corrupt file is exactly the condition under which the rest of startup still has work to do.
     * A throw here would take the process down on the launch that most needs to reach recovery.
     */
    private fun warmQueryPlanner() {
        if (appGraph.startupMigrationCoordinator.lastDecision is StartupCheck.RouteToRecovery) return
        if (getSystemService(ActivityManager::class.java)?.isLowRamDevice == true) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { refreshQueryPlannerStatistics(appDatabase) }
                .onFailure { error -> Log.e(error) }
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
