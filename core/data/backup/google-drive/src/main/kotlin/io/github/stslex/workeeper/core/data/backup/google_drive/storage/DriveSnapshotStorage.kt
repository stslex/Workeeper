// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.BackupConstants
import io.github.stslex.workeeper.core.data.backup.api.SnapshotConstants
import io.github.stslex.workeeper.core.data.backup.api.SnapshotStorage
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStore
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.TokenInvalidator
import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveErrorMapper
import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveException
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveApi
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveFileDto
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveFileMetadataDto
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `SnapshotStorage` impl over Drive v3 files in the *visible* `drive` space — the sibling
 * of `DriveBackupStorage` (which targets the hidden `appDataFolder`). It owns the
 * `Workeeper/` folder lifecycle (create-or-lookup + id cache), uploads the JSON bytes, and
 * prunes old snapshots via the shared [RotationPolicy].
 *
 * 401 handling mirrors `DriveBackupStorage` ([withTokenRefreshOn401]); a stale cached
 * folder id surfaces as a `404` on upload, which is recovered once (recreate + retry).
 */
@Singleton
internal class DriveSnapshotStorage @Inject constructor(
    private val driveApi: DriveApi,
    private val accountStore: AccountDataStore,
    private val tokenInvalidator: TokenInvalidator,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : SnapshotStorage {

    private val logger = Log.tag("DriveSnapshotStorage")

    override suspend fun uploadSnapshot(content: ByteArray): BackupResult<Unit> =
        withContext(dispatcher) {
            runCatching {
                val name = SnapshotConstants.FILE_PREFIX +
                    System.currentTimeMillis() +
                    SnapshotConstants.FILE_SUFFIX
                val folderId = resolveFolderId()
                val effectiveFolderId = uploadWithFolderRecovery(name, content, folderId)
                rotate(effectiveFolderId)
            }.fold(
                onSuccess = { BackupResult.Success(Unit) },
                onFailure = { BackupResult.Failure(DriveErrorMapper.toBackupError(it)) },
            )
        }

    /** Cached id if present; else find the oldest existing `Workeeper/` folder, else create one. */
    private suspend fun resolveFolderId(): String {
        accountStore.snapshotFolderId()?.let { return it }
        val existing = withTokenRefreshOn401 {
            driveApi.listFiles(spaces = DRIVE_SPACE, query = FOLDER_QUERY)
        }.minByOrNull { it.createdTime.orEmpty() }
        val id = existing?.id
            ?: withTokenRefreshOn401 { driveApi.createFolder(SnapshotConstants.FOLDER_NAME) }.id
        accountStore.setSnapshotFolderId(id)
        return id
    }

    /**
     * Uploads to [folderId]; if the folder is gone (`404`, e.g. user trashed it), drops the
     * cached id, recreates the folder, and retries once. Returns the folder id actually
     * uploaded into, so rotation prunes the right folder.
     */
    private suspend fun uploadWithFolderRecovery(
        name: String,
        content: ByteArray,
        folderId: String,
    ): String = try {
        withTokenRefreshOn401 { driveApi.uploadMultipart(metadata(name, folderId), content) }
        folderId
    } catch (notFound: ClientRequestException) {
        if (notFound.response.status != HttpStatusCode.NotFound) throw notFound
        logger.w(notFound) { "snapshot folder $folderId missing — recreating and retrying once" }
        accountStore.setSnapshotFolderId(null)
        val newId = withTokenRefreshOn401 { driveApi.createFolder(SnapshotConstants.FOLDER_NAME) }.id
        accountStore.setSnapshotFolderId(newId)
        withTokenRefreshOn401 { driveApi.uploadMultipart(metadata(name, newId), content) }
        newId
    }

    /**
     * Best-effort rotation: list snapshots in [folderId] and delete the oldest beyond
     * [BackupConstants.MAX_BACKUPS]. Failures never propagate — the upload already
     * succeeded and leftover files are non-blocking housekeeping.
     */
    private suspend fun rotate(folderId: String) {
        runCatching {
            val files = withTokenRefreshOn401 {
                driveApi.listFiles(spaces = DRIVE_SPACE, query = exportQuery(folderId))
            }
            RotationPolicy.refsToDelete(files, BackupConstants.MAX_BACKUPS) { it.createdAtEpochMs() }
                .forEach { file ->
                    runCatching { withTokenRefreshOn401 { driveApi.deleteFile(file.id) } }
                        .onFailure { logger.e(it, "snapshot rotation: delete ${file.id} failed") }
                }
        }.onFailure { logger.e(it, "snapshot rotation: list failed") }
    }

    private fun metadata(name: String, folderId: String): DriveFileMetadataDto =
        DriveFileMetadataDto(
            name = name,
            parents = listOf(folderId),
            mimeType = JSON_MIME_TYPE,
            appProperties = emptyMap(),
        )

    /** Recovers the creation epoch from the `workeeper_export_<epochMs>.json` name. */
    private fun DriveFileDto.createdAtEpochMs(): Long = name
        .removePrefix(SnapshotConstants.FILE_PREFIX)
        .removeSuffix(SnapshotConstants.FILE_SUFFIX)
        .toLongOrNull() ?: 0L

    private suspend fun <T> withTokenRefreshOn401(block: suspend () -> T): T = try {
        block()
    } catch (firstFailure: DriveException.AuthRevoked) {
        logger.w(firstFailure) { "401 — invalidating token and retrying once" }
        tokenInvalidator.invalidate()
        block()
    }

    private companion object {
        const val DRIVE_SPACE = "drive"
        const val JSON_MIME_TYPE = "application/json"

        val FOLDER_QUERY = "mimeType='application/vnd.google-apps.folder' and " +
            "name='${SnapshotConstants.FOLDER_NAME}' and trashed=false"

        fun exportQuery(folderId: String): String = "'$folderId' in parents and " +
            "name contains '${SnapshotConstants.FILE_PREFIX}' and trashed=false"
    }
}
