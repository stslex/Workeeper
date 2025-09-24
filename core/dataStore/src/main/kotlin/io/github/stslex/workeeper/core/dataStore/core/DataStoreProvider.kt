package io.github.stslex.workeeper.core.dataStore.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal class DataStoreProvider(
    context: Context,
    private val name: String,
) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = name)

    val dataStore: DataStore<Preferences> = context.dataStore
}
