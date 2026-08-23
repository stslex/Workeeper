// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File

/**
 * File-level access to the Room-backed SQLite database for backup/restore.
 *
 * **The provider never closes or swaps the published database** (Phase 5 R2,
 * `kmp-phase-5-startup-processor.md` §8.5): Room 3's `close()` is terminal for the object
 * (§7.1, measured), so close-and-replace is a runtime-generation transition owned by the
 * application runtime host, reached by callers through the
 * [io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement] seam. What lives here
 * is everything AROUND that transaction:
 *
 *  - [captureSnapshot] / [reserveRollbackSnapshot] produce portable copies (checkpoint + copy);
 *  - [validateSnapshotForRestore] runs the pre-swap gates (magic header, schema-version
 *    comparison) through the STILL-OPEN live database;
 *  - [replaceLiveDatabaseFile] is the pure file mechanics (sidecar delete + copy + atomic
 *    rename) the runtime invokes AFTER it closed the generation's database;
 *  - version peeks, migration-path queries, and the preserved-file lifecycle.
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
     * The pre-swap restore gates, in the exact order the pre-split `restoreFromSnapshot` ran
     * them: (1) SQLite magic header on [source]; (2) [source]'s schema version via
     * [peekSnapshotSchemaVersion], compared against the LIVE database's `user_version` —
     * returning [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.BackupTooNew]
     * when [source] is newer. Reads the live database, so the runtime MUST call this while the
     * generation's database is still open — before Quiescing and close.
     */
    suspend fun validateSnapshotForRestore(source: File): BackupResult<Unit>

    /**
     * The pure file mechanics of a live-database replacement: delete the `-wal` and `-shm`
     * sidecars, copy [source] to a same-directory temp file, atomically rename it into the live
     * slot. [source] is NOT consumed and NOT validated — validation is [validateSnapshotForRestore]
     * and consumption is the caller's — and, critically, the database is NOT closed here: the
     * runtime closes the generation's `AppDatabase` (terminal, §7.1) before invoking this, as one
     * step of the replacement transaction it owns. Never call this outside that transaction.
     */
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

    // `preserveCurrentDb()` (a copy straight onto the canonical undo slot) is GONE: an attempt
    // reserves its own snapshot and promotes it on commit (spec §8.5a), which is what keeps two
    // concurrent restores from overwriting each other's rollback file.

    suspend fun reserveRollbackSnapshot(attemptId: String): BackupResult<File>

    /**
     * COPIES a reservation from [reserveRollbackSnapshot] onto the canonical
     * `cache/pre_restore_backup.db` undo slot, replacing whatever the previous restore left
     * there. Called only after the attempt's live-file mutation committed, and NEVER moves or
     * deletes the reservation (Phase 5 R4, spec §8.5a): the still-`Prepared` journal names that
     * file, so it must remain recoverable across every crash point of the promotion. The
     * runtime deletes the reservation only after the durable `Committed` record lands; a
     * committed cold start cleans a retained copy up idempotently.
     */
    suspend fun promoteRollbackReservation(reservation: File): BackupResult<Unit>

    // An unused reservation is deleted by the runtime that owns it — the same file-ownership
    // rule the staged restore source follows; no provider method is needed for a plain delete.

    /**
     * The preserved `cache/pre_restore_backup.db` when it exists, else `null`. The runtime's
     * rollback branch replays it through [replaceLiveDatabaseFile] and then consumes it via
     * [deletePreRestoreBackup] — the same net effect (and the same crash-window shape) as the
     * pre-split copy+rename+delete sequence. Also the Settings "Revert last restore" row's
     * existence check (`!= null` — the separate boolean accessor was merged into this one).
     */
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
