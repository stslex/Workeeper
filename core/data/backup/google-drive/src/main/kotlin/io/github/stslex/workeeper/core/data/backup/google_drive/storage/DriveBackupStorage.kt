package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.BackupConstants
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveErrorMapper
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestSerializer
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveApi
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveFileMetadataDto
import io.github.stslex.workeeper.core.data.backup.google_drive.storage.DriveFileMapper.MANIFEST_APP_PROPERTY_KEY
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * `BackupStorage` impl over Drive v3 `appdata` files. Mapping (`DriveFileMapper`)
 * and rotation logic (`RotationPolicy`) live in sibling stateless objects so the
 * impl stays focused on orchestration + error mapping.
 */
@Singleton
internal class DriveBackupStorage @Inject constructor(
    private val driveApi: DriveApi,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : BackupStorage {

    private val logger = Log.tag("DriveBackupStorage")

    override suspend fun listBackups(): BackupResult<List<BackupRef>> = withContext(dispatcher) {
        runCatching {
            driveApi.listFiles()
                .mapNotNull(DriveFileMapper::toBackupRef)
                .sortedByDescending { it.manifest.createdAtEpochMs }
        }.fold(
            onSuccess = { BackupResult.Success(it) },
            onFailure = { BackupResult.Failure(DriveErrorMapper.toBackupError(it)) },
        )
    }

    override suspend fun uploadBackup(
        dbFile: File,
        manifest: BackupManifest,
    ): BackupResult<BackupRef> = withContext(dispatcher) {
        runCatching {
            val metadata = DriveFileMetadataDto(
                name = buildBackupName(manifest),
                parents = listOf(APP_DATA_FOLDER),
                mimeType = SQLITE_MIME_TYPE,
                appProperties = mapOf(
                    MANIFEST_APP_PROPERTY_KEY to ManifestSerializer.serialize(manifest),
                ),
            )
            val driveFile = driveApi.uploadMultipart(metadata, dbFile)
            val newRef = BackupRef(remoteId = driveFile.id, manifest = manifest)
            rotate()
            newRef
        }.fold(
            onSuccess = { BackupResult.Success(it) },
            onFailure = { BackupResult.Failure(DriveErrorMapper.toBackupError(it)) },
        )
    }

    override suspend fun downloadBackup(
        ref: BackupRef,
        target: File,
    ): BackupResult<BackupManifest> = withContext(dispatcher) {
        runCatching {
            driveApi.downloadFile(ref.remoteId, target)
        }.fold(
            onSuccess = { written -> verifySize(written, ref.manifest) },
            onFailure = { BackupResult.Failure(DriveErrorMapper.toBackupError(it)) },
        )
    }

    override suspend fun deleteBackup(ref: BackupRef): BackupResult<Unit> =
        withContext(dispatcher) {
            runCatching {
                driveApi.deleteFile(ref.remoteId)
            }.fold(
                onSuccess = { BackupResult.Success(Unit) },
                onFailure = { BackupResult.Failure(DriveErrorMapper.toBackupError(it)) },
            )
        }

    /**
     * Best-effort rotation. Lists current backups after a successful upload and
     * deletes the oldest if we are over [BackupConstants.MAX_BACKUPS]. Failures
     * inside rotation never propagate — the upload itself already succeeded and
     * leftover old backups are a non-blocking housekeeping concern.
     */
    private suspend fun rotate() {
        runCatching {
            val current = driveApi.listFiles().mapNotNull(DriveFileMapper::toBackupRef)
            val toDelete = RotationPolicy.refsToDelete(current, BackupConstants.MAX_BACKUPS)
            toDelete.forEach { ref ->
                runCatching { driveApi.deleteFile(ref.remoteId) }
                    .onFailure { logger.e(it, "rotation: delete ${ref.remoteId} failed") }
            }
        }.onFailure { logger.e(it, "rotation: list failed") }
    }

    private fun verifySize(
        written: Long,
        manifest: BackupManifest,
    ): BackupResult<BackupManifest> {
        val expected = manifest.dbFileSizeBytes
        return if (abs(written - expected) > SIZE_TOLERANCE_BYTES) {
            BackupResult.Failure(
                BackupError.CorruptedBackup(
                    reason = "size mismatch: written=$written expected=$expected",
                ),
            )
        } else {
            BackupResult.Success(manifest)
        }
    }

    private fun buildBackupName(manifest: BackupManifest): String =
        "${BackupConstants.FILE_PREFIX}${manifest.createdAtEpochMs}${BackupConstants.DB_FILE_SUFFIX}"

    private companion object {
        const val APP_DATA_FOLDER = "appDataFolder"
        const val SQLITE_MIME_TYPE = "application/x-sqlite3"
        const val SIZE_TOLERANCE_BYTES = 16L
    }
}
