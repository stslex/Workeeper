package io.github.stslex.workeeper.core.data.dataStore.core

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// App-Scope Collapse Step 3 (CommonDataStore slice): widened internal -> public because the now-public
// `CommonDataStoreImpl` (Metro `@ContributesBinding`) extends it — Kotlin forbids a public subclass exposing
// an internal supertype. It remains an internal-by-convention helper (never referenced cross-module except
// as CommonDataStoreImpl's base); the D1 cross-module-*Impl lint backstop covers this class of widening.
open class BaseDataStore(
    private val storeProvider: DataStoreProvider,
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

    fun getString(key: String, default: String): Flow<String> =
        storeProvider.dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(key)] ?: default
        }

    suspend fun updateString(key: String, value: String) {
        logger.i("Update key: $key with value: $value")
        storeProvider.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(key)] = value
        }
    }
}
