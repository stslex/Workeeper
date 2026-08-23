// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.migration.MIGRATIONS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import io.github.stslex.workeeper.core.data.database.migration.hasMigrationPath as registryHasMigrationPath

/**
 * App-Scope Collapse Step 5 (5a). Metro-owned via repeatable `@ContributesBinding(AppScope)` — binds BOTH
 * [DatabaseSnapshotProvider] and [LiveDatabaseLocator] to this ONE `@SingleIn(AppScope)` instance (the same
 * `@Binds`-pair the deleted `CoreDatabaseBindingsModule` held). Public for cross-module aggregation (D1;
 * never hand-construct — resolve via DI). Derives from the [AppDatabase] `create()` root; `Context` is
 * PLAIN (from the graph's `create(applicationContext)` — `@ApplicationContext` is a Hilt qualifier, not
 * carried into the Metro graph); `@IODispatcher` is the direct `Dispatchers.IO`. No `appGraph` back-edge.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DatabaseSnapshotProvider>())
@ContributesBinding(AppScope::class, binding = binding<LiveDatabaseLocator>())
public class DatabaseSnapshotProviderImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val context: Context,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : DatabaseSnapshotProvider, LiveDatabaseLocator {

    override fun liveDatabaseFile(): File = context.getDatabasePath(AppDatabase.NAME)

    override suspend fun captureSnapshot(target: File): BackupResult<Unit> =
        withContext(dispatcher) {
            try {
                checkpointWal()
                val source = context.getDatabasePath(AppDatabase.NAME)
                source.copyTo(target, overwrite = true)
                BackupResult.Success(Unit)
            } catch (e: IOException) {
                BackupResult.Failure(BackupError.Io(e))
            }
        }

    override suspend fun currentSchemaVersion(): Int = withContext(dispatcher) {
        readUserVersion()
    }

    override fun hasMigrationPath(from: Int, to: Int): Boolean =
        registryHasMigrationPath(from = from, to = to)

    override suspend fun peekSnapshotSchemaVersion(source: File): BackupResult<Int> =
        withContext(dispatcher) {
            try {
                SQLiteDatabase
                    .openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    .use { db -> BackupResult.Success(db.version) }
            } catch (e: SQLiteException) {
                BackupResult.Failure(
                    BackupError.CorruptedBackup(reason = e.message ?: "peek failed"),
                )
            } catch (e: IllegalStateException) {
                BackupResult.Failure(
                    BackupError.CorruptedBackup(reason = e.message ?: "peek failed"),
                )
            }
        }

    override suspend fun reserveRollbackSnapshot(attemptId: String): BackupResult<File> =
        withContext(dispatcher) {
            val target = File(context.cacheDir, "$ROLLBACK_RESERVATION_PREFIX$attemptId.db")
            try {
                checkpointWal()
                val source = context.getDatabasePath(AppDatabase.NAME)
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
                BackupResult.Success(target)
            } catch (e: IOException) {
                target.delete()
                BackupResult.Failure(BackupError.Io(e))
            }
        }

    override suspend fun promoteRollbackReservation(reservation: File): BackupResult<Unit> =
        withContext(dispatcher) {
            val target = preRestoreBackupFile()
            try {
                target.delete()
                if (!reservation.renameTo(target)) {
                    // Same-directory rename cannot normally fail; fall back to copy+delete so a
                    // promotion never silently leaves the undo slot holding older data.
                    reservation.copyTo(target, overwrite = true)
                    reservation.delete()
                }
                BackupResult.Success(Unit)
            } catch (e: IOException) {
                BackupResult.Failure(BackupError.Io(e))
            }
        }

    override fun getPreRestoreBackupFile(): File? =
        preRestoreBackupFile().takeIf { it.exists() }

    override fun availableMigrationsLabel(): String =
        MIGRATIONS.joinToString(",") { "${it.startVersion}→${it.endVersion}" }

    override suspend fun preserveDbBeforeMigration(): File? = withContext(dispatcher) {
        val source = context.getDatabasePath(AppDatabase.NAME)
        if (!source.exists()) return@withContext null
        // Checkpoint the WAL via a direct SQLite open so committed-but-unsynced rows
        // make it into the snapshot. We cannot go through `appDatabase.openHelper`
        // here — that would trigger Room's migration path, which is exactly what
        // the caller is trying to avoid. The SQLite open with OPEN_READWRITE does
        // not invoke Room and does not run migrations; it just flushes the WAL.
        runCatching {
            SQLiteDatabase.openDatabase(
                source.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { db -> db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
        }.onFailure { e ->
            // A stale snapshot is strictly better than no snapshot for the
            // recovery export; proceed with whatever data is in the main file.
            Log.tag(TAG).w("WAL checkpoint failed; snapshot may miss recent commits", e)
        }
        val target = preMigrationBackupFile()
        try {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            target
        } catch (e: IOException) {
            target.delete()
            null
        }
    }

    override fun getPreMigrationBackupFile(): File? =
        preMigrationBackupFile().takeIf { it.exists() }

    override suspend fun deletePreMigrationBackup() {
        withContext(dispatcher) { preMigrationBackupFile().delete() }
    }

    private fun preMigrationBackupFile(): File =
        File(context.cacheDir, PRE_MIGRATION_BACKUP_NAME)

    override suspend fun deletePreRestoreBackup() {
        withContext(dispatcher) { preRestoreBackupFile().delete() }
    }

    private fun preRestoreBackupFile(): File = File(context.cacheDir, PRE_RESTORE_BACKUP_NAME)

    override suspend fun validateSnapshotForRestore(source: File): BackupResult<Unit> =
        withContext(dispatcher) {
            val magicResult = verifySqliteMagic(source)
            if (magicResult is BackupResult.Failure) return@withContext magicResult

            val sourceVersion = when (val r = peekSnapshotSchemaVersion(source)) {
                is BackupResult.Success -> r.data
                is BackupResult.Failure -> return@withContext r
            }
            // Reads the LIVE database — the runtime calls this before Quiescing/close.
            val currentVersion = readUserVersion()
            if (sourceVersion > currentVersion) {
                return@withContext BackupResult.Failure(
                    BackupError.BackupTooNew(
                        backupSchemaVersion = sourceVersion,
                        appSchemaVersion = currentVersion,
                    ),
                )
            }
            BackupResult.Success(Unit)
        }

    override suspend fun replaceLiveDatabaseFile(source: File): BackupResult<Unit> =
        withContext(dispatcher) {
            // Pure file mechanics — the runtime already closed the generation's database (Phase 5
            // R2, spec §8.5); nothing here touches a Room connection.
            try {
                val target = context.getDatabasePath(AppDatabase.NAME)
                val parent = target.parentFile
                    ?: throw IOException("database parent dir missing")
                File(parent, "${AppDatabase.NAME}-wal").delete()
                File(parent, "${AppDatabase.NAME}-shm").delete()
                val tmp = File(parent, "${AppDatabase.NAME}.tmp")
                source.copyTo(tmp, overwrite = true)
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    throw IOException("atomic rename failed")
                }
                BackupResult.Success(Unit)
            } catch (e: IOException) {
                BackupResult.Failure(BackupError.Io(e))
            }
        }

    private fun verifySqliteMagic(source: File): BackupResult<Unit> = try {
        val header = ByteArray(SQLITE_HEADER_SIZE)
        val read = source.inputStream().use { it.read(header) }
        if (read == SQLITE_HEADER_SIZE && header.contentEquals(SQLITE_MAGIC)) {
            BackupResult.Success(Unit)
        } else {
            BackupResult.Failure(
                BackupError.CorruptedBackup(reason = "invalid SQLite header"),
            )
        }
    } catch (e: IOException) {
        BackupResult.Failure(BackupError.CorruptedBackup(reason = e.message ?: "header read failed"))
    }

    /**
     * Flush Room's WAL into the main db file so a subsequent file copy captures committed rows.
     * Room 3 removed `openHelper.writableDatabase.query(...)`; the checkpoint now runs through a
     * writer connection. `PRAGMA wal_checkpoint(TRUNCATE)` RETURNS a row (busy, log, checkpointed)
     * — `stmt.step()` is what actually executes the pragma, so it must be stepped (a prepared-but-
     * unstepped statement would silently no-op and leave the WAL beside the snapshot).
     */
    private suspend fun checkpointWal() {
        appDatabase.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { stmt -> stmt.step() }
        }
    }

    /** Live schema version via `PRAGMA user_version` (Room 3 replacement for `openHelper.*.version`). */
    private suspend fun readUserVersion(): Int =
        appDatabase.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA user_version") { stmt ->
                if (stmt.step()) stmt.getLong(0).toInt() else 0
            }
        }

    private companion object {
        const val TAG = "DatabaseSnapshotProvider"
        const val SQLITE_HEADER_SIZE = 16
        const val PRE_RESTORE_BACKUP_NAME = "pre_restore_backup.db"
        const val ROLLBACK_RESERVATION_PREFIX = "rollback_reservation_"
        const val PRE_MIGRATION_BACKUP_NAME = "pre_migration_backup.db"
        val SQLITE_MAGIC: ByteArray =
            "SQLite format 3".toByteArray(Charsets.US_ASCII) + 0x00.toByte()
    }
}
