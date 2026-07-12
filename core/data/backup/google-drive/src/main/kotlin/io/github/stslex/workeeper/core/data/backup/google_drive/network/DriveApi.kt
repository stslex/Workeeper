// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

import java.io.File

/**
 * Thin Drive REST surface. Each method maps to a single Drive endpoint. Errors are
 * surfaced as thrown exceptions and translated into
 * [io.github.stslex.workeeper.core.data.backup.api.error.BackupError] variants by
 * `DriveErrorMapper`.
 *
 * The surface is **space-aware**: callers pass the Drive `spaces` and parent ids they
 * target, so the same client serves both the binary backup path (`appDataFolder`) and
 * the snapshot path (visible `drive`). Authorization headers are injected by the
 * configured `HttpClient` (see [NetworkBindingContainer]); callers do not pass tokens.
 *
 * Public solely for cross-module Metro aggregation (App-Scope Collapse Step 3, PF.3): `DriveApiImpl` carries
 * `@ContributesBinding(AppScope)`, so this bound interface — and the [DriveFileDto] / [DriveFileMetadataDto]
 * it names in its contract — must be visible to app/app's `AppGraph`. Not for external use (the only
 * consumers are the gd-internal `DriveBackupStorage` / `DriveSnapshotStorage`).
 */
interface DriveApi {

    /**
     * `GET /drive/v3/files` matching [query] within [spaces]. Returns the parsed file
     * rows; pagination is not used in v1 (callers cap their result sets). The binary
     * backup path passes `spaces = "appDataFolder"`; the snapshot path passes
     * `spaces = "drive"`.
     */
    suspend fun listFiles(spaces: String, query: String): List<DriveFileDto>

    /**
     * `POST /upload/drive/v3/files?uploadType=multipart` with a `multipart/related`
     * body containing the JSON [metadata] and the binary payload read from [content].
     * Returns the created Drive file row. Caller owns [content]; impl reads but does
     * not delete it.
     */
    suspend fun uploadMultipart(metadata: DriveFileMetadataDto, content: File): DriveFileDto

    /**
     * In-memory [ByteArray] overload of [uploadMultipart] — same multipart upload for
     * payloads already in memory (e.g. the JSON snapshot). [DriveFileMetadataDto.parents]
     * decides the destination; [DriveFileMetadataDto.mimeType] the content type.
     */
    suspend fun uploadMultipart(metadata: DriveFileMetadataDto, content: ByteArray): DriveFileDto

    /**
     * `POST /drive/v3/files` creating an empty folder named [name] in the user's visible
     * Drive (My Drive root). Used by the snapshot storage to create its `Workeeper/`
     * folder. Returns the created folder row.
     */
    suspend fun createFolder(name: String): DriveFileDto

    /**
     * `GET /drive/v3/files/{fileId}?alt=media`. Streams the response body into [target],
     * overwriting it. Returns bytes written for size verification by the caller.
     */
    suspend fun downloadFile(fileId: String, target: File): Long

    /** `DELETE /drive/v3/files/{fileId}`. */
    suspend fun deleteFile(fileId: String)
}
