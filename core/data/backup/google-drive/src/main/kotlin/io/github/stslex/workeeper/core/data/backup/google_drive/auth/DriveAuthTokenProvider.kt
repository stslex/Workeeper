// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.utils.CommonExt.runIf
import io.github.stslex.workeeper.core.data.backup.google_drive.network.AuthTokenProvider
import io.github.stslex.workeeper.core.data.backup.google_drive.utils.KtorLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * `AuthTokenProvider` serving the cached access token, falling back to a silent `authorize()`.
 * Returns `null` — read as `BackupError.NotAuthenticated` — when no usable token can be had.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DriveAuthTokenProvider @Inject internal constructor(
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

    // Request only already-granted scopes; appdata-only users never trip a resolution.
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

    /** Authorizes [scopes], persists the grant, caches the token; `null` if resolution needed. */
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
