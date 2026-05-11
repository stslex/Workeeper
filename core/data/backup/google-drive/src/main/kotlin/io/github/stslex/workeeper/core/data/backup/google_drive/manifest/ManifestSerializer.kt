package io.github.stslex.workeeper.core.data.backup.google_drive.manifest

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Serializes [BackupManifest] to/from the JSON string stored in
 * `appProperties.manifest` on every Drive backup. Stateless; safe for `@Singleton`
 * consumers.
 *
 * Decode failures collapse to [BackupError.CorruptedBackup] so callers can surface
 * a typed restore error without inspecting the underlying exception.
 */
internal object ManifestSerializer {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun serialize(manifest: BackupManifest): String = json.encodeToString(manifest.toDto())

    fun deserialize(raw: String): BackupResult<BackupManifest> = try {
        BackupResult.Success(json.decodeFromString<BackupManifestDto>(raw).toDomain())
    } catch (e: SerializationException) {
        BackupResult.Failure(
            BackupError.CorruptedBackup(reason = e.message ?: "manifest decode failed"),
        )
    } catch (e: IllegalArgumentException) {
        BackupResult.Failure(
            BackupError.CorruptedBackup(reason = e.message ?: "manifest decode failed"),
        )
    }

    private fun BackupManifest.toDto(): BackupManifestDto = BackupManifestDto(
        appVersion = appVersion,
        dbSchemaVersion = dbSchemaVersion,
        createdAtEpochMs = createdAtEpochMs,
        dbFileSizeBytes = dbFileSizeBytes,
        deviceModel = deviceModel,
    )

    private fun BackupManifestDto.toDomain(): BackupManifest = BackupManifest(
        appVersion = appVersion,
        dbSchemaVersion = dbSchemaVersion,
        createdAtEpochMs = createdAtEpochMs,
        dbFileSizeBytes = dbFileSizeBytes,
        deviceModel = deviceModel,
    )
}
