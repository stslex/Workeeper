// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.utils.CommonExt.runIf
import io.github.stslex.workeeper.core.data.backup.google_drive.network.AuthTokenProvider
import io.github.stslex.workeeper.core.data.backup.google_drive.utils.KtorLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `AuthTokenProvider` impl that prefers the cached access token captured at
 * sign-in / `completeSignIn` time (see [AccountDataStore.setToken]) and falls
 * back to a silent `authorize()` only when the cache is empty or expired.
 *
 * Returns `null` (treated as `BackupError.NotAuthenticated` by the network
 * layer) when no account is stored OR when both the cache and the silent
 * `authorize()` fail to yield a usable token. Failure modes are surfaced at
 * warning ([Log.w] on null-token refresh) and error ([Log.e] on `authorize()`
 * throwing) levels only — no debug-level diagnostic logging in production.
 */
@Singleton
internal class DriveAuthTokenProvider @Inject constructor(
    private val authorizationClient: AuthorizationClient,
    private val accountStore: AccountDataStore,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : AuthTokenProvider {

    override suspend fun currentToken(): String? = withContext(dispatcher) {
        if (accountStore.observeAccount().first() == null) {
            return@withContext null
        }
        val cached = accountStore.token()
        if (cached != null && System.currentTimeMillis() < cached.expiresAtEpochMs) {
            return@withContext cached.token
        }
        refreshTokenFromGms()
    }

    // Request ONLY the scopes the account already granted: base, plus drive.file only if
    // it was previously granted. Appdata-only users therefore request exactly
    // DriveAuthScopes.ALL (identical to v1) and never trip an authorize() resolution.
    private suspend fun refreshTokenFromGms(): String? = runCatching {
        val includeDriveFile = accountStore.isDriveFileGranted()
        val scopes = if (includeDriveFile) {
            DriveAuthScopes.ALL_WITH_DRIVE_FILE
        } else {
            DriveAuthScopes.ALL
        }
        authorizeFor(scopes)
            ?: runIf(includeDriveFile) {
                authorizeFor(DriveAuthScopes.ALL)
            }
    }
        .onFailure { t -> Log.tag(KtorLogger.TAG).e(t, "authorize() threw") }
        .getOrNull()

    /**
     * Authorizes [scopes], re-derives + persists the `drive.file` grant from the result's
     * granted scopes (so a revocation flips the flag off on the very next refresh), caches a
     * non-null token, and returns the token (or `null` when resolution is required).
     */
    private suspend fun authorizeFor(scopes: List<Scope>): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes)
            .build()
        val result = authorizationClient.authorize(request).await()
        accountStore.setDriveFileGranted(
            result.grantedScopes.contains(DriveAuthScopes.DRIVE_FILE),
        )
        val token = result.accessToken
        if (token != null) {
            accountStore.setToken(
                token = token,
                expiresAtEpochMs = System.currentTimeMillis() + TOKEN_TTL_MS,
            )
        } else {
            Log.tag(KtorLogger.TAG)
                .w("authorize() returned null token (resolution required or revoked)")
        }
        return token
    }
}
