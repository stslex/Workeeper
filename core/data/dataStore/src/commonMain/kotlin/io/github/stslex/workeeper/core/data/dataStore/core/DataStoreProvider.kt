package io.github.stslex.workeeper.core.data.dataStore.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// Metro-native assisted injection; the produced type stays UNSCOPED (Metro forbids scoping an
// assisted type). GUARD: `open` is the HS6 test seam only — production code must not subclass this.
open class DataStoreProvider @AssistedInject constructor(
    @Assisted private val name: String,
    pathResolver: DataStorePathResolver,
) {

    open val dataStore: DataStore<Preferences> = provideDataStore(pathResolver, name)

    private companion object {

        /**
         * Name -> store, memoized for the PROCESS lifetime: DataStore rejects a second live
         * instance over one file. CAS over an immutable map — Native has no ConcurrentHashMap.
         */
        @OptIn(ExperimentalAtomicApi::class)
        private val stores = AtomicReference<Map<String, DataStore<Preferences>>>(emptyMap())

        @OptIn(ExperimentalAtomicApi::class)
        fun provideDataStore(
            pathResolver: DataStorePathResolver,
            name: String,
        ): DataStore<Preferences> {
            while (true) {
                val current = stores.load()
                current[name]?.let { return it }

                val created = PreferenceDataStoreFactory.createWithPath {
                    pathResolver.resolve(name)
                }
                if (stores.compareAndSet(current, current + (name to created))) return created
            }
        }
    }
}
