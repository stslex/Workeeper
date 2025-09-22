package io.github.stslex.workeeper.core.dataStore.core

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class BaseDataStore internal constructor(
    private val storeProvider: DataStoreProvider
) {

    private val logger = Log.tag("DataStore")

    fun getLong(key: String): Flow<Long?> = storeProvider.dataStore.data.map { prefs ->
        prefs[longPreferencesKey(key)]
    }

    suspend fun updateLong(key: String, value: Long) {
        logger.i("Update key: $key with value: $value")
        storeProvider.dataStore.edit { prefs ->
            prefs[longPreferencesKey(key)] = value
        }
    }
}