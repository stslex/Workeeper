package io.github.stslex.workeeper.core.data.dataStore.core

import dagger.assisted.AssistedFactory

@AssistedFactory
internal interface DataStoreProviderFactory {

    fun create(name: String): DataStoreProvider
}
