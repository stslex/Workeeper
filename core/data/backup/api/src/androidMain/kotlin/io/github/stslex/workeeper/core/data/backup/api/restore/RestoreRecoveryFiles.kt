// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File

/** Android owner of strict protocol filenames below `noBackupFilesDir/restore-recovery`. */
interface RestoreRecoveryFiles {

    /** Atomically creates or reads the stable installation token in the no-backup root. */
    suspend fun installEpoch(): InstallEpoch

    /** Publishes an immutable undo through `undo_<owner>.db.creating` without overwriting. */
    suspend fun publishUndo(source: File, ref: UndoRef): BackupResult<File>

    /**
     * Transfers a validated caller source into runtime ownership through
     * `staged_restore_<owner>.db.creating`, then atomically publishes the final name.
     */
    suspend fun publishRestoreSource(source: File, ref: RestoreSourceRef): BackupResult<File>

    /** Exact immutable undo when present; the path is derived only from [ref]. */
    fun undoFile(ref: UndoRef): File?

    /** Runtime-owned staged restore when present; the path is derived only from [ref]. */
    fun restoreSourceFile(ref: RestoreSourceRef): File?

    /** Released positional C in cache, used only by the explicit rollout migration table. */
    fun legacyPreRestoreFile(): File?

    /** Copies C immutably, or durability-syncs its existing replay target. Never consumes C. */
    suspend fun migrateLegacyUndo(ref: UndoRef): BackupResult<File>

    /** Deletes exactly [ref]; failure is retryable garbage and never a state transition. */
    suspend fun deleteUndo(ref: UndoRef): Boolean

    /** Deletes exactly [ref]; failure is retryable garbage and never a state transition. */
    suspend fun deleteRestoreSource(ref: RestoreSourceRef): Boolean

    /** Durable raw recovery export, written through a `.creating` file under the root. */
    suspend fun publishRecoveryExport(source: File): BackupResult<File>

    /** Existing durable raw export, or null. No SQLite handle is opened. */
    fun recoveryExportFile(): File?

    /** Copies [source] into the narrowly exposed cache share directory on explicit request. */
    suspend fun createShareCopy(source: File, fileName: String): BackupResult<File>

    /** Removes only strict protocol-owned names not protected by persisted [state]. */
    suspend fun sweep(state: RestoreProtocolState): RestoreGarbageCollectionReport
}

data class RestoreGarbageCollectionReport(
    val deletedNames: List<String>,
    val retryNames: List<String>,
)
