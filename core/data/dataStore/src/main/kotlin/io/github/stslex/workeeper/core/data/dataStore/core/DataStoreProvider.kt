package io.github.stslex.workeeper.core.data.dataStore.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import java.util.concurrent.ConcurrentHashMap

// App-Scope Collapse Step 3 (CommonDataStore slice): Metro-native assisted injection (Metro 1.1.1). The
// `dagger.assisted.*` trio was converted to `dev.zacsweers.metro.*`; Metro generates the factory impl for
// the hand-written [DataStoreProviderFactory]. The produced type stays UNSCOPED (Metro forbids scoping an
// assisted type; the app-scoped singleton lives on the consumer `CommonDataStoreImpl`). The `Context`
// dropped from Hilt's `@ApplicationContext` to a plain param resolved from the app graph's
// `create(applicationContext)` bound instance.
// `open` (class and property) is the HS6 test seam, not an extension point: the memoizing
// companion below is process-lifetime by design and cannot express "a second process", so the
// persistence gate substitutes a generation-scoped DataStore over one file to simulate
// process death. Production code must not subclass this.
open class DataStoreProvider @AssistedInject constructor(
    @Assisted private val name: String,
    context: Context,
) {

    open val dataStore: DataStore<Preferences> = provideDataStore(context.applicationContext, name)

    private companion object {

        private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()
        private val lock = Any()

        fun provideDataStore(context: Context, name: String): DataStore<Preferences> {
            stores[name]?.let { return it }

            synchronized(lock) {
                stores[name]?.let { return it }

                val dataStore = PreferenceDataStoreFactory.create {
                    context.preferencesDataStoreFile(name)
                }

                stores[name] = dataStore
                return dataStore
            }
        }
    }
}
