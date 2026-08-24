package io.github.stslex.workeeper.core.data.dataStore.store

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.dataStore.core.BaseDataStore
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import kotlinx.coroutines.flow.Flow

/**
 * App-scope owner of the `common_prefs` store. Explicit `binding<CommonDataStore>()` because the
 * class has two supertypes; `public` because `@ContributesBinding` on internal does not aggregate.
 */
@ContributesBinding(AppScope::class, binding = binding<CommonDataStore>())
@SingleIn(AppScope::class)
@Inject
class CommonDataStoreImpl(
    storeFactory: DataStoreProviderFactory,
) : CommonDataStore, BaseDataStore(
    storeFactory.create(NAME),
) {

    override var homeSelectedStartDate: Flow<Long?> = getLong(KEY_HOME_SELECTED_START_DATE)

    override var homeSelectedEndDate: Flow<Long?> = getLong(KEY_HOME_SELECTED_END_DATE)

    override val themePreference: Flow<String> = getString(KEY_THEME_PREFERENCE, DEFAULT_THEME)

    override val homeStartCardMode: Flow<String> =
        getString(KEY_HOME_START_CARD_MODE, DEFAULT_START_CARD_MODE)

    override suspend fun setHomeSelectedStartDate(value: Long) {
        updateLong(KEY_HOME_SELECTED_START_DATE, value)
    }

    override suspend fun setHomeSelectedEndDate(value: Long) {
        updateLong(KEY_HOME_SELECTED_END_DATE, value)
    }

    override suspend fun setThemePreference(value: String) {
        updateString(KEY_THEME_PREFERENCE, value)
    }

    override suspend fun setHomeStartCardMode(value: String) {
        updateString(KEY_HOME_START_CARD_MODE, value)
    }

    private companion object {

        const val KEY_HOME_SELECTED_START_DATE = "home_selected_start_date"
        const val KEY_HOME_SELECTED_END_DATE = "home_selected_end_date"
        const val KEY_THEME_PREFERENCE = "theme_preference"
        const val KEY_HOME_START_CARD_MODE = "home_start_card_mode"
        const val DEFAULT_THEME = "SYSTEM"

        // `StartCardModeDomain.WEEK.value` in feature/home — HS3's default, pinned by
        // CommonDataStorePersistenceTest so the two constants cannot drift apart silently.
        const val DEFAULT_START_CARD_MODE = "WEEK"
        const val NAME = "common_prefs"
    }
}
