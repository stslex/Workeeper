package io.github.stslex.workeeper.core.store.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal class StoreProvider(
    context: Context,
    private val name: String
) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = name)

    val store: DataStore<Preferences> = context.dataStore
}