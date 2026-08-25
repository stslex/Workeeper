// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

import java.io.File

/**
 * Thin Drive REST surface, one method per endpoint; errors are thrown and mapped by
 * `DriveErrorMapper`. Callers pass the `spaces` and parent ids, so it serves both backup paths.
 */
interface DriveApi {

    /** `GET /drive/v3/files` matching [query] within [spaces]. No pagination. */
    suspend fun listFiles(spaces: String, query: String): List<DriveFileDto>

    /** Multipart create. Caller owns [content]; the impl reads but does not delete it. */
    suspend fun uploadMultipart(metadata: DriveFileMetadataDto, content: File): DriveFileDto

    /** In-memory [ByteArray] overload of [uploadMultipart], for payloads already in memory. */
    suspend fun uploadMultipart(metadata: DriveFileMetadataDto, content: ByteArray): DriveFileDto

    /** `POST /drive/v3/files` creating an empty folder [name] in the user's visible Drive. */
    suspend fun createFolder(name: String): DriveFileDto

    /** `GET /drive/v3/files/{fileId}?alt=media` into [target]. Returns bytes written. */
    suspend fun downloadFile(fileId: String, target: File): Long

    /** `DELETE /drive/v3/files/{fileId}`. */
    suspend fun deleteFile(fileId: String)
}
