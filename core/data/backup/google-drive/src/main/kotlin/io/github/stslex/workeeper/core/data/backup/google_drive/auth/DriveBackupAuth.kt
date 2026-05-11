package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.Scope
import io.github.stslex.workeeper.core.core.di.IODispatcher
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
 * the `drive.appdata` scope. Local account state lives in [AccountDataStore];
 * tokens are never stored — see `DriveAuthTokenProvider` for the per-request
 * fetch path.
 *
 * Sign-out is local-only in v1: we clear the account record and let the server
 * token expire naturally (typically <= 60 minutes). Calling Google's `/revoke`
 * endpoint is a follow-up; users can also revoke via Google Account settings.
 */
@Singleton
internal class DriveBackupAuth @Inject constructor(
    private val authorizationClient: AuthorizationClient,
    private val accountStore: AccountDataStore,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : BackupAuth {

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
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
            authorizationClient.authorize(request).await()
        }.fold(
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
                val result = authorizationClient.getAuthorizationResultFromIntent(intentData)
                val account = result.toAccount()
                accountStore.setAccount(account)
                account
            }.fold(
                onSuccess = { BackupResult.Success(it) },
                onFailure = { BackupResult.Failure(DriveErrorMapper.toBackupError(it)) },
            )
        }

    override suspend fun signOut(): BackupResult<Unit> = withContext(dispatcher) {
        accountStore.clear()
        BackupResult.Success(Unit)
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
        val account = result.toAccount()
        accountStore.setAccount(account)
        return SignInResult.Success(account)
    }

    private fun AuthorizationResult.toAccount(): Account {
        val gsa = toGoogleSignInAccount()
        return Account(
            email = gsa?.email ?: PLACEHOLDER_EMAIL,
            displayName = gsa?.displayName,
        )
    }

    private companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val PLACEHOLDER_EMAIL = "drive_account"
    }
}
