// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File

/**
 * Upload/download/list/delete lifecycle of backup archives on a remote provider; every call
 * requires an active session. The caller owns every [File] passed in; impls never delete them.
 */
interface BackupStorage {

    /** Every backup stored for the signed-in account, newest first. Not a hot stream. */
    suspend fun listBackups(): BackupResult<List<BackupRef>>

    /** Reads [dbFile], uploads it with [manifest], and returns a [BackupRef] to the entry. */
    suspend fun uploadBackup(dbFile: File, manifest: BackupManifest): BackupResult<BackupRef>

    /** Downloads [ref] into [target] and returns its [BackupManifest] for a version check. */
    suspend fun downloadBackup(ref: BackupRef, target: File): BackupResult<BackupManifest>

    /** Removes [ref] from the provider. Idempotent: deleting a missing ref is success. */
    suspend fun deleteBackup(ref: BackupRef): BackupResult<Unit>
}
