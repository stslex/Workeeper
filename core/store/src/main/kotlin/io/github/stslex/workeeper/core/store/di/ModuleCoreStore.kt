package io.github.stslex.workeeper.core.store.di

import android.content.Context
import io.github.stslex.workeeper.core.store.core.StoreProvider
import io.github.stslex.workeeper.core.store.store.CommonStore
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("io.github.stslex.workeeper.core.store")
class ModuleCoreStore {

    @Single
    @CommonStoreQualifier
    internal fun bindStoreProvider(context: Context): StoreProvider = StoreProvider(
        context = context,
        name = CommonStore.NAME
    )
}