// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
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

/**
 * [SnapshotStorage] over Drive v3 files in the *visible* `drive` space, sibling of
 * `DriveBackupStorage`; owns the `Workeeper/` folder lifecycle and prunes via [RotationPolicy].
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DriveSnapshotStorage @Inject internal constructor(
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

    /** Deletes every `workeeper_export_*` file; no-ops without the `drive.file` grant. */
    override suspend fun deleteAllSnapshots(): BackupResult<Unit> = withContext(dispatcher) {
        if (!accountStore.isDriveFileGranted()) return@withContext BackupResult.Success(Unit)
        runCatching {
            val folderId = existingFolderId() ?: return@runCatching
            val files = withTokenRefreshOn401 {
                driveApi.listFiles(spaces = DRIVE_SPACE, query = exportQuery(folderId))
            }
            files.forEach { file ->
                runCatching { withTokenRefreshOn401 { driveApi.deleteFile(file.id) } }
                    .onFailure { logger.e(it, "deleteAllSnapshots: delete ${file.id} failed") }
            }
        }.fold(
            onSuccess = { BackupResult.Success(Unit) },
            onFailure = { BackupResult.Failure(DriveErrorMapper.toBackupError(it)) },
        )
    }

    /** Cached id if present; else find the oldest existing `Workeeper/` folder, else create one. */
    private suspend fun resolveFolderId(): String {
        accountStore.snapshotFolderId()?.let { return it }
        val id = findFolderInDrive()?.id
            ?: withTokenRefreshOn401 { driveApi.createFolder(SnapshotConstants.FOLDER_NAME) }.id
        accountStore.setSnapshotFolderId(id)
        return id
    }

    /** Cached id, else the oldest existing `Workeeper/` folder, else `null` (no create). */
    private suspend fun existingFolderId(): String? =
        accountStore.snapshotFolderId() ?: findFolderInDrive()?.id

    private suspend fun findFolderInDrive(): DriveFileDto? = withTokenRefreshOn401 {
        driveApi.listFiles(spaces = DRIVE_SPACE, query = FOLDER_QUERY)
    }.minByOrNull { it.createdTime.orEmpty() }

    /** Uploads to [folderId]; a `404` recreates the folder, retries once and returns its id. */
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

    /** Best-effort rotation past [BackupConstants.MAX_BACKUPS]; failures never propagate. */
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
