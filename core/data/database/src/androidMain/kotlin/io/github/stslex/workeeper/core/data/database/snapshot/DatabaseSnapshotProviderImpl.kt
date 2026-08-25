// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreGarbageCollectionReport
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.migration.MIGRATIONS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import io.github.stslex.workeeper.core.data.database.migration.hasMigrationPath as registryHasMigrationPath

/** App-scoped Metro binding for exact-ref snapshots and live-database lookup. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DatabaseSnapshotProvider>())
@ContributesBinding(AppScope::class, binding = binding<LiveDatabaseLocator>())
public class DatabaseSnapshotProviderImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val context: Context,
    private val recoveryFiles: RestoreRecoveryFileStore,
    private val storageCapacity: RestoreStorageCapacity,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : DatabaseSnapshotProvider, LiveDatabaseLocator {

    override fun liveDatabaseFile(): File = context.getDatabasePath(AppDatabase.NAME)

    override suspend fun captureSnapshot(target: File): BackupResult<Unit> =
        withContext(dispatcher) {
            backupIoResult {
                appDatabase.checkpointWal()
                val source = liveDatabaseFile()
                target.parentFile?.let(::requireDirectory)
                source.copyTo(target, overwrite = true)
                Unit
            }
        }

    override suspend fun stageRestoreSource(
        source: File,
        ref: RestoreSourceRef,
    ): BackupResult<File> = recoveryFiles.publishRestoreSource(source, ref)

    override fun getRestoreSourceFile(ref: RestoreSourceRef): File? =
        recoveryFiles.restoreSourceFile(ref)

    override suspend fun createUndo(ref: UndoRef): BackupResult<File> {
        val prepared = withContext(dispatcher) {
            backupIoResult {
                appDatabase.checkpointWal()
                liveDatabaseFile().also { source ->
                    if (!source.isFile) throw IOException("live database is missing")
                }
            }
        }
        return when (prepared) {
            is BackupResult.Failure -> prepared
            is BackupResult.Success -> recoveryFiles.publishUndo(prepared.data, ref)
        }
    }

    override fun getUndoFile(ref: UndoRef): File? = recoveryFiles.undoFile(ref)

    override suspend fun validateRestoreSource(ref: RestoreSourceRef): BackupResult<Unit> {
        val source = getRestoreSourceFile(ref)
            ?: return missingAssetFailure("staged restore source")
        val structural = SqliteFileValidator.verifyStructural(source)
        if (structural is BackupResult.Failure) return structural
        val sourceVersion = when (val result = peekSnapshotSchemaVersion(source)) {
            is BackupResult.Success -> result.data
            is BackupResult.Failure -> return result
        }
        val currentVersion = currentSchemaVersion()
        return when {
            sourceVersion > currentVersion -> BackupResult.Failure(
                BackupError.BackupTooNew(
                    backupSchemaVersion = sourceVersion,
                    appSchemaVersion = currentVersion,
                ),
            )

            !hasMigrationPath(sourceVersion, currentVersion) -> BackupResult.Failure(
                BackupError.MissingMigrationPath(
                    backupSchemaVersion = sourceVersion,
                    appSchemaVersion = currentVersion,
                ),
            )

            else -> BackupResult.Success(Unit)
        }
    }

    override suspend fun validateUndo(ref: UndoRef): BackupResult<Unit> {
        val source = getUndoFile(ref) ?: return missingAssetFailure("immutable undo")
        return SqliteFileValidator.verifyStructural(source)
    }

    override suspend fun validateLegacyUndo(): BackupResult<Unit> {
        val source = legacyPreRestoreFile() ?: return missingAssetFailure("legacy undo")
        return SqliteFileValidator.verifyStructural(source)
    }

    override suspend fun checkRestoreCapacity(ref: RestoreSourceRef): BackupResult<Unit> =
        withContext(dispatcher) {
            val source = getRestoreSourceFile(ref)
                ?: return@withContext missingAssetFailure("staged restore source")
            val checkpoint = backupIoResult {
                appDatabase.checkpointWal()
            }
            if (checkpoint is BackupResult.Failure) return@withContext checkpoint
            checkStorageCapacity(
                storageCapacity,
                context.noBackupFilesDir,
                storageCapacity.sizeBytes(liveDatabaseFile()),
                storageCapacity.sizeBytes(source),
            )
        }

    override suspend fun checkRollbackCapacity(ref: UndoRef): BackupResult<Unit> =
        withContext(dispatcher) {
            val source = getUndoFile(ref)
                ?: return@withContext missingAssetFailure("immutable undo")
            checkStorageCapacity(
                storageCapacity,
                context.noBackupFilesDir,
                storageCapacity.sizeBytes(source),
            )
        }

    override suspend fun replaceLiveDatabaseFromRestore(
        ref: RestoreSourceRef,
    ): BackupResult<Unit> {
        val source = getRestoreSourceFile(ref)
            ?: return missingAssetFailure("staged restore source")
        return withContext(dispatcher) { LiveDatabaseFileReplacer.replace(context, source) }
    }

    override suspend fun replaceLiveDatabaseFromUndo(ref: UndoRef): BackupResult<Unit> {
        val source = getUndoFile(ref) ?: return missingAssetFailure("immutable undo")
        return withContext(dispatcher) { LiveDatabaseFileReplacer.replace(context, source) }
    }

    override suspend fun deleteUndo(ref: UndoRef): Boolean = recoveryFiles.deleteUndo(ref)

    override suspend fun deleteRestoreSource(ref: RestoreSourceRef): Boolean =
        recoveryFiles.deleteRestoreSource(ref)

    override fun legacyPreRestoreFile(): File? = recoveryFiles.legacyPreRestoreFile()

    override suspend fun migrateLegacyUndo(ref: UndoRef): BackupResult<File> =
        recoveryFiles.migrateLegacyUndo(ref)

    override suspend fun deleteLegacyPreRestore(): Boolean =
        recoveryFiles.deleteLegacyPreRestore()

    override suspend fun currentSchemaVersion(): Int = withContext(dispatcher) {
        appDatabase.readUserVersion()
    }

    override suspend fun inspectLiveDatabaseWithoutRoom(): BackupResult<Int> {
        val source = liveDatabaseFile()
        val structural = SqliteFileValidator.verifyStructural(source)
        if (structural is BackupResult.Failure) return structural
        val version = when (val header = SqliteHeaderCheck.readUserVersion(source)) {
            is BackupResult.Failure -> return header
            is BackupResult.Success -> header.data
        }
        return when {
            version > APP_DATABASE_VERSION -> BackupResult.Failure(
                BackupError.BackupTooNew(
                    backupSchemaVersion = version,
                    appSchemaVersion = APP_DATABASE_VERSION,
                ),
            )

            !hasMigrationPath(version, APP_DATABASE_VERSION) -> BackupResult.Failure(
                BackupError.MissingMigrationPath(
                    backupSchemaVersion = version,
                    appSchemaVersion = APP_DATABASE_VERSION,
                ),
            )

            else -> BackupResult.Success(version)
        }
    }

    override suspend fun peekSnapshotSchemaVersion(source: File): BackupResult<Int> =
        withContext(dispatcher) {
            corruptedBackupResult("schema peek failed") {
                SQLiteDatabase
                    .openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    .use { db -> db.version }
            }
        }

    override fun hasMigrationPath(from: Int, to: Int): Boolean =
        registryHasMigrationPath(from = from, to = to)

    override fun availableMigrationsLabel(): String =
        MIGRATIONS.joinToString(",") { "${it.startVersion}→${it.endVersion}" }

    override suspend fun preserveDbBeforeMigration(): BackupResult<File> =
        withContext(dispatcher) {
            val source = liveDatabaseFile()
            if (!source.isFile) return@withContext missingAssetFailure("live database")
            val checkpoint = backupIoResult {
                SQLiteDatabase.openDatabase(
                    source.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                ).use { db ->
                    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                        if (!cursor.moveToFirst()) throw IOException(WAL_CHECKPOINT_NO_ROW)
                        requireCompleteWalCheckpoint(
                            busy = cursor.getLong(0),
                            logFrames = cursor.getLong(1),
                            checkpointedFrames = cursor.getLong(2),
                        )
                    }
                }
            }
            if (checkpoint is BackupResult.Failure) return@withContext checkpoint
            recoveryFiles.publishRecoveryExport(source)
        }

    override fun getRecoveryExportFile(): File? = recoveryFiles.recoveryExportFile()

    override suspend fun createRecoveryExportShareCopy(
        fileName: String,
    ): BackupResult<File> {
        val source = getRecoveryExportFile() ?: return missingAssetFailure("recovery export")
        return recoveryFiles.createShareCopy(source, fileName)
    }

    override suspend fun deleteRecoveryExport(): Boolean = recoveryFiles.deleteRecoveryExport()

    override suspend fun sweepRecoveryFiles(
        state: RestoreProtocolState,
    ): RestoreGarbageCollectionReport = recoveryFiles.sweep(state)

    internal companion object {
        const val CAPACITY_MARGIN_BYTES: Long = 16L * 1024L * 1024L
    }
}

internal suspend fun AppDatabase.checkpointWal() {
    useWriterConnection { connection ->
        connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { statement ->
            if (!statement.step()) throw IOException(WAL_CHECKPOINT_NO_ROW)
            requireCompleteWalCheckpoint(
                busy = statement.getLong(0),
                logFrames = statement.getLong(1),
                checkpointedFrames = statement.getLong(2),
            )
        }
    }
}

internal fun requireCompleteWalCheckpoint(
    busy: Long,
    logFrames: Long,
    checkpointedFrames: Long,
) {
    if (busy != 0L || logFrames != checkpointedFrames) {
        throw IOException(
            "WAL checkpoint incomplete: busy=$busy, " +
                "logFrames=$logFrames, checkpointedFrames=$checkpointedFrames",
        )
    }
}

private const val WAL_CHECKPOINT_NO_ROW: String = "WAL checkpoint returned no result row"

internal suspend fun AppDatabase.readUserVersion(): Int =
    useReaderConnection { connection ->
        connection.usePrepared("PRAGMA user_version") { statement ->
            if (statement.step()) statement.getLong(0).toInt() else 0
        }
    }
