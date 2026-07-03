// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.storage

/**
 * Pure rotation logic. Given the full set of remote files and a retention cap, decides
 * which to delete (oldest first by [createdAtEpochMs]). Generic over the ref type so the
 * binary backup (`BackupRef`) and the JSON snapshot (`DriveFileDto`) paths share one
 * policy. Stateless; tests live in `RotationPolicyTest`.
 */
internal object RotationPolicy {

    fun <T> refsToDelete(refs: List<T>, max: Int, createdAtEpochMs: (T) -> Long): List<T> {
        if (refs.size <= max) return emptyList()
        return refs
            .sortedBy(createdAtEpochMs)
            .take(refs.size - max)
    }
}
