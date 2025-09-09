package io.github.stslex.workeeper.core.store.core

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class BaseStore internal constructor(
    private val storeProvider: StoreProvider
) {

    private val logger = Log.tag("Store")

    fun getLong(key: String): Flow<Long?> = storeProvider.store.data.map { prefs -> prefs[longPreferencesKey(key)] }

    suspend fun updateLong(key: String, value: Long) {
        logger.i("Update key: $key with value: $value")
        storeProvider.store.edit { prefs ->
            prefs[longPreferencesKey(key)] = value
        }
    }
}