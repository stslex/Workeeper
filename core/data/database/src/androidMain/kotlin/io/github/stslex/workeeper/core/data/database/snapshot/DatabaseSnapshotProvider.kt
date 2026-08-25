// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreGarbageCollectionReport
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File

/**
 * Exact-reference database replacement mechanics. Protocol paths never cross this boundary: a
 * caller supplies an opaque owner and this module derives the file below the no-backup root.
 */
interface DatabaseSnapshotProvider :
    DatabaseBackupSnapshotProvider,
    RestoreDatabaseMechanics,
    RecoveryDatabaseFiles

interface DatabaseBackupSnapshotProvider {

    /** Checkpoints the WAL, then copies the live database to a caller-owned backup file. */
    suspend fun captureSnapshot(target: File): BackupResult<Unit>

    /** Schema version of the serving live database through Room. */
    suspend fun currentSchemaVersion(): Int

    /** Direct schema peek for startup migration preflight; the path is not persisted. */
    suspend fun peekSnapshotSchemaVersion(source: File): BackupResult<Int>

    fun hasMigrationPath(from: Int, to: Int): Boolean

    fun availableMigrationsLabel(): String
}

interface RestoreDatabaseMechanics {

    /** Transfers the downloaded source into non-evictable runtime ownership. */
    suspend fun stageRestoreSource(source: File, ref: RestoreSourceRef): BackupResult<File>

    /** Exact staged restore source, or null when the same-install asset is missing. */
    fun getRestoreSourceFile(ref: RestoreSourceRef): File?

    /** Checkpoints the live WAL and atomically publishes this attempt's immutable undo. */
    suspend fun createUndo(ref: UndoRef): BackupResult<File>

    /** Exact immutable undo, or null when the same-install asset is missing. */
    fun getUndoFile(ref: UndoRef): File?

    /** Validates the exact runtime-owned restore source while Room is still serving. */
    suspend fun validateRestoreSource(ref: RestoreSourceRef): BackupResult<Unit>

    /** Validates the exact immutable undo without opening Room. */
    suspend fun validateUndo(ref: UndoRef): BackupResult<Unit>

    /** Validates released `cache/pre_restore_backup.db` only for rollout migration. */
    suspend fun validateLegacyUndo(): BackupResult<Unit>

    /** Advisory five-file-peak admission check. Equality is sufficient; no bytes are allocated. */
    suspend fun checkRestoreCapacity(ref: RestoreSourceRef): BackupResult<Unit>

    /** Advisory rollback admission check. Equality is sufficient; no bytes are allocated. */
    suspend fun checkRollbackCapacity(ref: UndoRef): BackupResult<Unit>

    /** Replaces the closed live database from this exact staged source. */
    suspend fun replaceLiveDatabaseFromRestore(ref: RestoreSourceRef): BackupResult<Unit>

    /** Replaces the closed live database from this exact immutable undo. */
    suspend fun replaceLiveDatabaseFromUndo(ref: UndoRef): BackupResult<Unit>

    /** Best-effort exact deletion; false means retryable garbage. */
    suspend fun deleteUndo(ref: UndoRef): Boolean

    /** Best-effort exact deletion; false means retryable garbage. */
    suspend fun deleteRestoreSource(ref: RestoreSourceRef): Boolean
}

interface RecoveryDatabaseFiles {

    /** Released positional C, used only by the explicit rollout table. */
    fun legacyPreRestoreFile(): File?

    /** Copies released C immutably, or re-syncs its replay target, without consuming C. */
    suspend fun migrateLegacyUndo(ref: UndoRef): BackupResult<File>

    /** Consumes only released C after the migrated protocol state is durable. */
    suspend fun deleteLegacyPreRestore(): Boolean

    /** Header-only inspection used before any Room or framework SQLite handle may be opened. */
    suspend fun inspectLiveDatabaseWithoutRoom(): BackupResult<Int>

    /** Publishes the raw pre-migration image durably below the no-backup recovery root. */
    suspend fun preserveDbBeforeMigration(): BackupResult<File>

    fun getRecoveryExportFile(): File?

    /** Creates an on-demand copy in the narrowly exposed cache share directory. */
    suspend fun createRecoveryExportShareCopy(fileName: String): BackupResult<File>

    /** Removes the durable export after recovery state no longer needs it. */
    suspend fun deleteRecoveryExport(): Boolean

    /** Owner-aware cleanup based only on decoded same-install protocol state. */
    suspend fun sweepRecoveryFiles(
        state: RestoreProtocolState,
    ): RestoreGarbageCollectionReport
}
