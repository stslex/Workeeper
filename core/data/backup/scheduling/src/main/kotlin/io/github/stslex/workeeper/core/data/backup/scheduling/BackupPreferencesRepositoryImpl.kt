// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupSchedule
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Metro-owned via `@ContributesBinding(AppScope)`, which the app-scope `AppGraph` auto-aggregates
 * (interface-bound → the single supertype `BackupPreferencesRepository` is the implicit bound type).
 * `@SingleIn(AppScope)` gives a process-lifetime single owner.
 *
 * `public` (was `internal`): Metro 1.1.1 rejects a cross-Gradle-module `@ContributesBinding` on an
 * `internal` class — the merged graph in `:app:app` cannot extend an internal contribution from another
 * module (MergedContributionChecker `isInternalInFriendModule`; verified in the B4 spike — internal,
 * `@PublishedApi internal`, and both `@ContributesTo`-container shapes all fail). Only public aggregates.
 * The widening is narrow: the override methods were already public via the interface; only the class and
 * constructor visibility change.
 *
 * The store is minted through [DataStoreProviderFactory], never with a per-instance
 * `PreferenceDataStoreFactory.create` — a `DataStore` is a per-file singleton and `DataStoreProvider`'s
 * memoization is static (process-lifetime), while this class is `@SingleIn(AppScope)` (graph-lifetime).
 * A second `AppGraph` in one process must resolve the SAME store, or DataStore 1.1+ throws
 * `IllegalStateException: There are multiple DataStores active` on the second read. Invariant pinned by
 * `app/app` androidTest `AppScopeDataStoreSingletonTest`. `preferencesDataStoreFile(PREFS_NAME)` — the
 * expression the provider applies — is the same one this class applied directly, so the resolved file is
 * unchanged and no user data moves.
 *
 * The primary constructor is `internal` and takes the store itself: unit tests bind a temp-file
 * `DataStore` through it, which keeps them off the provider's process-lifetime map (a memoized store
 * would outlive the test that created it and leak state into the next one).
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class BackupPreferencesRepositoryImpl internal constructor(
    private val dataStore: DataStore<Preferences>,
) : BackupPreferencesRepository {

    @Inject
    constructor(storeFactory: DataStoreProviderFactory) : this(
        storeFactory.create(PREFS_NAME).dataStore,
    )

    override fun observe(): Flow<BackupPreferences> = dataStore.data.map(::fromPrefs)

    override suspend fun setSchedule(schedule: BackupSchedule) {
        dataStore.edit { it[KEY_SCHEDULE] = schedule.name }
    }

    override suspend fun setAllowOnMobileData(allow: Boolean) {
        dataStore.edit { it[KEY_ALLOW_MOBILE_DATA] = allow }
    }

    override suspend fun setLastAttempt(epochMs: Long) {
        dataStore.edit { it[KEY_LAST_ATTEMPT] = epochMs }
    }

    override suspend fun setLastSuccess(epochMs: Long) {
        dataStore.edit { it[KEY_LAST_SUCCESS] = epochMs }
    }

    override suspend fun setLastError(error: BackupErrorCode?) {
        dataStore.edit { prefs ->
            if (error == null) {
                prefs.remove(KEY_LAST_ERROR)
            } else {
                prefs[KEY_LAST_ERROR] = error.name
            }
        }
    }

    override suspend fun setAutoBackupBootstrapped(bootstrapped: Boolean) {
        dataStore.edit { it[KEY_AUTO_BACKUP_BOOTSTRAPPED] = bootstrapped }
    }

    override suspend fun setAiExportEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AI_EXPORT_ENABLED] = enabled }
    }

    private fun fromPrefs(prefs: Preferences): BackupPreferences {
        val default = BackupPreferences.DEFAULT
        val schedule = prefs[KEY_SCHEDULE]
            ?.let { name -> runCatching { BackupSchedule.valueOf(name) }.getOrNull() }
            ?: default.schedule
        val lastError = prefs[KEY_LAST_ERROR]
            ?.takeIf { it.isNotEmpty() }
            ?.let { name -> runCatching { BackupErrorCode.valueOf(name) }.getOrNull() }
        return BackupPreferences(
            schedule = schedule,
            allowOnMobileData = prefs[KEY_ALLOW_MOBILE_DATA] ?: default.allowOnMobileData,
            lastAttemptAtEpochMs = prefs[KEY_LAST_ATTEMPT] ?: default.lastAttemptAtEpochMs,
            lastSuccessAtEpochMs = prefs[KEY_LAST_SUCCESS] ?: default.lastSuccessAtEpochMs,
            lastError = lastError,
            autoBackupBootstrapped = prefs[KEY_AUTO_BACKUP_BOOTSTRAPPED]
                ?: default.autoBackupBootstrapped,
            aiExportEnabled = prefs[KEY_AI_EXPORT_ENABLED] ?: default.aiExportEnabled,
        )
    }

    private companion object {
        const val PREFS_NAME = "backup_scheduling_prefs"

        val KEY_SCHEDULE = stringPreferencesKey("schedule")
        val KEY_ALLOW_MOBILE_DATA = booleanPreferencesKey("allow_on_mobile_data")
        val KEY_LAST_ATTEMPT = longPreferencesKey("last_attempt_at")
        val KEY_LAST_SUCCESS = longPreferencesKey("last_success_at")
        val KEY_LAST_ERROR = stringPreferencesKey("last_error")
        val KEY_AUTO_BACKUP_BOOTSTRAPPED = booleanPreferencesKey("auto_backup_bootstrapped")
        val KEY_AI_EXPORT_ENABLED = booleanPreferencesKey("ai_export_enabled")
    }
}
