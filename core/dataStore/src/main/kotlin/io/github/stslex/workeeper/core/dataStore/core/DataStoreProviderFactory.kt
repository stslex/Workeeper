package io.github.stslex.workeeper.core.dataStore.core

import dagger.assisted.AssistedFactory

@AssistedFactory
interface DataStoreProviderFactory {

    fun create(name: String): DataStoreProvider
}
