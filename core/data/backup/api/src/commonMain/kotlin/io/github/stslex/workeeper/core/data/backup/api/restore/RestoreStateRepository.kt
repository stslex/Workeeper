// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed state for the restore-recovery flows
 * (`documentation/feature-specs/backup-recovery.md`).
 *
 * Two distinct pieces of state, both surviving process restart:
 *
 * 1. **The attempt journal** ([RestoreAttempt], Phase 5 R3 — spec §8.5a) — at most ONE
 *    unresolved database-replacement attempt at a time. Claimed atomically before the point of
 *    no return, advanced to [RestoreAttempt.Phase.Committed] only once the requested file
 *    mutation is durably committed, and cleared only by the attempt that owns it. The
 *    cold-start pre-flight reads it to decide success vs recovery: a
 *    [RestoreAttempt.Phase.Prepared] (or otherwise unknown) attempt NEVER yields a success
 *    verdict, however healthy the live database looks.
 *
 * 2. **`pre_restore_backup_available`** — a longer-lived flag set after the
 *    post-restart pre-flight verifies the restore succeeded. While `true`,
 *    Settings renders the "Revert last restore" row and the corresponding
 *    `cache/pre_restore_backup.db` snapshot is preserved. Cleared on undo or
 *    when the next Restore's snapshot is promoted over it.
 */
interface RestoreStateRepository {

    /**
     * Atomically claims the attempt slot for [attempt] (which must be
     * [RestoreAttempt.Phase.Prepared]) and persists its identity, kind, manifest context and
     * reserved rollback-snapshot path in ONE edit.
     *
     * Returns `false` when a DIFFERENT unresolved attempt already owns the slot — a new
     * replacement must never inherit or overwrite another attempt's bookkeeping. Re-claiming
     * with the SAME id is idempotent and returns `true`.
     */
    suspend fun beginAttempt(attempt: RestoreAttempt): Boolean

    /**
     * Advances the slot to [RestoreAttempt.Phase.Committed] — the durable record that this
     * attempt's requested file mutation COMMITTED. The runtime calls it after the live-file
     * rename returned success and the reserved snapshot was promoted, and before consuming any
     * rollback asset. Returns `false` (and writes nothing) when [attemptId] does not own the
     * slot.
     */
    suspend fun recordAttemptCommitted(attemptId: String): Boolean

    /**
     * Clears the attempt slot — and the legacy flags — iff [attemptId] owns it. Returns `false`
     * without writing when another attempt owns the slot, so a late terminal effect from a
     * superseded attempt can never erase the live one's bookkeeping.
     */
    suspend fun resolveAttempt(attemptId: String): Boolean

    /** The unresolved attempt, or `null` when the slot is free. */
    suspend fun getAttempt(): RestoreAttempt?

    /** Record that `cache/pre_restore_backup.db` is preserved and available for undo. */
    suspend fun markPreRestoreBackupAvailable(originalDataDateEpochMs: Long)

    /** Clear the preserved-backup marker (file deletion is a separate concern). */
    suspend fun clearPreRestoreBackupAvailable()

    /** Reactive view used by Settings to toggle the "Revert last restore" row. */
    fun observePreRestoreBackupAvailable(): Flow<Boolean>

    /**
     * The original-data date passed into `AppDialog.UndoRestoreConfirmation`'s
     * body ("Your data will revert to the state it was in on …"). Equals the
     * `startedAtEpochMs` captured at restore time. Returns `null` when no
     * preserved backup is available.
     */
    suspend fun getPreRestoreOriginalDate(): Long?
}
