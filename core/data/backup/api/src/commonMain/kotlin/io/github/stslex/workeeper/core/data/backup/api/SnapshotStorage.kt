// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult

/**
 * Upload contract for the AI-readable JSON snapshot: upload-only, since the app never restores
 * it. Best-effort by policy - a [BackupResult.Failure] must never affect the binary backup.
 */
interface SnapshotStorage {

    /** Uploads [content] (UTF-8 JSON) as a new snapshot, then prunes past the retention cap. */
    suspend fun uploadSnapshot(content: ByteArray): BackupResult<Unit>

    /**
     * Deletes every snapshot file, leaving the empty folder. Call on consent withdrawal and
     * BEFORE sign-out revokes `drive.file`, or the files are stranded permanently.
     */
    suspend fun deleteAllSnapshots(): BackupResult<Unit>
}
