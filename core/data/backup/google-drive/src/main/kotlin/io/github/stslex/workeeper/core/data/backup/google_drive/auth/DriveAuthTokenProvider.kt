package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.backup.google_drive.network.AuthTokenProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `AuthTokenProvider` impl that asks GMS Identity for a fresh access token on
 * every call when an account is signed in. Returns `null` (treated as
 * `BackupError.NotAuthenticated` by the network layer) when no account is stored.
 *
 * Token fetched on each call; GMS internal cache assumed. See
 * `documentation/tech-debt.md` → "DriveAuthTokenProvider — token fetch caching"
 * for the revisit conditions.
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
        runCatching {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
            authorizationClient.authorize(request).await().accessToken
        }.getOrNull()
    }

    private companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
