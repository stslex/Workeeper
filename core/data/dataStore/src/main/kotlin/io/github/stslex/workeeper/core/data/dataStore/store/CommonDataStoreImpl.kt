package io.github.stslex.workeeper.core.data.dataStore.store

import io.github.stslex.workeeper.core.data.dataStore.core.BaseDataStore
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommonDataStoreImpl @Inject internal constructor(
    storeFactory: DataStoreProviderFactory,
) : CommonDataStore, BaseDataStore(
    storeFactory.create(NAME),
) {

    override var homeSelectedStartDate: Flow<Long?> = getLong(KEY_HOME_SELECTED_START_DATE)

    override var homeSelectedEndDate: Flow<Long?> = getLong(KEY_HOME_SELECTED_END_DATE)

    override val themePreference: Flow<String> = getString(KEY_THEME_PREFERENCE, DEFAULT_THEME)

    override suspend fun setHomeSelectedStartDate(value: Long) {
        updateLong(KEY_HOME_SELECTED_START_DATE, value)
    }

    override suspend fun setHomeSelectedEndDate(value: Long) {
        updateLong(KEY_HOME_SELECTED_END_DATE, value)
    }

    override suspend fun setThemePreference(value: String) {
        updateString(KEY_THEME_PREFERENCE, value)
    }

    private companion object {

        const val KEY_HOME_SELECTED_START_DATE = "home_selected_start_date"
        const val KEY_HOME_SELECTED_END_DATE = "home_selected_end_date"
        const val KEY_THEME_PREFERENCE = "theme_preference"
        const val DEFAULT_THEME = "SYSTEM"
        const val NAME = "common_prefs"
    }
}
