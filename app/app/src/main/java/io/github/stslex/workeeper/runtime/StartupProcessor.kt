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
 * The startup sequence of one runtime generation, extracted from
 * `BaseApplication.onCreateGraphBootstrap` as an order-preserving refactor (Phase 5, spec §8.3).
 * The stages and their guarantees are UNCHANGED from the measured inventory (spec §2):
 *
 *  1. **Scenario 1** (post-restart restore verification) — blocking. `RestoreRolledBack` →
 *     [StartupOutcome.RestartRequired] (caller restarts; nothing else runs, matching the old
 *     non-returning `restartApp()`). `RestoreSucceeded` → Scenario 2 is SKIPPED.
 *  2. **Scenario 2** (startup-migration peek, Room-free) — blocking, only after a Scenario-1
 *     no-op. Its decision stays cached on `StartupMigrationCoordinator.lastDecision`; the
 *     RouteToRecovery routing itself remains MainActivity's read, exactly as before.
 *  3. **Chores**, fire-and-forget on the generation lifetime (no anonymous scopes): image
 *     temp-file cleanup (best-effort, unguarded body — same failure policy as before) and the
 *     query-planner warm-up (never on `RouteToRecovery`, never on low-RAM devices, `runCatching`
 *     + log — the full rationale lives in spec §2 stage 4 and the pre-extraction KDocs, preserved
 *     in git history at `BaseApplication.kt@1845d7c9:189-229`).
 *  4. **Dialog-observer arming** — the eager `recoveryBootstrap` read that registers the
 *     choice-bus subscriber BEFORE any `MainActivity.onCreate` (replay-0 bus; a lazy read drops
 *     the first dispatch).
 *
 * Both `runBlocking` boundaries are load-bearing and deliberate: dispatching the preflight on a
 * background coroutine would briefly show MainActivity content before recovery routing decides.
 *
 * The three constructor seams exist for the JVM tests ONLY — production passes the real values
 * (`BaseApplication` wires `isLowRamDevice` from `ActivityManager`, and the defaults are the
 * production planner + IO dispatcher).
 */
internal class StartupProcessor(
    private val isLowRamDevice: () -> Boolean,
    private val warmPlanner: suspend (AppDatabase) -> Unit = { refreshQueryPlannerStatistics(it) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Cold-start pass, blocking (main thread, inside `Application.onCreate`). Returns the typed
     * outcome; the caller maps [StartupOutcome.RestartRequired] to the process restart.
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
            // The caller restarts the process; chores and observer arming are intentionally
            // skipped — identical to the pre-extraction flow, where the non-returning
            // restartApp() prevented them from ever running.
            return StartupOutcome.RestartRequired
        }
        if (restoreOutcome == PreflightOutcome.NoOp) {
            // Scenario 1 found no restore in progress — run Scenario 2. (RestoreSucceeded skips
            // it: the freshly-restored db was already verified by the Scenario-1 open.)
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
     * The same stages as [coldStart] in the same order, in suspend form for CANDIDATE
     * generations during an in-process transition (spec §8.3 "replacement preflight" / §8.4
     * Preflight state). No `runBlocking`: the transition machine is already off the main thread,
     * and the cold-start blocking rationale (content must not flash before routing) does not
     * apply — the UI is showing the Transitioning interstitial. The small duplication of
     * [coldStart]'s ordering logic is deliberate: sharing one body would either force the
     * suspend path through `runBlocking` or dissolve the two load-bearing cold-start blocking
     * boundaries into an abstraction; fifteen duplicated lines are cheaper than either, and
     * `StartupProcessorTest` pins both orderings.
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
     * Stages 3–4: the two chores and the observer arming, in the pre-extraction order
     * (cleanup → planner → observer). Runs for every non-restart outcome — including
     * `RouteToRecovery`, where only the planner's own guard changes anything.
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
     * Eagerly constructs the cross-feature dialog reactor so its subscriber registers on the
     * choice SharedFlow before the first Activity. The return value is intentionally discarded —
     * construction is the side-effect.
     */
    private fun armDialogObserver(graph: AppGraph) {
        graph.recoveryBootstrap
    }
}
