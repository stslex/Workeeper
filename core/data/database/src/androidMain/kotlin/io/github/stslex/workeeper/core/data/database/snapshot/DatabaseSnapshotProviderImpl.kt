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

/** App-scoped Metro binding for snapshot and live-database lookup. */
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
            val staging = File(target.parentFile, "$PRE_RESTORE_BACKUP_NAME.promoting")
            try {
                // Copy, not move: the journal's reservation stays recoverable through promotion.
                staging.delete()
                reservation.copyTo(staging, overwrite = true)
                // Keep the canonical slot until replacement content is safely staged.
                if (!staging.renameTo(target)) {
                    target.delete()
                    if (!staging.renameTo(target)) {
                        staging.copyTo(target, overwrite = true)
                        staging.delete()
                    }
                }
                BackupResult.Success(Unit)
            } catch (e: IOException) {
                staging.delete()
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
        // GUARD: checkpoint through a direct SQLite open, never through Room — opening via Room
        // would run the very migration this snapshot exists to protect against.
        runCatching {
            SQLiteDatabase.openDatabase(
                source.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { db -> db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
        }.onFailure { e ->
            // A stale snapshot beats no snapshot for the recovery export.
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
            // Runtime has closed the generation database; this path has no Room access.
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
     * Flush the WAL into the main db file so a later file copy captures committed rows.
     * GUARD: the pragma returns a row — only `step()` executes it; unstepped it silently no-ops.
     */
    private suspend fun checkpointWal() {
        appDatabase.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { stmt -> stmt.step() }
        }
    }

    /** Live schema version via `PRAGMA user_version`. */
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
