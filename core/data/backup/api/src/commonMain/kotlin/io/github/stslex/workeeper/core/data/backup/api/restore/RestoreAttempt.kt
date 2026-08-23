// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

/**
 * One serialized database-replacement ATTEMPT, persisted across process death (Phase 5 R3,
 * `kmp-phase-5-startup-processor.md` §8.5a). Replaces the two independent booleans
 * (`restore_in_progress` + `restore_mutation_interrupted`) whose gap produced a false
 * "restore succeeded": a process death after the close began but before the terminal effects
 * left the OLD, still-valid database on disk with no interrupted marker, so the cold-start
 * schema peek succeeded and published `RestoreSuccess` for a restore that never happened.
 *
 * The attempt slot holds AT MOST ONE unresolved attempt. Everything belonging to that attempt —
 * its identity, its manifest context, and the path of the rollback snapshot reserved for it —
 * is written in ONE atomic DataStore edit before the point of no return, and cleared in one
 * atomic edit when the attempt resolves. A different attempt can neither read nor clear it.
 */
data class RestoreAttempt(
    /** Unique per attempt; only this id may advance or resolve the slot. */
    val id: String,
    val kind: Kind,
    val phase: Phase,
    /**
     * The manifest context of a [Kind.Restore] attempt (Crashlytics keys + the undo UI's
     * "your data will revert to …" date). Null for [Kind.Rollback] attempts.
     */
    val context: RestoreInProgressContext?,
    /**
     * Absolute path of the rollback snapshot RESERVED for this attempt, when the runtime took
     * one. Recovery prefers it over the canonical undo slot: between the live-file mutation and
     * the snapshot's promotion to the canonical slot, this reservation is the only file that
     * holds the true pre-attempt database.
     */
    val rollbackSnapshotPath: String?,
) {

    /** Which operation the attempt performs — they recover differently. */
    enum class Kind {
        /** A user-requested restore from a downloaded backup. */
        Restore,

        /** A rollback onto the preserved pre-restore snapshot (undo, or scenario-1 recovery). */
        Rollback,
    }

    /**
     * How far the attempt is durably known to have progressed. The ONLY phase that permits a
     * success verdict is [Committed]; [Prepared] means "the outcome is unknown", which the
     * cold-start pre-flight must treat as a failure needing recovery — never as a success,
     * however healthy the live database looks.
     */
    enum class Phase {
        /**
         * The attempt claimed the slot and may have started mutating. Written BEFORE anything
         * irreversible; a crash anywhere between here and [Committed] leaves this value.
         */
        Prepared,

        /**
         * The requested file mutation COMMITTED (the live-file rename returned success) and the
         * rollback snapshot was promoted. What remains is the verification/preflight the next
         * launch performs.
         */
        Committed,
    }
}
