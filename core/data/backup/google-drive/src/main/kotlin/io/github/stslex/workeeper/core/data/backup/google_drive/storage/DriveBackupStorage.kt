// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
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
import kotlin.math.abs

/**
 * [BackupStorage] over Drive v3 `appdata` files; mapping and rotation live in sibling stateless
 * objects. Every call goes through [withTokenRefreshOn401].
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DriveBackupStorage @Inject internal constructor(
    private val driveApi: DriveApi,
    private val tokenInvalidator: TokenInvalidator,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : BackupStorage {

    private val logger = Log.tag("DriveBackupStorage")

    override suspend fun listBackups(): BackupResult<List<BackupRef>> = withContext(dispatcher) {
        runCatching {
            withTokenRefreshOn401 { driveApi.listFiles(spaces = APP_DATA_FOLDER, query = LIST_QUERY) }
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

    /** Best-effort rotation past [BackupConstants.MAX_BACKUPS]; failures never propagate. */
    private suspend fun rotate() {
        runCatching {
            val current = withTokenRefreshOn401 { driveApi.listFiles(spaces = APP_DATA_FOLDER, query = LIST_QUERY) }
                .mapNotNull(DriveFileMapper::toBackupRef)
            val toDelete = RotationPolicy.refsToDelete(current, BackupConstants.MAX_BACKUPS) {
                it.manifest.createdAtEpochMs
            }
            toDelete.forEach { ref ->
                runCatching { withTokenRefreshOn401 { driveApi.deleteFile(ref.remoteId) } }
                    .onFailure { logger.e(it, "rotation: delete ${ref.remoteId} failed") }
            }
        }.onFailure { logger.e(it, "rotation: list failed") }
    }

    /** Runs [block]; a 401 clears the cached bearer token and retries once, then propagates. */
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

        /** `appDataFolder` listing query for our backup files (mirrors the upload naming). */
        const val LIST_QUERY = "name contains '${BackupConstants.FILE_PREFIX}' and trashed=false"
    }
}
