// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import java.io.File

/**
 * The live-database replacement transaction, owned by the RUNTIME (Phase 5 R2 —
 * `kmp-phase-5-startup-processor.md` §8.5). Replaces the direct
 * `DatabaseSnapshotProvider.restoreFromSnapshot` / `rollbackToPreRestoreBackup` calls: the
 * snapshot provider no longer closes or swaps the published database independently — closing a
 * Room 3 database is TERMINAL for the object (§7.1, measured on device), so the only party that
 * may do it is the one that owns the generation being ended and can mint its successor.
 *
 * **Submission ownership.** Callers only SUBMIT and await: the transaction runs on the runtime
 * host's own scope, so a caller whose scope the transition itself kills (the Settings restore
 * Store disposed at `Transitioning`; the undo reactor living inside the outgoing generation's
 * lifetime) abandons its await without cancelling or stranding the transaction.
 *
 * **Hooks.** [restoreFromSnapshot]'s `beforeMutation` runs INSIDE the transaction after
 * validation and before any quiescing/close — the place for crash-safety markers
 * (`restore_in_progress`), ordered so no other transition can interleave between the marker
 * write and the swap. [rollbackToPreRestoreBackup]'s `onCommitted` runs on the TRANSACTION's
 * coroutine after the swap committed (and, in-process, after the successor generation is
 * published), before awaiters complete — the place for post-commit state/dialog effects that
 * must survive the initiator's death. Hooks must touch only process-lifetime state (DataStore);
 * their failures are contained and logged.
 *
 * **Phase-aware results.** Callers MUST branch on [DatabaseReplacementResult]: only
 * [DatabaseReplacementResult.RejectedBeforeMutation] permits pre-swap cleanup (deleting the
 * preserved rollback file, clearing the in-progress marker). After the point of no return the
 * recovery assets belong to the runtime's failure ladder and the persisted-state protocol —
 * deleting them on [DatabaseReplacementResult.FailedAfterMutation] would destroy the only
 * recovery path.
 *
 * androidMain deliberately (the signature is `java.io.File`; precedent: [BackupStorage]).
 * Implemented by the application runtime host and handed into the app graph as a `create()`
 * bound-instance root — the graph cannot own the thing that replaces it.
 */
interface DatabaseReplacement {

    /**
     * Validate [source] (SQLite magic, schema-version gates — same checks, same order, same
     * error taxonomy as the pre-split provider method), run [beforeMutation], then replace the
     * live database file. [source] is NOT consumed — the caller deletes its temp file, as before.
     */
    suspend fun restoreFromSnapshot(
        source: File,
        beforeMutation: suspend () -> Unit = {},
    ): DatabaseReplacementResult

    /**
     * Replace the live database file from the preserved `pre_restore_backup.db`, consuming it on
     * success — the Scenario-1 auto-rollback and the undo flow, unchanged semantics.
     * [onCommitted] carries the caller's post-commit effects (see class KDoc).
     */
    suspend fun rollbackToPreRestoreBackup(
        onCommitted: suspend () -> Unit = {},
    ): DatabaseReplacementResult
}

/** The phase-aware outcome of one replacement transaction (see [DatabaseReplacement]'s KDoc). */
sealed interface DatabaseReplacementResult {

    /** The swap committed (and, in-process, a successor generation is serving). */
    data object Committed : DatabaseReplacementResult

    /**
     * The transaction ended BEFORE anything was mutated — the live file, the database handle
     * (or its close completed cleanly-nothing-after), and all recovery assets are untouched;
     * the previous generation keeps serving. Pre-swap cleanup is SAFE on this outcome.
     */
    data class RejectedBeforeMutation(val error: BackupError) : DatabaseReplacementResult

    /**
     * The point of no return was crossed (database closed and/or file mutated) and the
     * transaction could not complete. Recovery assets and markers are the RUNTIME's — callers
     * must never delete them on this outcome.
     */
    data class FailedAfterMutation(val error: BackupError) : DatabaseReplacementResult

    /** No generation is serving; the runtime is terminal and recovery must be surfaced. */
    data object FatalNoGeneration : DatabaseReplacementResult
}
