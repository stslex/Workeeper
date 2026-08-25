// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.backup.google_drive.network.AuthTokenProvider
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveAuthPlugin
import io.github.stslex.workeeper.core.data.backup.google_drive.utils.KtorLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Provides the Drive `HttpClient` into the app graph without app/app naming the ktor type.
 * GUARD: an `internal` container silently fails cross-module aggregation — keep it public.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object NetworkBindingContainer {

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(authTokenProvider: AuthTokenProvider): HttpClient = HttpClient(Android) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }
        install(Logging) {
            logger = KtorLogger
            level = LogLevel.ALL
        }
        install(DriveAuthPlugin) {
            this.authTokenProvider = authTokenProvider
        }
        defaultRequest {
            url("https://www.googleapis.com/")
        }
    }
}
