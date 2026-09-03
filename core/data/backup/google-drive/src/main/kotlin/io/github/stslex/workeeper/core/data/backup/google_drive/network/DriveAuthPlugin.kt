// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * Ktor plugin attaching `Authorization: Bearer <token>` per request, so a fresh token takes
 * effect without rebuilding the client, and mapping a 401 to [DriveException.AuthRevoked].
 */
internal val DriveAuthPlugin = createClientPlugin("DriveAuth", ::DriveAuthPluginConfig) {
    val provider = requireNotNull(pluginConfig.authTokenProvider) {
        "DriveAuthPlugin requires authTokenProvider"
    }

    onRequest { request, _ ->
        if (request.headers.contains(HttpHeaders.Authorization)) return@onRequest
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
