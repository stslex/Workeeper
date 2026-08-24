// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire shape of one file row; only consumed fields are modelled, unknown keys are ignored. */
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

/** Metadata payload for create-file and patch-file; `appProperties` carries the manifest. */
@Serializable
data class DriveFileMetadataDto(
    @SerialName("name") val name: String,
    @SerialName("parents") val parents: List<String>,
    @SerialName("mimeType") val mimeType: String,
    @SerialName("appProperties") val appProperties: Map<String, String>,
)

/** Metadata-only body for create-folder. No `parents` - it lands at the My Drive root. */
@Serializable
internal data class DriveFolderRequestDto(
    @SerialName("name") val name: String,
    @SerialName("mimeType") val mimeType: String,
)
