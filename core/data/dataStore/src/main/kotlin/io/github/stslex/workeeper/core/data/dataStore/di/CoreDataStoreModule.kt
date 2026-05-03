package io.github.stslex.workeeper.core.dataStore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStoreImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface CoreDataStoreModule {

    @Binds
    @Singleton
    fun bindCommonDataStore(impl: CommonDataStoreImpl): CommonDataStore
}
