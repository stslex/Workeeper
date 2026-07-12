// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class UserInfoFetcherImpl @Inject internal constructor(
    private val httpClient: HttpClient,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : UserInfoFetcher {

    private val logger = Log.tag(TAG)

    override suspend fun fetch(accessToken: String): UserInfo? = withContext(dispatcher) {
        runCatching {
            val dto: UserInfoDto = httpClient
                .get(USERINFO_URL) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                .body()
            UserInfo(email = dto.email, name = dto.name)
        }
            .onFailure { t -> logger.w(t) { "userinfo fetch failed (best-effort)" } }
            .getOrNull()
    }

    @Serializable
    private data class UserInfoDto(
        @SerialName("email") val email: String? = null,
        @SerialName("name") val name: String? = null,
    )

    private companion object {
        const val TAG = "UserInfoFetcher"
        const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
    }
}
