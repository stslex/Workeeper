// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.DriveBackupAuth.Companion.PLACEHOLDER_EMAIL
import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveErrorMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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

    override suspend fun signIn(): SignInResult = authorizeWith(DriveAuthScopes.ALL)

    override suspend fun requestDriveFileAccess(): SignInResult =
        authorizeWith(DriveAuthScopes.ALL_WITH_DRIVE_FILE)

    override fun observeDriveFileGranted(): Flow<Boolean> = accountStore.observeDriveFileGranted()

    /**
     * Shared interactive authorize. [signIn] requests the base [DriveAuthScopes.ALL];
     * [requestDriveFileAccess] adds `drive.file`. Both resolve through [resolveSignIn], which
     * re-derives the `drive.file` grant from the result.
     */
    private suspend fun authorizeWith(scopes: List<Scope>): SignInResult = withContext(dispatcher) {
        runCatching {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(scopes)
                .build()
            authorizationClient.authorize(request).await()
        }
            .onFailure { e ->
                logger.e(e, "authorize failed")
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
                            persistTokenAndGrant(result)
                            val account = resolveAccount(result)
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
            .setScopes(DriveAuthScopes.ALL_WITH_DRIVE_FILE)
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

    /**
     * Persists the authorize result's `drive.file` grant and access token consistently.
     *
     * A success result can carry a newly granted scope but no fresh access token (GMS returns a
     * null token when it deems a cached credential sufficient). The grant is written FIRST, so a
     * concurrent [DriveAuthTokenProvider] refresh that misses the token cache requests the correct
     * scope set. Then: cache a fresh token if one came back; otherwise, if `drive.file` is now
     * granted, drop any cached token — it predates the grant and may be appdata-only, so serving
     * it would 403 the visible-Drive upload until its ~50-min TTL expires. Dropping it forces
     * [DriveAuthTokenProvider] to refresh a `drive.file`-capable token on the next Drive call.
     */
    private suspend fun persistTokenAndGrant(result: AuthorizationResult) {
        val driveFileGranted = result.isDriveFileGranted().also { granted ->
            accountStore.setDriveFileGranted(granted)
        }
        val freshToken = result.accessToken
        when {
            freshToken != null -> accountStore.setToken(
                token = freshToken,
                expiresAtEpochMs = System.currentTimeMillis() + TOKEN_TTL_MS,
            )

            driveFileGranted -> accountStore.clearToken()
        }
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
        persistTokenAndGrant(result)
        val account = resolveAccount(result)
        accountStore.setAccount(account)
        return SignInResult.Success(account)
    }

    /**
     * Resolves the account to persist for a successful authorize result.
     *
     * A real identity (userinfo email, else the `GoogleSignInAccount` email) always wins. When the
     * result carries neither — an incremental `drive.file` grant that GMS satisfies with a cached
     * credential, so `accessToken` is null AND `toGoogleSignInAccount()` is null — falling back to
     * [PLACEHOLDER_EMAIL] would clobber the already signed-in user's email/display name purely from
     * enabling the toggle. So in that case the existing stored account is preserved. Only a truly
     * first-time sign-in with no derivable identity and no prior account reaches the placeholder.
     */
    private suspend fun resolveAccount(result: AuthorizationResult): Account =
        result.toAccountOrNull(fetchUserInfo(result.accessToken))
            ?: accountStore.account()
            ?: Account(email = PLACEHOLDER_EMAIL, displayName = null)

    private fun AuthorizationResult.missingRequiredScopes(): List<String> {
        val granted: List<String> = grantedScopes.orEmpty()
        return DriveAuthScopes.REQUIRED.filterNot { granted.contains(it) }
    }

    private fun AuthorizationResult.isDriveFileGranted(): Boolean =
        grantedScopes.orEmpty().contains(DriveAuthScopes.DRIVE_FILE)

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

    /**
     * Builds an [Account] from a real identity source, or `null` when none is available (no
     * userinfo email and no `GoogleSignInAccount` email). The placeholder fallback lives in
     * [resolveAccount] so callers can first preserve an existing stored identity.
     */
    private fun AuthorizationResult.toAccountOrNull(userInfo: UserInfo?): Account? {
        val gsa = toGoogleSignInAccount()
        val email = userInfo?.email ?: gsa?.email ?: return null
        return Account(email = email, displayName = userInfo?.name ?: gsa?.displayName)
    }

    private companion object {
        const val TAG = "DriveBackupAuth"
        const val PLACEHOLDER_EMAIL = "drive_account"
    }
}
