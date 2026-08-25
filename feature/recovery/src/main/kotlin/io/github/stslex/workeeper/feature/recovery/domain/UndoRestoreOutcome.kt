// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

/**
 * Outcome of [RestoreRecoveryCoordinator.performUndoRestore].
 */
internal sealed interface UndoRestoreOutcome {

    /** Exact rollback commit bookkeeping is durable; verified finalization follows on restart. */
    data object Succeeded : UndoRestoreOutcome

    /** The confirmation named an older pointer; no current owner state was changed. */
    data object NotCurrent : UndoRestoreOutcome

    /** Same-install pointer/source truth is broken; restart must route to recovery. */
    data object RecoveryRequired : UndoRestoreOutcome

    /** The mutation failed without a durable terminal; persisted ownership remains authoritative. */
    data object IoFailure : UndoRestoreOutcome
}
