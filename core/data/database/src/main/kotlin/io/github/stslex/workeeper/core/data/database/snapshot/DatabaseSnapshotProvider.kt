package io.github.stslex.workeeper.core.data.database.snapshot

import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File

/**
 * File-level access to the Room-backed SQLite database for backup/restore.
 *
 * The provider exposes the four operations the backup feature needs without leaking
 * Room internals to callers:
 *
 *  - [captureSnapshot] produces a portable `.db` copy.
 *  - [restoreFromSnapshot] replaces the live db file with a previously captured copy.
 *  - [currentSchemaVersion] reports the schema version of the live database.
 *  - [peekSnapshotSchemaVersion] inspects a `.db` file's schema version without
 *    opening it through Room.
 */
interface DatabaseSnapshotProvider {

    /**
     * Issues `PRAGMA wal_checkpoint(TRUNCATE)` against the live database to flush any
     * pending WAL pages into the main `.db` file, then file-copies the `.db` to
     * [target]. Briefly blocks writes for the duration of the checkpoint.
     *
     * Overwrites [target] when it already exists. Does not delete [target] on failure
     * — partial writes may remain; the caller owns cleanup.
     *
     * Returns [BackupResult.Failure] with [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.Io]
     * when the checkpoint or the file copy fails.
     */
    suspend fun captureSnapshot(target: File): BackupResult<Unit>

    /**
     * Destructively replaces the live database file with the contents of [source].
     *
     * The call (1) validates the SQLite magic header on [source], (2) compares its
     * schema version against the running app's via [peekSnapshotSchemaVersion] —
     * returning [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.BackupTooNew]
     * if [source] is newer — (3) closes the live `AppDatabase`, (4) deletes the
     * `-wal` and `-shm` sidecars, and (5) atomically replaces the main `.db` via a
     * same-directory temp file + rename.
     *
     * The caller MUST tear down every consumer of the in-process Room reference
     * (DAOs, repositories, observers) before invoking — the reference is stale after
     * success. The app must restart to rebuild the Room graph; this provider does
     * not perform that restart.
     */
    suspend fun restoreFromSnapshot(source: File): BackupResult<Unit>

    /**
     * Schema version of the live database, read from the SQLite `PRAGMA user_version`
     * via Room's open helper. Matches the `@Database(version = N)` declaration.
     */
    suspend fun currentSchemaVersion(): Int

    /**
     * Whether the registered Room migration graph contains a sequence of edges
     * that can migrate a database from [from] to [to]. Reads from the same
     * `MIGRATIONS` array that the live `Room.databaseBuilder` is wired with, so
     * pre-restore checks and the runtime behavior agree by construction.
     *
     * Returns `true` for the trivial case `from == to`; returns `false` for
     * downgrades (`from > to`) and for forward gaps with no chain.
     */
    fun hasMigrationPath(from: Int, to: Int): Boolean

    /**
     * Reads `PRAGMA user_version` from [source] without going through Room.
     *
     * Returns [BackupResult.Failure] with
     * [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.CorruptedBackup]
     * when [source] cannot be opened or queried as SQLite.
     */
    suspend fun peekSnapshotSchemaVersion(source: File): BackupResult<Int>

    /**
     * Copies the live database to `cache/pre_restore_backup.db` before a Restore
     * replaces it. Used both as the automatic rollback target if the post-restart
     * Room migration fails (Scenario 1) and as the source for user-initiated
     * undo (Scenario 3). Overwrites any previously preserved snapshot — only
     * one slot is kept at a time.
     *
     * Issues `PRAGMA wal_checkpoint(TRUNCATE)` first so the preserved file is
     * self-contained; the WAL/SHM sidecars on the live database are unaffected.
     *
     * Returns the preserved [File] on success, or [BackupResult.Failure] with
     * [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.Io]
     * when the checkpoint or file copy fails. The caller treats failure as
     * "do not proceed with the restore" — there is no point swapping in a new
     * database we cannot roll back from.
     *
     * Spec: `documentation/feature-specs/backup-recovery.md` →
     * "Storage lifecycle of preserved DB files".
     */
    suspend fun preserveCurrentDb(): BackupResult<File>

    /**
     * Atomically replaces the live database with the contents of the most
     * recently preserved `pre_restore_backup.db`. Cleans up `-wal` / `-shm`
     * sidecars, closes the in-process Room handle, and renames the preserved
     * file into the live database slot. Used by both the Scenario 1 automatic
     * rollback (migration failure after restore) and the Scenario 3
     * user-initiated undo.
     *
     * After success, the preserved file no longer exists (it was renamed
     * into the live slot). The caller MUST restart the app — the Room graph
     * is stale.
     *
     * Returns [BackupResult.Failure] with
     * [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.CorruptedBackup]
     * if no preserved file exists, or `.Io` if the swap fails.
     */
    suspend fun rollbackToPreRestoreBackup(): BackupResult<Unit>

    /**
     * Whether a `cache/pre_restore_backup.db` exists. Cheap file-existence
     * check used by Settings to decide whether to render the
     * "Revert last restore" row.
     */
    fun hasPreRestoreBackup(): Boolean

    /**
     * Deletes the preserved `cache/pre_restore_backup.db` without applying
     * it. Used after Scenario 1 failure-path rollback consumes the file
     * (rollback already moved it; this just guarantees the slot is empty
     * even on partial failure) and as a defensive cleanup on app updates.
     *
     * No-op when the file does not exist.
     */
    suspend fun deletePreRestoreBackup()
}
