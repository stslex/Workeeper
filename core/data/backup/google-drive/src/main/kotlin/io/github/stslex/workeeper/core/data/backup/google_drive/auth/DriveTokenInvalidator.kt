package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DriveTokenInvalidator @Inject constructor(
    private val authorizationClient: AuthorizationClient,
    private val accountStore: AccountDataStore,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : TokenInvalidator {

    private val logger = Log.tag(TAG)

    override suspend fun invalidate() = withContext(dispatcher) {
        val badToken = accountStore.token()?.token
        accountStore.clearToken()
        if (badToken == null) {
            return@withContext
        }
        runCatching {
            authorizationClient
                .clearToken(ClearTokenRequest.builder().setToken(badToken).build())
                .await()
        }.onFailure { t ->
            logger.w(t) { "clearToken failed (best-effort)" }
        }
    }

    private companion object {
        const val TAG = "DriveTokenInvalidator"
    }
}
