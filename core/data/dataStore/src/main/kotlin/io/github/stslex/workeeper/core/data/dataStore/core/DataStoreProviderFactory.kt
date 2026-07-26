package io.github.stslex.workeeper.core.data.dataStore.core

import dev.zacsweers.metro.AssistedFactory

// App-Scope Collapse Step 3 (CommonDataStore slice): Metro-native `@AssistedFactory` (converted from
// `dagger.assisted`). The interface stays hand-written; Metro generates its impl (generateAssistedFactories
// is left off — explicit over magic). Injected wherever the graph needs to mint a named [DataStoreProvider].
@AssistedFactory
interface DataStoreProviderFactory {

    fun create(name: String): DataStoreProvider
}
