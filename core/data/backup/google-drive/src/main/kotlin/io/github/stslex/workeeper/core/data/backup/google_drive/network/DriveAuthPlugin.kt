package io.github.stslex.workeeper.core.data.backup.google_drive.network

import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * Ktor client plugin that attaches `Authorization: Bearer <token>` to every
 * outgoing request and surfaces a typed [DriveException.AuthRevoked] when Drive
 * answers 401. The token is fetched per request from [AuthTokenProvider], so
 * fresh tokens take effect without rebuilding the client.
 *
 * Configure via [DriveAuthPluginConfig.authTokenProvider] when installing.
 */
internal val DriveAuthPlugin = createClientPlugin("DriveAuth", ::DriveAuthPluginConfig) {
    val provider = requireNotNull(pluginConfig.authTokenProvider) {
        "DriveAuthPlugin requires authTokenProvider"
    }

    onRequest { request, _ ->
        val token = provider.currentToken()
        if (token != null) {
            request.headers.append(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    onResponse { response ->
        if (response.status == HttpStatusCode.Unauthorized) {
            throw DriveException.AuthRevoked("Drive returned 401 (token revoked or never accepted)")
        }
    }
}
