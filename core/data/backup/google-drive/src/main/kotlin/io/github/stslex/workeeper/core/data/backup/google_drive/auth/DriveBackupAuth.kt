// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveErrorMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `BackupAuth` implementation backed by GMS Identity's `AuthorizationClient` for
 * the scopes declared in [DriveAuthScopes]. Local account + token state lives in
 * [AccountDataStore].
 *
 * The access token returned by `AuthorizationResult.accessToken` is captured at
 * sign-in time (silent `signIn` success path and `completeSignIn` after a
 * resolution flow) and persisted via [AccountDataStore.setToken] so
 * `DriveAuthTokenProvider` serves it on subsequent Drive HTTP calls without
 * issuing a fresh `authorize()`. The cached token is dropped explicitly on
 * `signOut`; revocation goes through `AuthorizationClient.revokeAccess` rather
 * than the OAuth2 revoke HTTP endpoint, because only the GMS path also clears
 * the GMS-local token cache — a server-side-only revoke leaves a stale cached
 * grant that the next silent `signIn` happily reuses and Drive then rejects.
 *
 * Identity (email + display name) comes from a follow-up call to the
 * `oauth2/v3/userinfo` endpoint via [UserInfoFetcher]; `AuthorizationResult`
 * itself only carries the token. Userinfo failures degrade to the
 * `GoogleSignInAccount`-derived email when present, then to a placeholder.
 */
@Singleton
internal class DriveBackupAuth @Inject constructor(
    private val authorizationClient: AuthorizationClient,
    private val accountStore: AccountDataStore,
    private val userInfoFetcher: UserInfoFetcher,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : BackupAuth {

    private val logger = Log.tag(TAG)
    private val authScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow<AuthState>(AuthState.SignedOut)

    override val state: StateFlow<AuthState> = mutableState.asStateFlow()

    init {
        accountStore.observeAccount()
            .map { account -> account?.let(AuthState::SignedIn) ?: AuthState.SignedOut }
            .onEach { mutableState.value = it }
            .launchIn(authScope)
    }

    override suspend fun signIn(): SignInResult = withContext(dispatcher) {
        runCatching {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(DriveAuthScopes.ALL)
                .build()
            authorizationClient.authorize(request).await()
        }
            .onFailure { e ->
                logger.e(e, "signIn failed")
            }
            .fold(
                onSuccess = { result -> resolveSignIn(result) },
                onFailure = { SignInResult.Failure(DriveErrorMapper.toBackupError(it)) },
            )
    }

    override suspend fun completeSignIn(intentData: Intent?): BackupResult<Account> =
        withContext(dispatcher) {
            if (intentData == null) {
                return@withContext BackupResult.Failure(
                    BackupError.Unknown(IllegalStateException("intentData is null")),
                )
            }
            runCatching {
                authorizationClient.getAuthorizationResultFromIntent(intentData)
            }
                .onFailure { e ->
                    logger.e(e, "completeSignIn failed")
                }
                .fold(
                    onSuccess = { result ->
                        val missing = result.missingRequiredScopes()
                        if (missing.isNotEmpty()) {
                            logger.w {
                                "completeSignIn partial grant; missing=$missing"
                            }
                            clearTokenBestEffort(result.accessToken)
                            BackupResult.Failure(BackupError.MissingRequiredScope)
                        } else {
                            captureAccessToken(result)
                            val account = result.toAccount(fetchUserInfo(result.accessToken))
                            accountStore.setAccount(account)
                            BackupResult.Success(account)
                        }
                    },
                    onFailure = { BackupResult.Failure(DriveErrorMapper.toBackupError(it)) },
                )
        }

    /**
     * Revokes Google authorization via `AuthorizationClient.revokeAccess` (which
     * ALSO clears the GMS-local token cache) and clears local account state.
     * Local clear succeeds even if the remote revoke fails (network unavailable,
     * grant already invalid, GMS unavailable). Always returns
     * [BackupResult.Success].
     */
    override suspend fun signOut(): BackupResult<Unit> = withContext(dispatcher) {
        val revokeRequest = RevokeAccessRequest.builder()
            .setScopes(DriveAuthScopes.ALL)
            .build()
        runCatching {
            authorizationClient.revokeAccess(revokeRequest).await()
        }.onFailure { t ->
            logger.w(t) { "revokeAccess failed (best-effort)" }
        }
        accountStore.clearToken()
        accountStore.clear()
        BackupResult.Success(Unit)
    }

    private suspend fun captureAccessToken(result: AuthorizationResult) {
        val token = result.accessToken ?: return
        accountStore.setToken(
            token = token,
            expiresAtEpochMs = System.currentTimeMillis() + TOKEN_TTL_MS,
        )
    }

    private suspend fun fetchUserInfo(accessToken: String?): UserInfo? {
        val token = accessToken ?: return null
        return userInfoFetcher.fetch(token)
    }

    private suspend fun resolveSignIn(result: AuthorizationResult): SignInResult {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: return SignInResult.Failure(
                    BackupError.Unknown(
                        IllegalStateException("hasResolution=true but pendingIntent=null"),
                    ),
                )
            return SignInResult.NeedsResolution(pendingIntent.intentSender)
        }
        val missing = result.missingRequiredScopes()
        if (missing.isNotEmpty()) {
            logger.w { "signIn silent-success partial grant; missing=$missing" }
            clearTokenBestEffort(result.accessToken)
            return SignInResult.PartialGrant(missing)
        }
        captureAccessToken(result)
        val account = result.toAccount(fetchUserInfo(result.accessToken))
        accountStore.setAccount(account)
        return SignInResult.Success(account)
    }

    private fun AuthorizationResult.missingRequiredScopes(): List<String> {
        val granted: List<String> = grantedScopes.orEmpty()
        return DriveAuthScopes.REQUIRED.filterNot { granted.contains(it) }
    }

    private suspend fun clearTokenBestEffort(badToken: String?) {
        if (badToken == null) return
        runCatching {
            authorizationClient
                .clearToken(ClearTokenRequest.builder().setToken(badToken).build())
                .await()
        }.onFailure { t ->
            logger.w(t) { "clearToken on partial grant failed (best-effort)" }
        }
    }

    private fun AuthorizationResult.toAccount(userInfo: UserInfo?): Account {
        val gsa = toGoogleSignInAccount()
        return Account(
            email = userInfo?.email ?: gsa?.email ?: PLACEHOLDER_EMAIL,
            displayName = userInfo?.name ?: gsa?.displayName,
        )
    }

    private companion object {
        const val TAG = "DriveBackupAuth"
        const val PLACEHOLDER_EMAIL = "drive_account"
    }
}
