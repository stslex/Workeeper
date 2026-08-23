// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed state for the restore-recovery flows
 * (`documentation/feature-specs/backup-recovery.md`).
 *
 * Two distinct pieces of state, both surviving process restart:
 *
 * 1. **`restore_in_progress`** — a transient flag set by
 *    `BackupInteractor.restoreLatest` just before the file replace, and
 *    cleared by the post-restart pre-flight in `Application.onCreate` (either
 *    after a successful Room open or after rollback). The presence of this
 *    flag tells the pre-flight that the user just tapped Restore; the
 *    [RestoreInProgressContext] payload feeds Crashlytics keys + the
 *    diagnostic export on failure.
 *
 * 2. **`pre_restore_backup_available`** — a longer-lived flag set after the
 *    post-restart pre-flight verifies the restore succeeded. While `true`,
 *    Settings renders the "Revert last restore" row and the corresponding
 *    `cache/pre_restore_backup.db` snapshot is preserved. Cleared on undo or
 *    when the next Restore overwrites the preserved file.
 */
interface RestoreStateRepository {

    /**
     * Mark that a Restore is in progress and persist the manifest context for
     * the post-restart pre-flight. Replaces any previously persisted context.
     */
    suspend fun markRestoreInProgress(context: RestoreInProgressContext)

    /**
     * Returns the persisted [RestoreInProgressContext] when a restore is in
     * progress, or `null` otherwise.
     *
     * **Read-only.** Callers MUST explicitly invoke [clearRestoreInProgress]
     * after handling the context — typically at the end of the post-restart
     * pre-flight branch that consumed it (success or rollback). Leaving the
     * context set across a normal app session causes the pre-flight to re-run
     * its in-progress logic on every cold start.
     */
    suspend fun getRestoreInProgressContext(): RestoreInProgressContext?

    /**
     * Clears the `restore_in_progress` flag and its context payload — and the
     * [markRestoreMutationInterrupted] journal entry, which is scoped to the
     * same restore attempt. Called after the pre-flight resolves the
     * in-progress restore (either success or rollback).
     */
    suspend fun clearRestoreInProgress()

    /**
     * Durable restore-journal entry (Phase 5 R2, spec §8.4): the restore's file
     * mutation FAILED or ended in an unknown state after the point of no
     * return. While set (together with `restore_in_progress`), the post-restart
     * pre-flight must take the FAILURE path directly — a schema peek could
     * succeed against the untouched OLD file and produce a false
     * "restore succeeded" — and roll back via the preserved snapshot. Written
     * by the restore transaction's effects on the transaction's own coroutine;
     * idempotent; cleared by [clearRestoreInProgress].
     */
    suspend fun markRestoreMutationInterrupted()

    /** Reads the [markRestoreMutationInterrupted] journal entry. */
    suspend fun isRestoreMutationInterrupted(): Boolean

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
