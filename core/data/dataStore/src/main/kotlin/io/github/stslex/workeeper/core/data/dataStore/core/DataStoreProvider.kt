package io.github.stslex.workeeper.core.data.dataStore.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap

internal class DataStoreProvider @AssistedInject constructor(
    @Assisted private val name: String,
    @ApplicationContext context: Context,
) {

    val dataStore: DataStore<Preferences> = provideDataStore(context.applicationContext, name)

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
