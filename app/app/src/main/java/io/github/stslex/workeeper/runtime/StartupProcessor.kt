// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.refreshQueryPlannerStatistics
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator.PreflightOutcome
import io.github.stslex.workeeper.feature.recovery.domain.StartupCheck
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Startup sequence of one runtime generation: restore preflight, migration peek, chores, observer
 * arming. See the Phase-5 startup-processor spec.
 */
internal class StartupProcessor(
    private val isLowRamDevice: () -> Boolean,
    private val warmPlanner: suspend (AppDatabase) -> Unit = { refreshQueryPlannerStatistics(it) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Cold-start pass, blocking on the main thread inside `Application.onCreate`; a background
     * preflight would flash MainActivity content before recovery routing decides.
     */
    fun coldStart(
        graph: AppGraph,
        appDatabase: AppDatabase,
        lifetime: AppScopeLifetime,
    ): StartupOutcome {
        val restoreOutcome = runBlocking {
            graph.restoreRecoveryCoordinator.handlePostRestoreLaunch()
        }
        if (restoreOutcome == PreflightOutcome.RestoreRolledBack) {
            // Chores and observer arming are skipped: the caller restarts the process.
            return StartupOutcome.RestartRequired
        }
        if (restoreOutcome == PreflightOutcome.RecoveryRequired) {
            // GUARD: terminal recovery arms nothing DB-bound and shows no main UI.
            return StartupOutcome.RouteToRecovery
        }
        if (restoreOutcome == PreflightOutcome.NoOp) {
            runBlocking {
                graph.startupMigrationCoordinator.checkAndRouteOrProceed()
            }
        }
        armPostPreflight(graph, appDatabase, lifetime)
        return when {
            graph.startupMigrationCoordinator.lastDecision is StartupCheck.RouteToRecovery ->
                StartupOutcome.RouteToRecovery

            else -> StartupOutcome.Proceed
        }
    }

    /**
     * [coldStart]'s stages in the same order, suspending, for candidate generations during an
     * in-process transition. See the Phase-5 startup-processor spec.
     */
    suspend fun preflightAndArm(
        graph: AppGraph,
        appDatabase: AppDatabase,
        lifetime: AppScopeLifetime,
    ): StartupOutcome {
        val restoreOutcome = graph.restoreRecoveryCoordinator.handlePostRestoreLaunch()
        if (restoreOutcome == PreflightOutcome.RestoreRolledBack) {
            return StartupOutcome.RestartRequired
        }
        if (restoreOutcome == PreflightOutcome.RecoveryRequired) {
            // GUARD: terminal recovery arms nothing DB-bound and shows no main UI.
            return StartupOutcome.RouteToRecovery
        }
        if (restoreOutcome == PreflightOutcome.NoOp) {
            graph.startupMigrationCoordinator.checkAndRouteOrProceed()
        }
        armPostPreflight(graph, appDatabase, lifetime)
        return when {
            graph.startupMigrationCoordinator.lastDecision is StartupCheck.RouteToRecovery ->
                StartupOutcome.RouteToRecovery

            else -> StartupOutcome.Proceed
        }
    }

    /**
     * Chores then observer arming, in order: cleanup → planner → observer. Not reached for a
     * rolled-back restore or for terminal recovery.
     */
    private fun armPostPreflight(
        graph: AppGraph,
        appDatabase: AppDatabase,
        lifetime: AppScopeLifetime,
    ) {
        cleanupOrphanedImageTempFiles(graph, lifetime)
        warmQueryPlanner(graph, appDatabase, lifetime)
        armDialogObserver(graph)
    }

    private fun cleanupOrphanedImageTempFiles(graph: AppGraph, lifetime: AppScopeLifetime) {
        val imageStorage = graph.imageStorage
        lifetime.childScope(ioDispatcher).launch {
            imageStorage.cleanupTempFiles()
        }
    }

    private fun warmQueryPlanner(
        graph: AppGraph,
        appDatabase: AppDatabase,
        lifetime: AppScopeLifetime,
    ) {
        if (graph.startupMigrationCoordinator.lastDecision is StartupCheck.RouteToRecovery) return
        if (isLowRamDevice()) return
        lifetime.childScope(ioDispatcher).launch {
            runCatching { warmPlanner(appDatabase) }
                .onFailure { error -> Log.e(error) }
        }
    }

    /**
     * Eagerly constructs the dialog reactor so its subscriber registers on the replay-0 choice
     * bus before the first Activity; construction is the side-effect.
     */
    private fun armDialogObserver(graph: AppGraph) {
        graph.recoveryBootstrap
    }
}
