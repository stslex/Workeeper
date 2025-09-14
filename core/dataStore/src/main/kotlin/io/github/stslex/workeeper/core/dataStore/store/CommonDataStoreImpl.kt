package io.github.stslex.workeeper.core.store.store

import io.github.stslex.workeeper.core.store.core.BaseDataStore
import io.github.stslex.workeeper.core.store.core.DataStoreProvider
import io.github.stslex.workeeper.core.store.di.CommonStoreQualifier
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class CommonDataStoreImpl internal constructor(
    @CommonStoreQualifier
    store: DataStoreProvider
) : CommonDataStore, BaseDataStore(store) {

    override var homeSelectedStartDate: Flow<Long?> = getLong(KEY_HOME_SELECTED_START_DATE)

    override var homeSelectedEndDate: Flow<Long?> = getLong(KEY_HOME_SELECTED_END_DATE)

    override suspend fun setHomeSelectedStartDate(value: Long) {
        updateLong(KEY_HOME_SELECTED_START_DATE, value)
    }

    override suspend fun setHomeSelectedEndDate(value: Long) {
        updateLong(KEY_HOME_SELECTED_END_DATE, value)
    }

    private companion object {

        private const val KEY_HOME_SELECTED_START_DATE = "home_selected_start_date"
        private const val KEY_HOME_SELECTED_END_DATE = "home_selected_end_date"
    }
}

