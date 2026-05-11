package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import io.github.stslex.workeeper.core.data.backup.api.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for the signed-in Drive account (email + display name only —
 * tokens are never stored). Backs `DriveBackupAuth.state`. Naming uses the
 * `DataStore` suffix to match the project's `HiltScopeRule` singleton classifier;
 * the spec called it `AccountStore` but bare "Store" maps to `@HiltViewModel`
 * (MVI store scope).
 */
internal interface AccountDataStore {

    /** Hot stream of the persisted account. Emits `null` when no account is stored. */
    fun observeAccount(): Flow<Account?>

    /** Persists [account]. Overwrites any prior value. */
    suspend fun setAccount(account: Account)

    /** Removes the stored account. No-op when nothing is stored. */
    suspend fun clear()
}
