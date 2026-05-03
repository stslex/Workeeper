package io.github.stslex.workeeper.core.dataStore.store

import kotlinx.coroutines.flow.Flow

interface CommonDataStore {

    val homeSelectedStartDate: Flow<Long?>

    val homeSelectedEndDate: Flow<Long?>

    val themePreference: Flow<String>

    suspend fun setHomeSelectedStartDate(value: Long)

    suspend fun setHomeSelectedEndDate(value: Long)

    suspend fun setThemePreference(value: String)
}
