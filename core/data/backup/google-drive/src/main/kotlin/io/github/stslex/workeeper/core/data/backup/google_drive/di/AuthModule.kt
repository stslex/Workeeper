package io.github.stslex.workeeper.core.data.backup.google_drive.di

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.Identity
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStore
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStoreImpl
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.DriveAuthTokenProvider
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.DriveBackupAuth
import io.github.stslex.workeeper.core.data.backup.google_drive.network.AuthTokenProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AuthProvidersModule {

    @Provides
    @Singleton
    fun provideAuthorizationClient(
        @ApplicationContext context: Context,
    ): AuthorizationClient = Identity.getAuthorizationClient(context)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface AuthBindingsModule {

    @Binds
    @Singleton
    fun bindBackupAuth(impl: DriveBackupAuth): BackupAuth

    @Binds
    @Singleton
    fun bindAuthTokenProvider(impl: DriveAuthTokenProvider): AuthTokenProvider

    @Binds
    @Singleton
    fun bindAccountDataStore(impl: AccountDataStoreImpl): AccountDataStore
}
