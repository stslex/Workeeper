// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveFileDto

/**
 * Maps a wire-level `DriveFileDto` to the domain `BackupRef`. Returns `null` when
 * the file has no `appProperties` block or the per-field manifest fails to
 * parse — callers (`listBackups`) treat the file as not-a-backup and skip it.
 * Parse failures emit a warning so corrupted entries are visible during
 * verification without breaking the rest of the listing.
 */
internal object DriveFileMapper {

    private val logger = Log.tag("DriveFileMapper")

    fun toBackupRef(file: DriveFileDto): BackupRef? {
        val props = file.appProperties ?: return null
        return when (val result = ManifestPropertiesMapper.fromAppProperties(props)) {
            is BackupResult.Success -> BackupRef(
                remoteId = file.id,
                manifest = result.data,
            )

            is BackupResult.Failure -> {
                val reason = (result.error as? BackupError.CorruptedBackup)?.reason
                    ?: result.error.toString()
                logger.w("skipping ${file.id}: $reason")
                null
            }
        }
    }
}
