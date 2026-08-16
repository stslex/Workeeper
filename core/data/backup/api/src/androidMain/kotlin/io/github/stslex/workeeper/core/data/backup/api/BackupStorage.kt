// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File

/**
 * Contract for the upload/download/list/delete lifecycle of backup archives on a
 * remote provider. Every call requires an active session — calls made without one
 * return [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.NotAuthenticated].
 *
 * File ownership: the caller owns every [File] passed in and is responsible for
 * deleting it. Impls only read or write to these files and must not delete them.
 */
interface BackupStorage {

    /**
     * Returns a snapshot of every backup currently stored for the signed-in account,
     * newest first. Reflects the provider's view at call time — not a hot stream;
     * callers re-invoke to pick up changes. The returned list is read-only by
     * contract (impls return a fresh list per call; UI layers wrap into an
     * `ImmutableList` at the state boundary if needed).
     */
    suspend fun listBackups(): BackupResult<List<BackupRef>>

    /**
     * Reads [dbFile], uploads it together with [manifest], and returns a
     * [BackupRef] pointing at the new entry. Caller owns [dbFile]; impl does not
     * delete it. Returns a [BackupResult.Failure] when the provider rejected the
     * upload.
     */
    suspend fun uploadBackup(dbFile: File, manifest: BackupManifest): BackupResult<BackupRef>

    /**
     * Downloads the archive identified by [ref] into [target], overwriting any
     * existing file. Caller owns [target] and is responsible for its lifecycle.
     * Returns the [BackupManifest] recovered from the archive on success — callers
     * compare its schema version against the local app schema before deciding to
     * restore.
     */
    suspend fun downloadBackup(ref: BackupRef, target: File): BackupResult<BackupManifest>

    /**
     * Permanently removes the backup identified by [ref] from the provider. The
     * call is idempotent: deleting a [BackupRef] that no longer exists is treated
     * as success.
     */
    suspend fun deleteBackup(ref: BackupRef): BackupResult<Unit>
}
