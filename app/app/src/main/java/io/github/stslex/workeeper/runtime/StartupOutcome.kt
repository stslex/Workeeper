// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

/** Typed result of one startup pass. */
internal sealed interface StartupOutcome {

    /** Normal launch: MainActivity composes the app. */
    data object Proceed : StartupOutcome

    /** Scenario 1 or 2 requires the DB-free recovery route. */
    data object RouteToRecovery : StartupOutcome

    /** A rollback replaced the live file; the caller must rebuild or restart. */
    data object RestartRequired : StartupOutcome

    /** Candidate DB verified, but mandatory owner/pointer finalization is not durable. */
    data object FinalizationPending : StartupOutcome
}
