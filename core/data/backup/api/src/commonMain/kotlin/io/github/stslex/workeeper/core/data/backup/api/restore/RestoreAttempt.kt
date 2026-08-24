// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

/** One persisted replacement attempt. One unresolved owner may advance or resolve its slot. */
data class RestoreAttempt(
    /** Unique per attempt; only this id may advance or resolve the slot. */
    val id: String,
    val kind: Kind,
    val phase: Phase,
    /** Manifest context of a [Kind.Restore] attempt; null for [Kind.Rollback]. */
    val context: RestoreInProgressContext?,
    /** Reserved rollback path; authoritative for this prepared attempt. */
    val rollbackSnapshotPath: String?,
    /** Why a [Kind.Rollback] runs; null for [Kind.Restore]. */
    val rollbackOrigin: RollbackOrigin?,
) {

    /** Which operation the attempt performs — they recover differently. */
    enum class Kind {
        /** A user-requested restore from a downloaded backup. */
        Restore,

        /** A rollback onto the preserved pre-restore snapshot (undo, or scenario-1 recovery). */
        Rollback,
    }

    /** Durable discriminator of a rollback's user-facing terminal. GUARD: names are wire format. */
    enum class RollbackOrigin {
        /** The user's "Revert last restore". */
        UserUndo,

        /** Startup recovery of an unresolved attempt. */
        ScenarioOneRecovery,
    }

    /** [Committed] alone permits success; [Prepared] is unknown and must recover. */
    enum class Phase {
        /** Written before mutation; outcome is unknown until [Committed]. */
        Prepared,

        /** Mutation and commit record are durable; startup may verify success. */
        Committed,
    }
}
