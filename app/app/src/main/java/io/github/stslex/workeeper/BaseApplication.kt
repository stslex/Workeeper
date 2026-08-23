// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import android.app.ActivityManager
import android.app.Application
import androidx.work.Configuration
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.app.common.di.AppRootDepsHolder
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiGenerationsHolder
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.core.images.buildImageStorage
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.utils.CommonExt
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkLease
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDepsHolder
import io.github.stslex.workeeper.core.data.backup.worker.MetroWorkerFactory
import io.github.stslex.workeeper.core.data.database.buildAppDatabase
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.di.AppDepsHolder
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.di.AppGraphOwner
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDeps
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDepsHolder
import io.github.stslex.workeeper.runtime.AppRuntime
import io.github.stslex.workeeper.runtime.RuntimeTransitionPolicy
import io.github.stslex.workeeper.runtime.StartupOutcome
import io.github.stslex.workeeper.runtime.StartupProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

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
    AppRootDepsHolder,
    AppUiGenerationsHolder {

    abstract val isDebugLoggingAllow: Boolean

    /**
     * The application-owned runtime host (Phase 5 R2, spec §8.1) — owns the DB factory, the
     * process [io.github.stslex.workeeper.core.core.images.ImageStorage], and the sequence of
     * runtime generations. Every seam below answers from its published generation. `by lazy` so
     * nothing is built before [onCreateGraphBootstrap]'s first read — preserving the cold-build
     * ordering ([buildAppDatabase] opens no SQLite file; `RecoveryActivity`'s Room-free bootstrap
     * safety holds). `Dispatchers.IO` is passed to [buildImageStorage] directly: the graph is
     * under construction at that point — reading its own dispatcher accessor would cycle, and
     * `Dispatchers.IO` is the identical stateless process-singleton the accessor returns.
     */
    private val appRuntime: AppRuntime by lazy {
        AppRuntime(
            applicationContext = applicationContext,
            dbFactory = ::buildAppDatabase,
            imageStorageFactory = { context -> buildImageStorage(context, Dispatchers.IO) },
            graphFactory = ::buildAppGraph,
            preflight = { generation ->
                startupProcessor.preflightAndArm(
                    graph = generation.graph,
                    appDatabase = generation.database,
                    lifetime = generation.lifetime,
                )
            },
            policy = RuntimeTransitionPolicy(
                fenceSnackbarResolves = { SnackbarManager.fenceResolves() },
                unfenceSnackbarResolves = { SnackbarManager.unfenceResolves() },
                pendingSnackbarCount = { SnackbarManager.pendingModelCount },
                // COMMITTED handovers only (the runtime never calls this on abort): models
                // queued under the outgoing generation are discarded at delivery, never
                // executed against the successor (spec §8.4 step 3).
                advanceSnackbarGeneration = { SnackbarManager.advanceGenerationEpoch() },
            ),
        )
    }

    /**
     * The published generation's Metro graph. A `get()` accessor, never a capture: readers that
     * re-read per access (MainActivity, the five holder seams) always observe the CURRENT
     * generation — the atomic-handover half of the R2 replacement invariant.
     */
    @Suppress("EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR_ERROR", "EXPOSED_PROPERTY_TYPE")
    override val appGraph: AppGraph
        get() = appRuntime.currentGeneration.graph

    override val appUiPhases: StateFlow<AppUiPhase>
        get() = appRuntime.uiPhases

    override fun admitUiGeneration(id: Int): AppUiAdmissionToken? = appRuntime.admitUiGeneration(id)

    override fun releaseUiGeneration(token: AppUiAdmissionToken) =
        appRuntime.releaseUiGeneration(token)

    // Feature-side readers acquire their own contributed `XxxGraph.Factory` via `context.appDeps<T>()`.
    // Every `@ContributesTo(AppScope::class)` extension factory is merged into `appGraph`, so the
    // accessor's `as T` cast is safe by construction.
    override fun appDeps(): Any = appGraph

    // God-object split (typed point-acquisition): the framework-instantiated RecoveryActivity reads
    // its 2 deps through this typed holder instead of `context.appDeps<T>()` — it uses no core:ui:mvi
    // symbols, so it must not gain a parasitic mvi edge just to reach the mvi-homed accessor. `appGraph`
    // implements RecoveryDeps, so returning it typed as RecoveryDeps is a compile-checked upcast.
    override fun recoveryDeps(): RecoveryDeps = appGraph

    // First-operation worker admission (Phase 5 R2, spec §8.4): BackupWorker (core:data:backup:
    // worker, DATA layer — it must not depend on core:ui:mvi) binds deps + quiesce-awaited lease
    // in one atomic step as the FIRST operation inside doWork, from the runtime that owns
    // generation transitions. See BackupWorkerDepsHolder's KDoc.
    override suspend fun awaitBackupWorkLease(): BackupWorkLease = appRuntime.awaitBackupWorkLease()

    // Typed point-acquisition, same shape as the two above: App() lives in app:common, which
    // `:app:app` depends on — so it sits below the graph and cannot name `AppGraph` or
    // `AppGraphOwner` at all. `appGraph` implements AppRootDeps, so returning it typed as AppRootDeps
    // is a compile-checked upcast, and the `as AppRootDepsHolder` cast at the App() call site is safe
    // by construction because this class implements the holder.
    override fun appRootDeps(): AppRootDeps = appGraph

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(MetroWorkerFactory())
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
        // First generation read — builds and publishes generation 1 (mutex-free, cold-start rule).
        val generation = appRuntime.currentGeneration
        val outcome = startupProcessor.coldStart(
            graph = generation.graph,
            appDatabase = generation.database,
            lifetime = generation.lifetime,
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
