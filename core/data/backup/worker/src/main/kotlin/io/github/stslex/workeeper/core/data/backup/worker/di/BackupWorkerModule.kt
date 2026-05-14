package io.github.stslex.workeeper.core.data.backup.worker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.worker.scheduler.BackupScheduler

@Module
@InstallIn(SingletonComponent::class)
internal interface BackupWorkerModule {

    @Binds
    fun bindAutoBackupController(impl: BackupScheduler): AutoBackupController
}
