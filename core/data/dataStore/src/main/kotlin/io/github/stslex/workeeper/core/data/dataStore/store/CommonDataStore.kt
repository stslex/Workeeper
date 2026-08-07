package io.github.stslex.workeeper.core.data.dataStore.store

import kotlinx.coroutines.flow.Flow

interface CommonDataStore {

    val homeSelectedStartDate: Flow<Long?>

    val homeSelectedEndDate: Flow<Long?>

    val themePreference: Flow<String>

    /**
     * The Home start card's readout mode (home-start-card.md HS6) — same shape as
     * [themePreference]: a raw stored string with the default baked in, so the mode
     * survives process death and an absent key reads as the default («Неделя»).
     */
    val homeStartCardMode: Flow<String>

    suspend fun setHomeSelectedStartDate(value: Long)

    suspend fun setHomeSelectedEndDate(value: Long)

    suspend fun setThemePreference(value: String)

    suspend fun setHomeStartCardMode(value: String)
}
