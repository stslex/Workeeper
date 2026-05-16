// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.BackupConstants
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.TokenInvalidator
import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveErrorMapper
import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveException
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveApi
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveFileMetadataDto
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
 *
 * Every Drive HTTP call goes through [withTokenRefreshOn401], which catches the
 * typed `DriveException.AuthRevoked` raised by `DriveAuthPlugin` on a 401
 * response, invalidates both the DataStore-cached and GMS-cached bearer tokens,
 * and retries the call once. The retry uses a freshly-issued token from
 * `authorize()`; a second 401 propagates and maps to `BackupError.AuthRevoked`.
 */
@Singleton
internal class DriveBackupStorage @Inject constructor(
    private val driveApi: DriveApi,
    private val tokenInvalidator: TokenInvalidator,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : BackupStorage {

    private val logger = Log.tag("DriveBackupStorage")

    override suspend fun listBackups(): BackupResult<List<BackupRef>> = withContext(dispatcher) {
        runCatching {
            withTokenRefreshOn401 { driveApi.listFiles() }
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
                appProperties = ManifestPropertiesMapper.toAppProperties(manifest),
            )
            val driveFile = withTokenRefreshOn401 { driveApi.uploadMultipart(metadata, dbFile) }
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
            withTokenRefreshOn401 { driveApi.downloadFile(ref.remoteId, target) }
        }.fold(
            onSuccess = { written -> verifySize(written, ref.manifest) },
            onFailure = { BackupResult.Failure(DriveErrorMapper.toBackupError(it)) },
        )
    }

    override suspend fun deleteBackup(ref: BackupRef): BackupResult<Unit> =
        withContext(dispatcher) {
            runCatching {
                withTokenRefreshOn401 { driveApi.deleteFile(ref.remoteId) }
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
            val current = withTokenRefreshOn401 { driveApi.listFiles() }
                .mapNotNull(DriveFileMapper::toBackupRef)
            val toDelete = RotationPolicy.refsToDelete(current, BackupConstants.MAX_BACKUPS)
            toDelete.forEach { ref ->
                runCatching { withTokenRefreshOn401 { driveApi.deleteFile(ref.remoteId) } }
                    .onFailure { logger.e(it, "rotation: delete ${ref.remoteId} failed") }
            }
        }.onFailure { logger.e(it, "rotation: list failed") }
    }

    /**
     * Runs [block]; on `DriveException.AuthRevoked` (Drive returned 401), clears
     * the bearer token from both the DataStore cache and the GMS local cache,
     * then retries [block] once. A second AuthRevoked propagates — the upper
     * layer maps it to `BackupError.AuthRevoked`.
     */
    private suspend fun <T> withTokenRefreshOn401(block: suspend () -> T): T = try {
        block()
    } catch (firstFailure: DriveException.AuthRevoked) {
        logger.w(firstFailure) { "401 — invalidating token and retrying once" }
        tokenInvalidator.invalidate()
        block()
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
