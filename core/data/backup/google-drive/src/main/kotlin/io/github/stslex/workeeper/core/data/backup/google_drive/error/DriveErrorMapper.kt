// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.error

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Translates Ktor / IO / serialization exceptions into the typed
 * [BackupError] variants the api module declares. Stateless `object`; the
 * `Mapper`-named class is intentionally NOT `@Inject`-constructed so the
 * project's `MetroScopeRule` does not demand a `@SingleIn` scope (the rule only
 * scope-checks constructor-`@Inject` classes).
 */
internal object DriveErrorMapper {

    private const val REASON_QUOTA_EXCEEDED = "quotaExceeded"
    private const val REASON_USER_RATE_LIMIT = "userRateLimitExceeded"

    @Suppress("ReturnCount")
    fun toBackupError(t: Throwable): BackupError = when (t) {
        is DriveException.NotAuthenticated -> BackupError.NotAuthenticated
        is DriveException.AuthRevoked -> BackupError.AuthRevoked
        is DriveException.QuotaExceeded -> BackupError.StorageQuotaExceeded
        is DriveException.Forbidden -> BackupError.AuthRevoked
        is ClientRequestException -> mapClientResponse(t)
        is ServerResponseException -> BackupError.Io(t)
        is SerializationException -> BackupError.CorruptedBackup(
            reason = t.message ?: "serialization failed",
        )
        is IOException -> BackupError.NetworkUnavailable
        else -> BackupError.Unknown(t)
    }

    private fun mapClientResponse(t: ClientRequestException): BackupError {
        val status = t.response.status
        if (status == HttpStatusCode.Unauthorized) {
            return BackupError.AuthRevoked
        }
        if (status == HttpStatusCode.Forbidden) {
            val body = runCatching { t.message }.getOrNull().orEmpty()
            return if (body.contains(REASON_QUOTA_EXCEEDED) || body.contains(REASON_USER_RATE_LIMIT)) {
                BackupError.StorageQuotaExceeded
            } else {
                BackupError.AuthRevoked
            }
        }
        return BackupError.Io(t)
    }
}
