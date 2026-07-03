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
 * Ktor-backed `DriveApi` for Drive v3 endpoints. All requests run on [dispatcher];
 * Authorization headers are injected by `DriveAuthPlugin` on the shared `HttpClient`.
 *
 * Upload + download buffer the payload fully in memory. v1 backups/snapshots are
 * bounded by `MAX_BACKUPS = 3` and small db/JSON sizes, so the trade-off favors
 * implementation simplicity over streaming.
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

    override suspend fun listFiles(spaces: String, query: String): List<DriveFileDto> =
        withContext(dispatcher) {
            httpClient
                .get(DRIVE_FILES_URL) {
                    parameter("spaces", spaces)
                    parameter("q", query)
                    parameter("fields", "files(id,name,createdTime,size,appProperties)")
                    parameter("pageSize", PAGE_SIZE)
                }
                .body<DriveFileListDto>()
                .files
        }

    override suspend fun uploadMultipart(
        metadata: DriveFileMetadataDto,
        content: File,
    ): DriveFileDto = withContext(dispatcher) { postMultipart(metadata, content.readBytes()) }

    override suspend fun uploadMultipart(
        metadata: DriveFileMetadataDto,
        content: ByteArray,
    ): DriveFileDto = withContext(dispatcher) { postMultipart(metadata, content) }

    override suspend fun createFolder(name: String): DriveFileDto = withContext(dispatcher) {
        val body = metadataJson
            .encodeToString(DriveFolderRequestDto(name = name, mimeType = FOLDER_MIME_TYPE))
            .toByteArray(Charsets.UTF_8)
        httpClient
            .post(DRIVE_FILES_URL) {
                parameter("fields", "id,name,createdTime")
                contentType(ContentType.Application.Json)
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

    /**
     * Builds a `multipart/related` body (JSON metadata part + raw payload) and POSTs it.
     * Shared by both [uploadMultipart] overloads; the binary path reads its file into
     * [content] first, so its on-the-wire request is byte-for-byte what it was before.
     */
    private suspend fun postMultipart(
        metadata: DriveFileMetadataDto,
        content: ByteArray,
    ): DriveFileDto {
        val boundary = "workeeper-${System.currentTimeMillis()}"
        val metadataPart = (
            "--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                metadataJson.encodeToString(metadata) +
                "\r\n--$boundary\r\n" +
                "Content-Type: ${metadata.mimeType}\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val closingPart = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val body = metadataPart + content + closingPart

        return httpClient
            .post(DRIVE_UPLOAD_URL) {
                parameter("uploadType", "multipart")
                parameter("fields", "id,name,createdTime,size,appProperties")
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(body)
            }
            .body<DriveFileDto>()
    }

    private companion object {
        const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val PAGE_SIZE = "100"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }
}
