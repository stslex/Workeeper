package io.github.stslex.workeeper.core.data.dataStore.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// App-Scope Collapse Step 3 (CommonDataStore slice): Metro-native assisted injection (Metro 1.1.1). The
// produced type stays UNSCOPED (Metro forbids scoping an assisted type; the app-scoped singleton lives on
// the consumer `CommonDataStoreImpl`).
//
// `open` (class and property) is the HS6 test seam, not an extension point: the memoizing
// companion below is process-lifetime by design and cannot express "a second process", so the
// persistence gate substitutes a generation-scoped DataStore over one file to simulate
// process death. Production code must not subclass this.
//
// Where the file lives is [DataStorePathResolver]'s job, not this class's — the Android
// implementation delegates to `Context.preferencesDataStoreFile`, so the path is unchanged from
// before the KMP split and no user data moves.
open class DataStoreProvider @AssistedInject constructor(
    @Assisted private val name: String,
    pathResolver: DataStorePathResolver,
) {

    open val dataStore: DataStore<Preferences> = provideDataStore(pathResolver, name)

    private companion object {

        /**
         * Name -> store, memoized for the lifetime of the PROCESS rather than of any DI graph,
         * because that is the lifetime DataStore itself enforces: it keeps a process-global set of
         * open files and throws `IllegalStateException: There are multiple DataStores active for the
         * same file` when a second instance opens one that is already live. Every `@SingleIn(AppScope)`
         * holder is graph-lifetime and would otherwise mint a second store the moment a second graph
         * exists — which is what `MetroTestRule` does per test. Pinned by `app/app` androidTest
         * `AppScopeDataStoreSingletonTest` and `AccountDataStoreSingletonTest`.
         *
         * A compare-and-set loop over an IMMUTABLE map, not a `ConcurrentHashMap` behind a
         * `synchronized` block: neither exists in Kotlin/Native, and `kotlin.concurrent.atomics` is
         * the multiplatform primitive that does (stdlib 2.4.10). Losing a race costs a discarded
         * `DataStore` and nothing else — DataStore opens its file lazily, in
         * `FileStorage.createConnection()` on first read or write, so an instance that never wins the
         * CAS is never opened and holds no file handle to leak. That is the property that makes a
         * lock-free memoize safe here; it is not a general one.
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
