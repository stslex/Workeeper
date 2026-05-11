package io.github.stslex.workeeper.core.data.backup.google_drive.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.google_drive.storage.DriveBackupStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface StorageModule {

    @Binds
    @Singleton
    fun bindBackupStorage(impl: DriveBackupStorage): BackupStorage
}
