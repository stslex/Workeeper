package io.github.stslex.workeeper.core.data.dataStore.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface CoreDataStoreModule {

    // CommonDataStore: App-Scope Collapse Step 3 (CommonDataStore slice) — migrated to Metro (AppGraph owns
    // it via @ContributesBinding(AppScope) on CommonDataStoreImpl). Its @Binds was removed here; the 3
    // still-Hilt readers (AppRootViewModel + settings EntryPoint/Graph) resolve via the adopt-back @Provides
    // in AppGraphAdoptBackModule.
}
