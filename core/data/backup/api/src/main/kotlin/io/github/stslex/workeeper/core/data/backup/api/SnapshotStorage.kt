// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult

/**
 * Upload contract for the AI-readable JSON snapshot. Mirrors [BackupStorage] but for the
 * visible-folder, text artifact, so the orchestration seam depends on this api rather than
 * the Drive impl.
 *
 * The snapshot is a read-only projection that the app never restores, so the surface is
 * upload-only — folder management and rotation are the impl's concern. Best-effort by
 * policy: a [BackupResult.Failure] here is non-fatal and must never affect the binary
 * backup (the orchestration seam swallows it).
 */
interface SnapshotStorage {

    /**
     * Uploads [content] (UTF-8 JSON) as a new snapshot file in the visible folder, then
     * prunes older snapshots beyond the retention cap. Best-effort.
     */
    suspend fun uploadSnapshot(content: ByteArray): BackupResult<Unit>

    /**
     * Deletes every snapshot file from the visible folder, leaving the (now-empty) folder.
     * Invoked when the user withdraws AI-export consent (toggle OFF) and BEFORE sign-out revokes
     * `drive.file` — after revocation the app can no longer see its own visible files, so they
     * would be stranded permanently. Best-effort: a no-op when `drive.file` is not granted (the
     * scope is required to enumerate/delete the visible files), and a [BackupResult.Failure] here
     * is non-fatal.
     */
    suspend fun deleteAllSnapshots(): BackupResult<Unit>
}
