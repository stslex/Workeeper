package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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
        const val SQLITE_HEADER_SIZE = 16
        val SQLITE_MAGIC: ByteArray =
            "SQLite format 3".toByteArray(Charsets.US_ASCII) + 0x00.toByte()
    }
}
