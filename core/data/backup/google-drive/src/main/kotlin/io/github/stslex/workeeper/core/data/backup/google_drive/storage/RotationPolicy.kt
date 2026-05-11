package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef

/**
 * Pure rotation logic. Given the full set of remote backups and a retention
 * cap, decides which refs to delete (oldest first by `manifest.createdAtEpochMs`).
 * Stateless; tests live in `RotationPolicyTest`.
 */
internal object RotationPolicy {

    fun refsToDelete(refs: List<BackupRef>, max: Int): List<BackupRef> {
        if (refs.size <= max) return emptyList()
        return refs
            .sortedBy { it.manifest.createdAtEpochMs }
            .take(refs.size - max)
    }
}
