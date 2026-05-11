package io.github.stslex.workeeper.core.data.backup.google_drive.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for [io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest].
 * Lives in the impl module so the api module stays serialization-agnostic.
 */
@Serializable
internal data class BackupManifestDto(
    @SerialName("appVersion") val appVersion: String,
    @SerialName("dbSchemaVersion") val dbSchemaVersion: Int,
    @SerialName("createdAtEpochMs") val createdAtEpochMs: Long,
    @SerialName("dbFileSizeBytes") val dbFileSizeBytes: Long,
    @SerialName("deviceModel") val deviceModel: String? = null,
)
