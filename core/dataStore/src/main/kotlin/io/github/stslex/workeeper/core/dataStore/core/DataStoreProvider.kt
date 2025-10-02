package io.github.stslex.workeeper.core.dataStore.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext

class DataStoreProvider @AssistedInject constructor(
    @Assisted private val name: String,
    @ApplicationContext context: Context,
) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = name)

    val dataStore: DataStore<Preferences> = context.dataStore
}
