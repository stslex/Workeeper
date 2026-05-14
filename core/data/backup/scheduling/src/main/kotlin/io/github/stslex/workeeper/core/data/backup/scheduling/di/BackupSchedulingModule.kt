package io.github.stslex.workeeper.core.data.backup.scheduling.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.scheduling.BackupPreferencesRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface BackupSchedulingModule {

    @Binds
    @Singleton
    fun bindBackupPreferencesRepository(
        impl: BackupPreferencesRepositoryImpl,
    ): BackupPreferencesRepository
}
