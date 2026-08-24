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
    /** Terminally refuses DB-bound worker admission; driven only from [coldStart]. */
    private val sealWorkerAdmission: () -> Unit = {},
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
        val outcome = when (nextStep(restoreOutcome)) {
            PostPreflightStep.Restart -> StartupOutcome.RestartRequired
            PostPreflightStep.TerminalRecovery -> StartupOutcome.RouteToRecovery
            PostPreflightStep.PeekThenArm -> {
                runBlocking { graph.startupMigrationCoordinator.checkAndRouteOrProceed() }
                armAndClassify(graph, appDatabase, lifetime)
            }

            PostPreflightStep.ArmOnly -> armAndClassify(graph, appDatabase, lifetime)
        }
        // A recovery-routed process keeps running; without this a persisted BackupWorker would
        // still bind a lease over the file this launch declared unprovable. See spec §8.5b.
        if (outcome == StartupOutcome.RouteToRecovery) sealWorkerAdmission()
        return outcome
    }

    /**
     * [coldStart]'s stages in the same order, suspending, for candidate generations during an
     * in-process transition. See the Phase-5 startup-processor spec.
     *
     * GUARD: never seals worker admission — a candidate's `RouteToRecovery` aborts back to a
     * HEALTHY outgoing generation, whose auto-backup must keep running.
     */
    suspend fun preflightAndArm(
        graph: AppGraph,
        appDatabase: AppDatabase,
        lifetime: AppScopeLifetime,
    ): StartupOutcome {
        val restoreOutcome = graph.restoreRecoveryCoordinator.handlePostRestoreLaunch()
        return when (nextStep(restoreOutcome)) {
            PostPreflightStep.Restart -> StartupOutcome.RestartRequired
            PostPreflightStep.TerminalRecovery -> StartupOutcome.RouteToRecovery
            PostPreflightStep.PeekThenArm -> {
                graph.startupMigrationCoordinator.checkAndRouteOrProceed()
                armAndClassify(graph, appDatabase, lifetime)
            }

            PostPreflightStep.ArmOnly -> armAndClassify(graph, appDatabase, lifetime)
        }
    }

    /**
     * Skip the schema peek only when THIS launch already proved the live file openable.
     * Exhaustive by construction: a sixth [PreflightOutcome] must pick a step. See spec §8.5b.
     */
    private fun nextStep(outcome: PreflightOutcome): PostPreflightStep = when (outcome) {
        PreflightOutcome.RestoreRolledBack -> PostPreflightStep.Restart
        PreflightOutcome.RecoveryRequired -> PostPreflightStep.TerminalRecovery

        // RecoveryCompleted inherits a live file a rollback replaced in an EARLIER process.
        PreflightOutcome.NoOp,
        PreflightOutcome.RecoveryCompleted,
        -> PostPreflightStep.PeekThenArm

        // currentSchemaVersion() already opened this file through Room, this launch.
        PreflightOutcome.RestoreSucceeded -> PostPreflightStep.ArmOnly
    }

    private fun armAndClassify(
        graph: AppGraph,
        appDatabase: AppDatabase,
        lifetime: AppScopeLifetime,
    ): StartupOutcome {
        armPostPreflight(graph, appDatabase, lifetime)
        return if (graph.startupMigrationCoordinator.lastDecision is StartupCheck.RouteToRecovery) {
            StartupOutcome.RouteToRecovery
        } else {
            StartupOutcome.Proceed
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

    /** What the Scenario-1 verdict licenses this launch to do next. See spec §8.5b. */
    private enum class PostPreflightStep {
        /** The live file changed under an open handle; the caller restarts the process. */
        Restart,

        /** GUARD: terminal recovery arms nothing DB-bound and shows no main UI. */
        TerminalRecovery,

        /** Unproven live file: peek its schema, then arm. */
        PeekThenArm,

        /** Openability already proven this launch; arm without a second peek. */
        ArmOnly,
    }
}
