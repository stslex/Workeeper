// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.scheduling.RestoreStateRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface BackupSchedulingModule {

    // BackupPreferencesRepository: App-Scope Collapse Step 3 (SB1) — migrated to Metro
    // (@ContributesBinding on the impl). Its @Binds was removed here; Hilt readers resolve it via the
    // adopt-back @Provides in AppGraphAdoptBackModule. RestoreStateRepository stays Hilt-owned (later slice).

    @Binds
    @Singleton
    fun bindRestoreStateRepository(
        impl: RestoreStateRepositoryImpl,
    ): RestoreStateRepository
}
