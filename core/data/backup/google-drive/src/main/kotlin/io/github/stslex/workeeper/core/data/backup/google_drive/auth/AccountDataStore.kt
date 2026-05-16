// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import io.github.stslex.workeeper.core.data.backup.api.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for the signed-in Drive account and its access-token cache.
 * Backs `DriveBackupAuth.state`. Naming uses the `DataStore` suffix to match the
 * project's `HiltScopeRule` singleton classifier; the spec called it
 * `AccountStore` but bare "Store" maps to `@HiltViewModel` (MVI store scope).
 *
 * Access tokens are persisted as part of the sign-in path
 * (`completeSignIn` / silent `signIn` success) so `DriveAuthTokenProvider` can
 * serve them on subsequent calls without a fresh `authorize()` round-trip. See
 * [TokenSnapshot] for the lifetime contract.
 */
internal interface AccountDataStore {

    /** Hot stream of the persisted account. Emits `null` when no account is stored. */
    fun observeAccount(): Flow<Account?>

    /** Persists [account]. Overwrites any prior value. */
    suspend fun setAccount(account: Account)

    /**
     * Removes every stored value (account row AND token row). [signOut][io.github.stslex.workeeper.core.data.backup.google_drive.auth.DriveBackupAuth.signOut]
     * still calls [clearToken] explicitly so token revocation is a separate step in
     * the audit trail, even though `clear()` would also drop it.
     */
    suspend fun clear()

    /**
     * Persists a freshly issued access token with its absolute expiry. Overwrites
     * any prior value. Callers should set [expiresAtEpochMs] to
     * `System.currentTimeMillis() + TOKEN_TTL_MS`.
     */
    suspend fun setToken(token: String, expiresAtEpochMs: Long)

    /**
     * Returns the cached token snapshot, or `null` when no token is stored. Does
     * NOT consult the expiry — callers decide whether to honour the cache or
     * refresh.
     */
    suspend fun token(): TokenSnapshot?

    /** Removes only the stored token. No-op when no token is stored. */
    suspend fun clearToken()
}
