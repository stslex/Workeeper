// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

import java.io.File

/**
 * Thin Drive REST surface used by `DriveBackupStorage`. Each method maps to a
 * single Drive endpoint, scoped to `drive.appdata`. Errors are surfaced as
 * thrown exceptions and translated into
 * [io.github.stslex.workeeper.core.data.backup.api.error.BackupError] variants by
 * `DriveErrorMapper`.
 *
 * Authorization headers are injected by the configured `HttpClient` (see
 * `NetworkModule`); callers do not pass tokens.
 */
internal interface DriveApi {

    /**
     * `GET /drive/v3/files` against `appDataFolder` for entries with our naming
     * prefix. Returns the parsed file rows; pagination is not used in v1 (we cap at
     * MAX_BACKUPS = 3, so the request always fits in a single page).
     */
    suspend fun listFiles(): List<DriveFileDto>

    /**
     * `POST /upload/drive/v3/files?uploadType=multipart` with a `multipart/related`
     * body containing the JSON metadata and the binary db payload. Returns the
     * created Drive file row.
     *
     * Caller owns [content]; impl reads but does not delete it.
     */
    suspend fun uploadMultipart(
        metadata: DriveFileMetadataDto,
        content: File,
    ): DriveFileDto

    /**
     * `GET /drive/v3/files/{fileId}?alt=media`. Streams the response body into
     * [target], overwriting it. Returns bytes written for size verification by the
     * caller.
     */
    suspend fun downloadFile(fileId: String, target: File): Long

    /** `DELETE /drive/v3/files/{fileId}`. */
    suspend fun deleteFile(fileId: String)
}
