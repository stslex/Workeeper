// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import android.app.ActivityManager
import android.app.Application
import androidx.work.Configuration
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.app.common.di.AppRootDepsHolder
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.buildImageStorage
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.utils.CommonExt
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDeps
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDepsHolder
import io.github.stslex.workeeper.core.data.backup.worker.MetroWorkerFactory
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.buildAppDatabase
import io.github.stslex.workeeper.core.ui.mvi.di.AppDepsHolder
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.di.AppGraphOwner
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDeps
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDepsHolder
import io.github.stslex.workeeper.runtime.StartupOutcome
import io.github.stslex.workeeper.runtime.StartupProcessor
import kotlinx.coroutines.Dispatchers

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
     * Held rather than inlined into [appGraph] only so [StartupProcessor]'s planner warm-up can
     * reach it: the graph takes it as a `create()` bound instance and exposes no accessor for it,
     * and giving it one would widen the graph's surface for a startup chore. Still the same single
     * cold `Room.databaseBuilder(...).build()` — constructing it opens no SQLite file.
     */
    private val appDatabase: AppDatabase by lazy { buildAppDatabase(applicationContext) }

    /**
     * The generation lifetime (Phase 5, spec §8.2): every app-scoped job and collector — the two
     * startup chores below and the three scope-owning graph singletons — derives its scope from
     * this one root, so the owner of the generation can end them all deterministically. In the
     * process-restart production model this is process-lifetime and never cancelled, which is
     * byte-equivalent to the anonymous scopes it replaces; the runtime host cancels-and-joins it
     * during in-process replacement (Quiescing).
     */
    private val appScopeLifetime = AppScopeLifetime()

    /**
     * The Metro app-scope graph, held for the whole process. `by lazy` so it is created on first access.
     * In production that first access is DURING `onCreate` — [onCreateGraphBootstrap] hands it to
     * [StartupProcessor.coldStart], whose recovery/startup-migration pre-flight reads it; only the
     * test override defers/skips that, so under test the graph is created on a later access.
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
            appScopeLifetime = appScopeLifetime,
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
        val outcome = startupProcessor.coldStart(
            graph = appGraph,
            appDatabase = appDatabase,
            lifetime = appScopeLifetime,
        )
        if (outcome == StartupOutcome.RestartRequired) {
            // Scenario 1 rolled the live db back — restart so the next process rebuilds against
            // the rolled-back file. Never returns (Runtime.exit inside).
            appGraph.restoreRecoveryCoordinator.restartApp()
        }
        // Proceed / RouteToRecovery: the decision is cached on the coordinator; MainActivity
        // reads it in its own onCreate — unchanged from the pre-extraction flow.
    }

    /**
     * The extracted startup sequence (Phase 5, spec §8.3) — an order-preserving refactor of the
     * four inline stages this class used to hold; every stage's ordering, blocking, guard, and
     * failure-policy guarantee is documented on [StartupProcessor]. The one production seam wired
     * here is the low-RAM check, which needs this [android.content.Context].
     */
    private val startupProcessor = StartupProcessor(
        isLowRamDevice = {
            getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        },
    )
}
