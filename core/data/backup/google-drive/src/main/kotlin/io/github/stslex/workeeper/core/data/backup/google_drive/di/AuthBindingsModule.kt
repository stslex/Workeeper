// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.SnapshotStorage
import io.github.stslex.workeeper.core.data.backup.google_drive.SnapshotExportRunnerImpl
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStore
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStoreImpl
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.DriveAuthTokenProvider
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.DriveBackupAuth
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.DriveTokenInvalidator
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.TokenInvalidator
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.UserInfoFetcher
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.UserInfoFetcherImpl
import io.github.stslex.workeeper.core.data.backup.google_drive.network.AuthTokenProvider
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveApi
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveApiImpl
import io.github.stslex.workeeper.core.data.backup.google_drive.storage.DriveBackupStorage
import io.github.stslex.workeeper.core.data.backup.google_drive.storage.DriveSnapshotStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface AuthBindingsModule {

    @Binds
    @Singleton
    fun bindBackupStorage(impl: DriveBackupStorage): BackupStorage

    @Binds
    @Singleton
    fun bindSnapshotStorage(impl: DriveSnapshotStorage): SnapshotStorage

    @Binds
    @Singleton
    fun bindSnapshotExportRunner(impl: SnapshotExportRunnerImpl): SnapshotExportRunner

    @Binds
    @Singleton
    fun bindDriveApi(impl: DriveApiImpl): DriveApi

    @Binds
    @Singleton
    fun bindBackupAuth(impl: DriveBackupAuth): BackupAuth

    @Binds
    @Singleton
    fun bindAuthTokenProvider(impl: DriveAuthTokenProvider): AuthTokenProvider

    @Binds
    @Singleton
    fun bindAccountDataStore(impl: AccountDataStoreImpl): AccountDataStore

    @Binds
    @Singleton
    fun bindTokenInvalidator(impl: DriveTokenInvalidator): TokenInvalidator

    @Binds
    @Singleton
    fun bindUserInfoFetcher(impl: UserInfoFetcherImpl): UserInfoFetcher
}
