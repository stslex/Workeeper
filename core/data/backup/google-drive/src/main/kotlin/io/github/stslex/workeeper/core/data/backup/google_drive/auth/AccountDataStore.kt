// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import io.github.stslex.workeeper.core.data.backup.api.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for the signed-in Drive account and its access-token cache; backs
 * `DriveBackupAuth.state`. The `DataStore` suffix is required by `MetroScopeRule`'s bucket.
 */
interface AccountDataStore {

    /** Hot stream of the persisted account. Emits `null` when no account is stored. */
    fun observeAccount(): Flow<Account?>

    /** One-shot read of the persisted account, or `null` when none is stored. */
    suspend fun account(): Account?

    /** Persists [account]. Overwrites any prior value. */
    suspend fun setAccount(account: Account)

    /** Removes every stored value (account row AND token row). */
    suspend fun clear()

    /** Persists a freshly issued access token with its absolute expiry. */
    suspend fun setToken(token: String, expiresAtEpochMs: Long)

    /** Cached token snapshot, or `null`. Does NOT consult the expiry - callers decide. */
    suspend fun token(): TokenSnapshot?

    /** Removes only the stored token. No-op when no token is stored. */
    suspend fun clearToken()

    /** Cached id of the visible-Drive `Workeeper/` folder, or `null` if not yet resolved. */
    suspend fun snapshotFolderId(): String?

    /** Caches the snapshot folder id; pass `null` to drop a stale id (e.g. after a 404). */
    suspend fun setSnapshotFolderId(id: String?)

    /** Hot stream of whether `drive.file` is currently granted (drives the AI-export toggle UI). */
    fun observeDriveFileGranted(): Flow<Boolean>

    /** One-shot read of the `drive.file` grant flag. */
    suspend fun isDriveFileGranted(): Boolean

    /** Persists the `drive.file` grant flag, re-derived from `grantedScopes` each authorize. */
    suspend fun setDriveFileGranted(granted: Boolean)
}
