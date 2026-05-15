package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject
import javax.inject.Singleton
import io.github.stslex.workeeper.core.data.database.migration.hasMigrationPath as registryHasMigrationPath

@Singleton
internal class DatabaseSnapshotProviderImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : DatabaseSnapshotProvider {

    override suspend fun captureSnapshot(target: File): BackupResult<Unit> =
        withContext(dispatcher) {
            try {
                appDatabase.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(TRUNCATE)")
                    .use { cursor -> cursor.moveToFirst() }
                val source = context.getDatabasePath(AppDatabase.NAME)
                source.copyTo(target, overwrite = true)
                BackupResult.Success(Unit)
            } catch (e: IOException) {
                BackupResult.Failure(BackupError.Io(e))
            }
        }

    override suspend fun currentSchemaVersion(): Int = withContext(dispatcher) {
        appDatabase.openHelper.readableDatabase.version
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

    override suspend fun preserveCurrentDb(): BackupResult<File> = withContext(dispatcher) {
        val target = preRestoreBackupFile()
        try {
            appDatabase.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { cursor -> cursor.moveToFirst() }
            val source = context.getDatabasePath(AppDatabase.NAME)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            BackupResult.Success(target)
        } catch (e: IOException) {
            target.delete()
            BackupResult.Failure(BackupError.Io(e))
        }
    }

    override suspend fun rollbackToPreRestoreBackup(): BackupResult<Unit> =
        withContext(dispatcher) {
            val source = preRestoreBackupFile()
            if (!source.exists()) {
                return@withContext BackupResult.Failure(
                    BackupError.CorruptedBackup(reason = "no pre-restore backup to roll back to"),
                )
            }
            try {
                appDatabase.close()
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
                source.delete()
                BackupResult.Success(Unit)
            } catch (e: IOException) {
                BackupResult.Failure(BackupError.Io(e))
            }
        }

    override fun hasPreRestoreBackup(): Boolean = preRestoreBackupFile().exists()

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

    override fun hasPreMigrationBackup(): Boolean = preMigrationBackupFile().exists()

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

    override suspend fun restoreFromSnapshot(source: File): BackupResult<Unit> =
        withContext(dispatcher) {
            val magicResult = verifySqliteMagic(source)
            if (magicResult is BackupResult.Failure) return@withContext magicResult

            val sourceVersion = when (val r = peekSnapshotSchemaVersion(source)) {
                is BackupResult.Success -> r.data
                is BackupResult.Failure -> return@withContext r
            }
            val currentVersion = appDatabase.openHelper.readableDatabase.version
            if (sourceVersion > currentVersion) {
                return@withContext BackupResult.Failure(
                    BackupError.BackupTooNew(
                        backupSchemaVersion = sourceVersion,
                        appSchemaVersion = currentVersion,
                    ),
                )
            }

            try {
                appDatabase.close()
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

    private companion object {
        const val TAG = "DatabaseSnapshotProvider"
        const val SQLITE_HEADER_SIZE = 16
        const val PRE_RESTORE_BACKUP_NAME = "pre_restore_backup.db"
        const val PRE_MIGRATION_BACKUP_NAME = "pre_migration_backup.db"
        val SQLITE_MAGIC: ByteArray =
            "SQLite format 3".toByteArray(Charsets.US_ASCII) + 0x00.toByte()
    }
}
