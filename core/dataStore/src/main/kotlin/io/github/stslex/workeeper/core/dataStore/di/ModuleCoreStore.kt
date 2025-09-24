package io.github.stslex.workeeper.core.dataStore.di

import android.content.Context
import io.github.stslex.workeeper.core.dataStore.core.DataStoreProvider
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
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
        name = CommonDataStore.NAME,
    )
}
