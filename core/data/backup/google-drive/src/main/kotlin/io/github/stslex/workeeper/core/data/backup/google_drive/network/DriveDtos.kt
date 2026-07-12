// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-level shape of a single file row returned by `GET /drive/v3/files` and the
 * upload + patch endpoints. Only the fields we actually consume are modelled; the
 * Json instance is configured with `ignoreUnknownKeys = true` so extra Drive fields
 * never break decoding.
 *
 * Public solely for cross-module Metro aggregation (App-Scope Collapse Step 3, PF.3): it is named in the
 * [DriveApi] contract, which went public so `DriveApiImpl`'s `@ContributesBinding(AppScope)` aggregates into
 * app/app's `AppGraph`. Not for external use.
 */
@Serializable
data class DriveFileDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("createdTime") val createdTime: String? = null,
    @SerialName("size") val size: String? = null,
    @SerialName("appProperties") val appProperties: Map<String, String>? = null,
)

@Serializable
internal data class DriveFileListDto(
    @SerialName("files") val files: List<DriveFileDto> = emptyList(),
)

/**
 * Metadata payload for the create-file (multipart/related) and patch-file endpoints.
 * `parents` for backup uploads is always `["appDataFolder"]`; `mimeType` is
 * `application/x-sqlite3`; `appProperties` carries the serialized manifest.
 *
 * Public for the same cross-module Metro-aggregation reason as [DriveFileDto] (named in the [DriveApi]
 * contract); not for external use.
 */
@Serializable
data class DriveFileMetadataDto(
    @SerialName("name") val name: String,
    @SerialName("parents") val parents: List<String>,
    @SerialName("mimeType") val mimeType: String,
    @SerialName("appProperties") val appProperties: Map<String, String>,
)

/**
 * Metadata-only body for the create-folder endpoint (`POST /drive/v3/files` with no
 * media). No `parents` — the folder is created at the visible My Drive root.
 */
@Serializable
internal data class DriveFolderRequestDto(
    @SerialName("name") val name: String,
    @SerialName("mimeType") val mimeType: String,
)
