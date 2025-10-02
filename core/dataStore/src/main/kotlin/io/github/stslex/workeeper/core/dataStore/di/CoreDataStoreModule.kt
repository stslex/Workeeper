package io.github.stslex.workeeper.core.dataStore.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.dataStore.core.DataStoreProvider
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStoreImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreDataStoreModule {

    @Provides
    @Singleton
    @CommonStoreQualifier
    internal fun provideDataStoreProvider(
        @ApplicationContext context: Context
    ): DataStoreProvider = DataStoreProvider(
        context = context,
        name = CommonDataStore.NAME,
    )
}

@Module
@InstallIn(SingletonComponent::class)
interface CoreDataStoreBindsModule {

    @Binds
    @Singleton
    fun bindCommonDataStore(impl: CommonDataStoreImpl): CommonDataStore
}
