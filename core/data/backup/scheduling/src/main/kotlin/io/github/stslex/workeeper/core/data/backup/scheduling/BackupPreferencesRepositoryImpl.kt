// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BackupPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupPreferencesRepository {

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(PREFS_NAME)
        }
    }

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
    }
}
