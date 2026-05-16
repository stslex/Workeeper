// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ktor-backed `DriveApi` for Drive v3 `appdata`-scoped endpoints. All requests run
 * on [dispatcher]; Authorization headers are injected by `DriveAuthPlugin` on the
 * shared `HttpClient`.
 *
 * Upload + download buffer the db file fully in memory. v1 backups are bounded by
 * `MAX_BACKUPS = 3` and typical workout-app db sizes (single-digit MB), so the
 * trade-off favors implementation simplicity over streaming.
 */
@Singleton
internal class DriveApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : DriveApi {

    private val metadataJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun listFiles(): List<DriveFileDto> = withContext(dispatcher) {
        httpClient
            .get(DRIVE_FILES_URL) {
                parameter("spaces", APP_DATA_FOLDER)
                parameter("q", "name contains '$BACKUP_FILE_PREFIX' and trashed=false")
                parameter("fields", "files(id,name,createdTime,size,appProperties)")
                parameter("pageSize", PAGE_SIZE)
            }
            .body<DriveFileListDto>()
            .files
    }

    override suspend fun uploadMultipart(
        metadata: DriveFileMetadataDto,
        content: File,
    ): DriveFileDto = withContext(dispatcher) {
        val boundary = "workeeper-${System.currentTimeMillis()}"
        val metadataPart = (
            "--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                metadataJson.encodeToString(metadata) +
                "\r\n--$boundary\r\n" +
                "Content-Type: ${metadata.mimeType}\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val closingPart = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val body = metadataPart + content.readBytes() + closingPart

        httpClient
            .post(DRIVE_UPLOAD_URL) {
                parameter("uploadType", "multipart")
                parameter("fields", "id,name,createdTime,size,appProperties")
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(body)
            }
            .body<DriveFileDto>()
    }

    override suspend fun downloadFile(fileId: String, target: File): Long =
        withContext(dispatcher) {
            val bytes = httpClient
                .get("$DRIVE_FILES_URL/$fileId") {
                    parameter("alt", "media")
                }
                .bodyAsBytes()
            target.writeBytes(bytes)
            bytes.size.toLong()
        }

    override suspend fun deleteFile(fileId: String) {
        withContext(dispatcher) {
            httpClient.delete("$DRIVE_FILES_URL/$fileId")
        }
    }

    private companion object {
        const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val APP_DATA_FOLDER = "appDataFolder"
        const val BACKUP_FILE_PREFIX = "app_"
        const val PAGE_SIZE = "100"
    }
}
