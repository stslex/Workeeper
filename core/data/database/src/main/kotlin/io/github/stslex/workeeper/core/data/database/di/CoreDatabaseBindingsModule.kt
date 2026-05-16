// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProviderImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface CoreDatabaseBindingsModule {

    @Binds
    @Singleton
    fun bindDatabaseSnapshotProvider(
        impl: DatabaseSnapshotProviderImpl,
    ): DatabaseSnapshotProvider
}
