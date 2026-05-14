package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
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

    private suspend fun refreshTokenFromGms(): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(DriveAuthScopes.ALL)
            .build()
        val result = try {
            authorizationClient.authorize(request).await()
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            Log.tag(KtorLogger.TAG).e(t, "authorize() threw")
            return null
        }
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
