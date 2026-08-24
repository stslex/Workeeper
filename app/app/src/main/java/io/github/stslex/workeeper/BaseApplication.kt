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
 * Process [Application] base. Holds the Metro app-scope [AppGraph] and exposes it through the
 * holder seams its consumers read. See documentation/architecture.md.
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

    /** Process-owned runtime, built lazily at the startup boundary. */
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
                // Runtime advances this only for committed handovers.
                advanceSnackbarGeneration = { SnackbarManager.advanceGenerationEpoch() },
            ),
        )
    }

    /** Current generation graph; accessors must not capture it across replacement. */
    @Suppress("EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR_ERROR", "EXPOSED_PROPERTY_TYPE")
    override val appGraph: AppGraph
        get() = appRuntime.currentGeneration.graph

    override val appUiPhases: StateFlow<AppUiPhase>
        get() = appRuntime.uiPhases

    override fun admitUiGeneration(id: Int): AppUiAdmissionToken? = appRuntime.admitUiGeneration(id)

    override fun releaseUiGeneration(token: AppUiAdmissionToken) =
        appRuntime.releaseUiGeneration(token)

    // Every `@ContributesTo(AppScope::class)` factory is merged in, so a caller's `as T` is safe.
    override fun appDeps(): Any = appGraph

    // Typed seam: RecoveryActivity must not gain a parasitic `core:ui:mvi` edge.
    override fun recoveryDeps(): RecoveryDeps = appGraph

    override suspend fun awaitBackupWorkLease(): BackupWorkLease = appRuntime.awaitBackupWorkLease()

    // Typed seam: `App()` lives in app:common, below the graph, and cannot name [AppGraph].
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
     * The graph-touching half of [onCreate]. Overridable so the androidTest `TestApplication` can
     * no-op it and install a per-test graph before any graph read.
     */
    protected open fun onCreateGraphBootstrap() {
        // First read builds and publishes generation 1.
        val generation = appRuntime.currentGeneration
        val outcome = startupProcessor.coldStart(
            graph = generation.graph,
            appDatabase = generation.database,
            lifetime = generation.lifetime,
        )
        if (outcome == StartupOutcome.RestartRequired) {
            // Never returns: restarts so the next process rebuilds against the rolled-back file.
            appGraph.restoreRecoveryCoordinator.restartApp()
        }
        // Proceed / RouteToRecovery is cached on the coordinator; MainActivity reads it.
    }

    /** The startup sequence; the low-RAM seam is wired here because it needs the Context. */
    private val startupProcessor = StartupProcessor(
        isLowRamDevice = {
            getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        },
    )
}
