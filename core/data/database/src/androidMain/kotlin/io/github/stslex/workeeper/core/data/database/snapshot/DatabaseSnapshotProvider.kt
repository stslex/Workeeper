// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File

/**
 * File-level backup and restore mechanics. The runtime owns database close and replacement;
 * this provider validates while open and performs pure file replacement after close.
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

    /** Validates [source] against the still-open live database before quiescing and close. */
    suspend fun validateSnapshotForRestore(source: File): BackupResult<Unit>

    /** Performs sidecar cleanup, copy, and atomic rename; runtime owns validation and close. */
    suspend fun replaceLiveDatabaseFile(source: File): BackupResult<Unit>

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

    suspend fun reserveRollbackSnapshot(attemptId: String): BackupResult<File>

    /** Copies, never moves, an attempt reservation to the canonical undo slot. */
    suspend fun promoteRollbackReservation(reservation: File): BackupResult<Unit>

    /** Canonical undo slot, if present. */
    fun getPreRestoreBackupFile(): File?

    /**
     * Formatted "from→to" pairs of every registered migration, joined with
     * commas (e.g. `"5→6,6→7"`). Used by Crashlytics non-fatals and the
     * diagnostic export to record the available migration set without
     * exposing the internal MIGRATIONS array outside the database module.
     */
    fun availableMigrationsLabel(): String

    /**
     * Deletes the preserved `cache/pre_restore_backup.db` without applying
     * it. Used after Scenario 1 failure-path rollback consumes the file
     * (rollback already moved it; this just guarantees the slot is empty
     * even on partial failure) and as a defensive cleanup on app updates.
     *
     * No-op when the file does not exist.
     */
    suspend fun deletePreRestoreBackup()

    /**
     * Copies the live database file to `cache/pre_migration_backup.db`
     * directly via `File.copyTo`, without going through Room. This is the
     * Scenario 2 safety net: when `Application.onCreate` decides to route
     * the user to `RecoveryActivity` (developer-error migration path), the
     * live `.db` is still pristine because Room was never opened — the
     * direct copy captures that state for the user to export.
     *
     * Distinct from [reserveRollbackSnapshot] (Scenario 1):
     * - Runs **before** Room init, so it cannot WAL-checkpoint (would force
     *   Room to open the database and migrate). Any unflushed WAL pages
     *   from the previous app run are not in the snapshot; this is
     *   acceptable because the realistic Scenario 2 case is "app updated
     *   without registering a migration" — the previous launch closed
     *   cleanly, WAL is already merged.
     * - Lives at a different cache path (`pre_migration_backup.db`) with an
     *   independent lifecycle (consumed by `RecoveryActivity` export, not
     *   by automatic rollback).
     *
     * Returns the preserved file on success, `null` if the live database
     * file does not exist (fresh install, no Scenario 2 to recover from)
     * or the copy fails (logged best-effort; the caller still routes to
     * recovery without the snapshot).
     */
    suspend fun preserveDbBeforeMigration(): File?

    /** Whether `cache/pre_migration_backup.db` exists on disk. */

    /**
     * Returns the preserved `cache/pre_migration_backup.db` File for
     * `RecoveryActivity`'s "Export raw data" action, or `null` if absent.
     */
    fun getPreMigrationBackupFile(): File?

    /** Deletes `cache/pre_migration_backup.db`. No-op when absent. */
    suspend fun deletePreMigrationBackup()
}
