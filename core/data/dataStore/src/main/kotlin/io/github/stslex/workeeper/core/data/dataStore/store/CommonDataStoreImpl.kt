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
 * App-Scope Collapse Step 3 (CommonDataStore slice). Hilt's `@Inject`/`@Singleton` were stripped and the
 * Hilt `@Binds` in `CoreDataStoreModule` removed; this type is now Metro-owned via
 * `@ContributesBinding(AppScope)`, which the app-scope `AppGraph` (`@DependencyGraph(AppScope::class)`)
 * auto-aggregates. `@SingleIn(AppScope)` gives the process-lifetime single-owner the `@Singleton` gave.
 * Declared `public` (not `internal`) because `@ContributesBinding` on an `internal` class does not aggregate
 * across Gradle modules (D1; same pattern as `NumUiUtilsImpl`/`AccountDataStoreImpl`). An explicit
 * `binding<CommonDataStore>()` is required because the class has two supertypes (it also extends the
 * `BaseDataStore` helper) — only [CommonDataStore] is the app-graph-bound type. Its dep
 * [DataStoreProviderFactory] is a Metro-native `@AssistedFactory` resolved from the graph; `create(NAME)`
 * mints the single `common_prefs` provider once at construction.
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
