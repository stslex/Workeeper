// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File

/**
 * Two-phase canonical undo-slot promotion. The slot is written ONLY by an atomic rename of a
 * fully staged file, and only after the durable `Committed` record. See spec §8.5a.
 */
interface RollbackPromotion {

    /** Copies [reservation] beside the canonical slot under [attemptId]. The slot is untouched. */
    suspend fun stagePromotedRollback(reservation: File, attemptId: String): BackupResult<Unit>

    /**
     * Installs [attemptId]'s staged image over the canonical slot, atomically and idempotently:
     * a pending staging is renamed; else [reservation] is re-staged and renamed; else an existing
     * slot is already this attempt's. Fails only when no undo image can be produced.
     */
    suspend fun completePromotedRollback(
        reservation: File?,
        attemptId: String,
    ): BackupResult<Unit>

    /** Discards the staging of an attempt that never became durable. */
    suspend fun discardStagedPromotion(attemptId: String)
}

/**
 * File-level backup and restore mechanics. The runtime owns database close and replacement;
 * this provider validates while open and performs pure file replacement after close.
 */
interface DatabaseSnapshotProvider : RollbackPromotion {

    /**
     * Checkpoints the WAL, then file-copies the live `.db` over [target]. Briefly blocks writes and
     * leaves partial writes behind on failure — the caller owns cleanup.
     */
    suspend fun captureSnapshot(target: File): BackupResult<Unit>

    /** Validates [source] against the still-open live database before quiescing and close. */
    suspend fun validateSnapshotForRestore(source: File): BackupResult<Unit>

    /**
     * Validates a rollback source — SQLite magic and page-count completeness. The rollback
     * direction has no other check, so an unvalidated swap is a false success (spec §25.4).
     */
    suspend fun validateRollbackSource(source: File): BackupResult<Unit>

    /** Performs sidecar cleanup, copy, and atomic rename; runtime owns validation and close. */
    suspend fun replaceLiveDatabaseFile(source: File): BackupResult<Unit>

    /** Schema version of the live database, read as `PRAGMA user_version` through Room. */
    suspend fun currentSchemaVersion(): Int

    /**
     * Whether the registered `MIGRATIONS` graph chains [from] to [to]. `true` when equal, `false`
     * for downgrades and for forward gaps with no chain.
     */
    fun hasMigrationPath(from: Int, to: Int): Boolean

    /**
     * Reads `PRAGMA user_version` from [source] without Room; fails with `CorruptedBackup` when
     * [source] cannot be opened or queried as SQLite.
     */
    suspend fun peekSnapshotSchemaVersion(source: File): BackupResult<Int>

    suspend fun reserveRollbackSnapshot(attemptId: String): BackupResult<File>

    /** Canonical undo slot, if present. */
    fun getPreRestoreBackupFile(): File?

    /**
     * Formatted `"from→to"` pairs of every registered migration, for non-fatals and the diagnostic
     * export, so the MIGRATIONS array stays inside this module.
     */
    fun availableMigrationsLabel(): String

    /** Deletes the canonical undo slot without applying it. No-op when the file is absent. */
    suspend fun deletePreRestoreBackup()

    /**
     * Copies the live `.db` to `cache/pre_migration_backup.db` before Room opens — the Scenario 2
     * net `RecoveryActivity` exports. `null` when no live file exists or the copy fails.
     */
    suspend fun preserveDbBeforeMigration(): File?

    /** The preserved `cache/pre_migration_backup.db` for RecoveryActivity's export, or `null`. */
    fun getPreMigrationBackupFile(): File?

    /** Deletes `cache/pre_migration_backup.db`. No-op when absent. */
    suspend fun deletePreMigrationBackup()
}
