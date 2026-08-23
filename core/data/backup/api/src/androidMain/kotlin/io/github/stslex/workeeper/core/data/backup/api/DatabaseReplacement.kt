// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import java.io.File

/**
 * The live-database replacement transaction, owned by the RUNTIME (Phase 5 R2 —
 * `kmp-phase-5-startup-processor.md` §8.5). Closing a Room 3 database is TERMINAL for the object
 * (§7.1, measured on device), so the only party that may do it is the one that owns the
 * generation being ended and can mint its successor.
 *
 * **Submission ownership + source ownership.** Callers only SUBMIT and await. For
 * [restoreFromSnapshot], ownership of [source] TRANSFERS to the runtime AT SUBMISSION: the file
 * is atomically staged into a runtime-owned location before the submitting call can be
 * cancelled, and the runtime deletes the staged copy on EVERY terminal outcome — a cancelled
 * caller's `finally { tempFile.delete() }` can never destroy a file the host transaction still
 * needs.
 *
 * **Typed transaction effects.** All caller compensation rides ONE [DatabaseReplacementEffects]
 * object, submitted with the operation and executed by the runtime on the TRANSACTION's
 * coroutine at exactly the matching protocol phase (§8.4): preparation in
 * [DatabaseReplacementEffects.onBeforeMutation], and exactly one terminal method per
 * transaction. Effects must touch only process-lifetime state (DataStore, dialog flags) and be
 * idempotent. A failing [DatabaseReplacementEffects.onCommitted] is NEVER swallowed into a clean
 * result — it surfaces as [DatabaseReplacementResult.Committed.effectsError].
 *
 * **Result semantics (spec §8.4).** [DatabaseReplacementResult.Committed] means the REQUESTED
 * operation committed — nothing else. A restore whose swap failed after the point of no return
 * but whose automatic rollback restored a serving generation returns
 * [DatabaseReplacementResult.RecoveredByRollback]: the data is the PRE-operation data, and
 * callers must produce restore-FAILURE semantics (never a success dialog, never an undo offer).
 * An explicitly REQUESTED rollback that commits returns Committed.
 *
 * androidMain deliberately (the signature is `java.io.File`; precedent: [BackupStorage]).
 * Implemented by the application runtime host and handed into the app graph as a `create()`
 * bound-instance root — the graph cannot own the thing that replaces it.
 */
interface DatabaseReplacement {

    /**
     * Validate the staged copy of [source] (SQLite magic, schema-version gates — same checks,
     * same order, same error taxonomy as the pre-split provider method), run
     * [DatabaseReplacementEffects.onBeforeMutation], then replace the live database file.
     * [source] is consumed by staging at submission; the caller's temp-file cleanup becomes a
     * no-op and MUST NOT be relied on.
     */
    suspend fun restoreFromSnapshot(
        source: File,
        effects: DatabaseReplacementEffects = DatabaseReplacementEffects.None,
    ): DatabaseReplacementResult

    /**
     * Replace the live database file from the preserved `pre_restore_backup.db`, consuming it on
     * success — the Scenario-1 auto-rollback and the undo flow, unchanged semantics.
     */
    suspend fun rollbackToPreRestoreBackup(
        /**
         * The snapshot to roll back onto — the durable journal's per-attempt reservation when a
         * recovering launch knows which file holds the true pre-attempt database. `null` uses
         * the canonical `pre_restore_backup.db` undo slot.
         */
        sourcePath: String? = null,
        effects: DatabaseReplacementEffects = DatabaseReplacementEffects.None,
    ): DatabaseReplacementResult
}

/**
 * The caller's compensation contract for ONE replacement transaction — the typed replacement
 * for ad-hoc callback lambdas (spec §8.5). Every method runs ON the transaction's coroutine
 * (the runtime host's never-cancelled scope), under the transition mutex, so the effects
 * survive the initiator's death and no other transition can interleave with them.
 *
 * The runtime invokes [onBeforeMutation] once (after validation, before anything irreversible)
 * and then EXACTLY ONE terminal method matching the transaction's outcome. Implementations must
 * be idempotent and confined to process-lifetime state. Methods default to no-ops because every
 * effect is caller-domain-optional; the runtime's own protocol NEVER depends on them running.
 */
interface DatabaseReplacementEffects {

    /**
     * The identity of THIS attempt — the durable journal key every effect writes under
     * (spec §8.5a). The runtime hands it back on every callback so a superseded attempt can
     * never advance or clear the live one's bookkeeping.
     */
    val attemptId: String

    /**
     * Pre-mutation preparation, executed with the rollback snapshot ALREADY reserved by the
     * runtime at [rollbackSnapshotPath]: persist the attempt as
     * [io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt.Phase.Prepared]
     * (identity + context + that path) in ONE atomic write. A throw rejects the transaction
     * before anything irreversible — including when another unresolved attempt still owns the
     * journal slot, which is exactly how a second restore is refused rather than allowed to
     * inherit the first's bookkeeping.
     */
    suspend fun onBeforeMutation(rollbackSnapshotPath: String) {}

    /**
     * The requested file mutation COMMITTED and the reserved snapshot was promoted onto the
     * canonical undo slot. Record the durable
     * [io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt.Phase.Committed]
     * transition HERE — this is the only point at which a later cold start may conclude the
     * operation succeeded.
     *
     * The runtime calls it before consuming any rollback asset, so a throw (bookkeeping failed
     * while the mutation stands) leaves the journal at `Prepared` and every recovery file in
     * place: the next launch conservatively rolls back rather than claiming a success it cannot
     * prove. The failure is surfaced on the transaction's result, never swallowed.
     */
    suspend fun onMutationCommitted() {}

    /**
     * Terminal: the transaction ended with NOTHING irreversible done. Compensate the
     * preparation here (delete a stale preserved snapshot, clear the in-progress marker) —
     * pre-mutation cleanup is legal ONLY on this outcome.
     */
    suspend fun onRejectedBeforeMutation(error: BackupError) {}

    /** Terminal: the REQUESTED operation committed. */
    suspend fun onCommitted() {}

    /**
     * Terminal: the requested operation failed after the point of no return and the runtime's
     * automatic rollback restored a serving generation on the PRE-operation data. Compensate
     * the preparation (the in-progress marker is stale — recovery already happened) and surface
     * FAILURE semantics; the preserved rollback file was consumed by the recovery.
     */
    suspend fun onRecoveredByRollback(error: BackupError) {}

    /**
     * Terminal: post-PONR failure with NO in-process recovery performed. The runtime preserved
     * every recovery file and marker — record durable state for the next launch here (e.g. the
     * restore journal's interrupted-mutation flag); never delete anything.
     */
    suspend fun onFailedAfterMutation(error: BackupError) {}

    /** Terminal: the runtime is fatal. Assets are preserved; record durable state only. */
    suspend fun onFatal() {}

    /** The explicit no-effects object for operations with no caller compensation. */
    object None : DatabaseReplacementEffects {
        override val attemptId: String = "no-effects"
    }
}

/** The phase-aware outcome of one replacement transaction (see [DatabaseReplacement]'s KDoc). */
sealed interface DatabaseReplacementResult {

    /**
     * Non-null when the caller's compensation/bookkeeping effect for THIS outcome failed
     * (spec §8.5a). A terminal effect failure is never merely logged behind a clean-looking
     * result: durable state may not match what the outcome implies, and the caller must treat
     * the operation as needing recovery even when the mutation itself succeeded.
     */
    val effectsError: BackupError?

    /**
     * The REQUESTED operation committed (and, in-process, a successor generation is serving).
     * [effectsError] is non-null when the caller's [DatabaseReplacementEffects.onCommitted]
     * failed — the operation itself is committed, but the caller's post-commit bookkeeping is
     * NOT done and must be surfaced, never treated as a clean commit.
     */
    data class Committed(override val effectsError: BackupError? = null) : DatabaseReplacementResult

    /**
     * The transaction ended BEFORE anything irreversible — the live file, the database handle,
     * the outgoing generation's ViewModelStore/lifetime, and all recovery assets are untouched;
     * the previous generation keeps serving. Pre-swap cleanup ran via
     * [DatabaseReplacementEffects.onRejectedBeforeMutation].
     */
    data class RejectedBeforeMutation(
        val error: BackupError,
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult

    /**
     * The requested restore failed after the point of no return; the runtime's bounded recovery
     * rolled back and a generation is SERVING on the PRE-operation data. This is a restore
     * FAILURE for every caller: never a success dialog, never an undo offer (the preserved file
     * was consumed by the recovery).
     */
    data class RecoveredByRollback(
        val error: BackupError,
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult

    /**
     * The point of no return was crossed and the transaction could not complete; no in-process
     * recovery was performed (the RestartProcess ending). Recovery assets and markers are
     * PRESERVED and belong to the runtime/journal protocol — callers must never delete them.
     */
    data class FailedAfterMutation(
        val error: BackupError,
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult

    /** No generation is serving; the runtime is terminal and recovery must be surfaced. */
    data class FatalNoGeneration(
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult
}
