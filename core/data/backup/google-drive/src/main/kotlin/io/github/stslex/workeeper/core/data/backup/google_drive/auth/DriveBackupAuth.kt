// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolution
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolutionOutcome
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.DriveBackupAuth.Companion.PLACEHOLDER_EMAIL
import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveErrorMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * `BackupAuth` backed by GMS Identity's `AuthorizationClient` for the [DriveAuthScopes]; account
 * and token state live in [AccountDataStore]. See documentation/feature-specs/backup.md.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DriveBackupAuth @Inject internal constructor(
    private val authorizationClient: AuthorizationClient,
    private val accountStore: AccountDataStore,
    private val userInfoFetcher: UserInfoFetcher,
    lifetime: AppScopeLifetime,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : BackupAuth {

    private val logger = Log.tag(TAG)

    // Generation-owned: the account-mirror collector dies with its generation, not the process.
    private val authScope = lifetime.childScope(dispatcher)
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

    /** Shared interactive authorize; [resolveSignIn] re-derives the `drive.file` grant. */
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

    override suspend fun completeSignIn(outcome: AuthResolutionOutcome): BackupResult<Account> =
        withContext(dispatcher) {
            // A null/non-Intent payload from the mvi edge means a cancelled resolution.
            val intentData = outcome.platform as? Intent
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
     * Revokes via `AuthorizationClient.revokeAccess`, which also clears the GMS token cache.
     * Local clear succeeds even when the remote revoke fails; always returns success.
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
     * Persists the `drive.file` grant first, then caches a fresh token or drops a pre-grant one
     * that may be appdata-only. See documentation/feature-specs/backup.md.
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
            return SignInResult.NeedsResolution(AuthResolution(pendingIntent.intentSender))
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
     * Account for a successful authorize: real identity, else the stored account, else
     * [PLACEHOLDER_EMAIL]. See documentation/feature-specs/backup.md.
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

    /** Builds an [Account] from a real identity source, or `null` when none is available. */
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
