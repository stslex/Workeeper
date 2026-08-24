// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.manifest

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult

/**
 * Maps [BackupManifest] to/from the Drive `appProperties` map, one entry per field to stay under
 * Drive's per-pair byte limit. Decode failures collapse to [BackupError.CorruptedBackup].
 */
internal object ManifestPropertiesMapper {

    internal const val KEY_APP_VERSION = "app_version"
    internal const val KEY_DB_SCHEMA_VERSION = "db_schema_version"
    internal const val KEY_CREATED_AT_EPOCH_MS = "created_at_epoch_ms"
    internal const val KEY_DB_FILE_SIZE_BYTES = "db_file_size_bytes"
    internal const val KEY_DEVICE_MODEL = "device_model"

    /** Defensive cap on `deviceModel` so the [KEY_DEVICE_MODEL] pair stays under 124 bytes. */
    internal const val DEVICE_MODEL_MAX_LEN = 100

    fun toAppProperties(manifest: BackupManifest): Map<String, String> = buildMap {
        put(KEY_APP_VERSION, manifest.appVersion)
        put(KEY_DB_SCHEMA_VERSION, manifest.dbSchemaVersion.toString())
        put(KEY_CREATED_AT_EPOCH_MS, manifest.createdAtEpochMs.toString())
        put(KEY_DB_FILE_SIZE_BYTES, manifest.dbFileSizeBytes.toString())
        manifest.deviceModel
            ?.take(DEVICE_MODEL_MAX_LEN)
            ?.let { put(KEY_DEVICE_MODEL, it) }
    }

    fun fromAppProperties(properties: Map<String, String>): BackupResult<BackupManifest> {
        val appVersion = properties[KEY_APP_VERSION]
            ?: return missingOrInvalid(KEY_APP_VERSION)
        val dbSchemaVersion = properties[KEY_DB_SCHEMA_VERSION]?.toIntOrNull()
            ?: return missingOrInvalid(KEY_DB_SCHEMA_VERSION)
        val createdAtEpochMs = properties[KEY_CREATED_AT_EPOCH_MS]?.toLongOrNull()
            ?: return missingOrInvalid(KEY_CREATED_AT_EPOCH_MS)
        val dbFileSizeBytes = properties[KEY_DB_FILE_SIZE_BYTES]?.toLongOrNull()
            ?: return missingOrInvalid(KEY_DB_FILE_SIZE_BYTES)
        val deviceModel = properties[KEY_DEVICE_MODEL]
        return BackupResult.Success(
            BackupManifest(
                appVersion = appVersion,
                dbSchemaVersion = dbSchemaVersion,
                createdAtEpochMs = createdAtEpochMs,
                dbFileSizeBytes = dbFileSizeBytes,
                deviceModel = deviceModel,
            ),
        )
    }

    private fun missingOrInvalid(field: String): BackupResult<BackupManifest> =
        BackupResult.Failure(
            BackupError.CorruptedBackup(reason = "manifest field $field missing or invalid"),
        )
}
