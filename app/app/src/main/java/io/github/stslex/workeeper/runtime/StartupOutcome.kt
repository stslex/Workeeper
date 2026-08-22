// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

/**
 * The typed result of one startup pass (Phase 5, `kmp-phase-5-startup-processor.md` §8.3) — the
 * explicit form of what `BaseApplication.onCreateGraphBootstrap` used to express implicitly
 * through early returns and a non-returning restart call.
 */
internal sealed interface StartupOutcome {

    /** Normal launch: MainActivity composes the app. */
    data object Proceed : StartupOutcome

    /**
     * Scenario 2 decided the schema is not openable: the decision is cached on
     * `StartupMigrationCoordinator.lastDecision` (exactly as before the extraction) and
     * MainActivity reads it to finish itself and launch `RecoveryActivity`.
     */
    data object RouteToRecovery : StartupOutcome

    /**
     * Scenario 1 rolled the live db back (`PreflightOutcome.RestoreRolledBack`): the process must
     * not continue on in-memory state built against the replaced file. The CALLER owns the
     * mechanism — cold start maps this to `RestoreRecoveryCoordinator.restartApp()` (process
     * exit, today's shipped behavior); the in-process replacement machine maps it to its bounded
     * rollback-and-rebuild branch.
     */
    data object RestartRequired : StartupOutcome
}
