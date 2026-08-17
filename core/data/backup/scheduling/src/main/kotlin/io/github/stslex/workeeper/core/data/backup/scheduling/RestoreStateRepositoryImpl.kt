// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed implementation of [RestoreStateRepository]. Lives in the
 * scheduling module alongside [BackupPreferencesRepositoryImpl] because both
 * own DataStore Preferences files in the same module-local layout.
 *
 * Storage: a separate Preferences file (`restore_state_prefs.preferences_pb`)
 * so the restore-recovery flags stay isolated from the auto-backup schedule
 * / last-error tuple owned by `BackupPreferencesRepository`. The two sets of
 * keys have orthogonal lifecycles and clearing one must never disturb the
 * other.
 *
 * Key names are **wire format** — same stability rule as
 * `feature/app-dialogs/impl/.../AppDialogKeys`. Never rename without the
 * deprecation path (add new key, write both, ship, remove old). Renaming a
 * `restore_in_progress` key mid-flow would lose a user's pending dialog on
 * app update.
 */
// App-Scope Collapse Step 3 (SB1): Hilt @Inject/@Singleton stripped, @Binds removed; Metro-owned via
// @ContributesBinding(AppScope). Public for cross-module aggregation (D1; never hand-construct — resolve
// via DI).
//
// Mint the store through DataStoreProviderFactory only: a second AppGraph that built its own would
// throw "There are multiple DataStores active" on the second read. Pinned by app/app androidTest
// AppScopeDataStoreSingletonTest. Bind the internal primary ctor in unit tests, not the provider.
// Mechanism and evidence: documentation/tech-debt.md -> "DataStore singleton bypass".
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RestoreStateRepositoryImpl internal constructor(
    private val dataStore: DataStore<Preferences>,
) : RestoreStateRepository {

    @Inject
    constructor(storeFactory: DataStoreProviderFactory) : this(
        storeFactory.create(PREFS_NAME).dataStore,
    )

    override suspend fun markRestoreInProgress(context: RestoreInProgressContext) {
        dataStore.edit { prefs ->
            prefs[KEY_RESTORE_IN_PROGRESS] = true
            prefs[KEY_BACKUP_SCHEMA_VERSION] = context.backupSchemaVersion
            prefs[KEY_BACKUP_CREATED_AT] = context.backupCreatedAtEpochMs
            prefs[KEY_BACKUP_APP_VERSION] = context.backupAppVersion
            prefs[KEY_RESTORE_STARTED_AT] = context.startedAtEpochMs
        }
    }

    override suspend fun getRestoreInProgressContext(): RestoreInProgressContext? {
        val prefs = dataStore.data.first()
        if (prefs[KEY_RESTORE_IN_PROGRESS] != true) return null
        return RestoreInProgressContext(
            backupSchemaVersion = prefs[KEY_BACKUP_SCHEMA_VERSION] ?: return null,
            backupCreatedAtEpochMs = prefs[KEY_BACKUP_CREATED_AT] ?: return null,
            backupAppVersion = prefs[KEY_BACKUP_APP_VERSION] ?: return null,
            startedAtEpochMs = prefs[KEY_RESTORE_STARTED_AT] ?: return null,
        )
    }

    override suspend fun clearRestoreInProgress() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_RESTORE_IN_PROGRESS)
            prefs.remove(KEY_BACKUP_SCHEMA_VERSION)
            prefs.remove(KEY_BACKUP_CREATED_AT)
            prefs.remove(KEY_BACKUP_APP_VERSION)
            prefs.remove(KEY_RESTORE_STARTED_AT)
        }
    }

    override suspend fun markPreRestoreBackupAvailable(originalDataDateEpochMs: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_PRE_RESTORE_AVAILABLE] = true
            prefs[KEY_PRE_RESTORE_ORIGINAL_DATE] = originalDataDateEpochMs
        }
    }

    override suspend fun clearPreRestoreBackupAvailable() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PRE_RESTORE_AVAILABLE)
            prefs.remove(KEY_PRE_RESTORE_ORIGINAL_DATE)
        }
    }

    override fun observePreRestoreBackupAvailable(): Flow<Boolean> =
        dataStore.data
            .map { it[KEY_PRE_RESTORE_AVAILABLE] == true }
            .distinctUntilChanged()

    override suspend fun getPreRestoreOriginalDate(): Long? =
        dataStore.data.first()[KEY_PRE_RESTORE_ORIGINAL_DATE]

    private companion object {
        const val PREFS_NAME = "restore_state_prefs"

        val KEY_RESTORE_IN_PROGRESS = booleanPreferencesKey("restore_in_progress")
        val KEY_BACKUP_SCHEMA_VERSION =
            intPreferencesKey("restore_in_progress_backup_schema_version")
        val KEY_BACKUP_CREATED_AT =
            longPreferencesKey("restore_in_progress_backup_created_at_epoch_ms")
        val KEY_BACKUP_APP_VERSION =
            stringPreferencesKey("restore_in_progress_backup_app_version")
        val KEY_RESTORE_STARTED_AT =
            longPreferencesKey("restore_in_progress_started_at_epoch_ms")

        val KEY_PRE_RESTORE_AVAILABLE = booleanPreferencesKey("pre_restore_backup_available")
        val KEY_PRE_RESTORE_ORIGINAL_DATE =
            longPreferencesKey("pre_restore_backup_original_date_epoch_ms")
    }
}
