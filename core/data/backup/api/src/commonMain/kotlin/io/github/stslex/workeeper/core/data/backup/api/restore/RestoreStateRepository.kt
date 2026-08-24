// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

import kotlinx.coroutines.flow.Flow

/** Persistent attempt journal and canonical-undo availability state. */
interface RestoreStateRepository {

    /** Atomically claims the slot; `false` when a DIFFERENT unresolved attempt owns it. */
    suspend fun beginAttempt(attempt: RestoreAttempt): Boolean

    /** Advances the slot to [RestoreAttempt.Phase.Committed]; `false` unless [attemptId] owns. */
    suspend fun recordAttemptCommitted(attemptId: String): Boolean

    /** Clears the slot and the legacy flags iff [attemptId] owns it, never for a superseded one. */
    suspend fun resolveAttempt(attemptId: String): Boolean

    /** The unresolved attempt, or `null` when the slot is free. */
    suspend fun getAttempt(): RestoreAttempt?

    /** Record that `cache/pre_restore_backup.db` is preserved and available for undo. */
    suspend fun markPreRestoreBackupAvailable(originalDataDateEpochMs: Long)

    /** Clear the preserved-backup marker (file deletion is a separate concern). */
    suspend fun clearPreRestoreBackupAvailable()

    /** Reactive view used by Settings to toggle the "Revert last restore" row. */
    fun observePreRestoreBackupAvailable(): Flow<Boolean>

    /** Original-data date for `AppDialog.UndoRestoreConfirmation`; `null` when none preserved. */
    suspend fun getPreRestoreOriginalDate(): Long?
}
