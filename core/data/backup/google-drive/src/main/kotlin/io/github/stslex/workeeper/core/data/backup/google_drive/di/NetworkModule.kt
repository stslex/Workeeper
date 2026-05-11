package io.github.stslex.workeeper.core.data.backup.google_drive.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.google_drive.network.AuthTokenProvider
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveApi
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveApiImpl
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveAuthPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(
        authTokenProvider: AuthTokenProvider,
    ): HttpClient = HttpClient(Android) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }
        install(DriveAuthPlugin) {
            this.authTokenProvider = authTokenProvider
        }
        defaultRequest {
            url("https://www.googleapis.com/")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindingsModule {

    @Binds
    @Singleton
    fun bindDriveApi(impl: DriveApiImpl): DriveApi
}
