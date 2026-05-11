package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestSerializer
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveFileDto

/**
 * Maps a wire-level `DriveFileDto` to the domain `BackupRef`. Returns `null` when
 * the file lacks the `appProperties.manifest` key or the manifest JSON fails to
 * parse — callers (`listBackups`) treat the file as not-a-backup and skip it.
 */
internal object DriveFileMapper {

    const val MANIFEST_APP_PROPERTY_KEY = "manifest"

    fun toBackupRef(file: DriveFileDto): BackupRef? {
        val raw = file.appProperties?.get(MANIFEST_APP_PROPERTY_KEY) ?: return null
        return when (val result = ManifestSerializer.deserialize(raw)) {
            is BackupResult.Success -> BackupRef(
                remoteId = file.id,
                manifest = result.data,
            )

            is BackupResult.Failure -> null
        }
    }
}
