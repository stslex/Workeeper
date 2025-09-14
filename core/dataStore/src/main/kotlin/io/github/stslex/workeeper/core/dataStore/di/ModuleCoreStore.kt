package io.github.stslex.workeeper.core.store.di

import android.content.Context
import io.github.stslex.workeeper.core.store.core.DataStoreProvider
import io.github.stslex.workeeper.core.store.store.CommonDataStore
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("io.github.stslex.workeeper.core.dataStore")
class ModuleCoreStore {

    @Single
    @CommonStoreQualifier
    internal fun bindStoreProvider(context: Context): DataStoreProvider = DataStoreProvider(
        context = context,
        name = CommonDataStore.NAME
    )
}