// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

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
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AccountDataStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AccountDataStore {

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(ACCOUNT_PREFS_NAME)
        }
    }

    override fun observeAccount(): Flow<Account?> = dataStore.data.map(::accountFromPrefs)

    override suspend fun setAccount(account: Account) {
        dataStore.edit { prefs ->
            prefs[KEY_EMAIL] = account.email
            val displayName = account.displayName
            if (displayName != null) {
                prefs[KEY_DISPLAY_NAME] = displayName
            } else {
                prefs.remove(KEY_DISPLAY_NAME)
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    override suspend fun setToken(token: String, expiresAtEpochMs: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_TOKEN_EXPIRES_AT] = expiresAtEpochMs
        }
    }

    override suspend fun token(): TokenSnapshot? {
        val prefs = dataStore.data.first()
        val token = prefs[KEY_TOKEN] ?: return null
        val expiresAt = prefs[KEY_TOKEN_EXPIRES_AT] ?: return null
        return TokenSnapshot(token = token, expiresAtEpochMs = expiresAt)
    }

    override suspend fun clearToken() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_TOKEN_EXPIRES_AT)
        }
    }

    override suspend fun snapshotFolderId(): String? = dataStore.data.first()[KEY_SNAPSHOT_FOLDER_ID]

    override suspend fun setSnapshotFolderId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(KEY_SNAPSHOT_FOLDER_ID)
            } else {
                prefs[KEY_SNAPSHOT_FOLDER_ID] = id
            }
        }
    }

    override fun observeDriveFileGranted(): Flow<Boolean> =
        dataStore.data.map { it[KEY_DRIVE_FILE_GRANTED] ?: false }

    override suspend fun isDriveFileGranted(): Boolean =
        dataStore.data.first()[KEY_DRIVE_FILE_GRANTED] ?: false

    override suspend fun setDriveFileGranted(granted: Boolean) {
        dataStore.edit { it[KEY_DRIVE_FILE_GRANTED] = granted }
    }

    private fun accountFromPrefs(prefs: Preferences): Account? {
        val email = prefs[KEY_EMAIL] ?: return null
        return Account(email = email, displayName = prefs[KEY_DISPLAY_NAME])
    }

    private companion object {
        const val ACCOUNT_PREFS_NAME = "backup_account_prefs"
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_TOKEN = stringPreferencesKey("access_token")
        val KEY_TOKEN_EXPIRES_AT = longPreferencesKey("access_token_expires_at")
        val KEY_SNAPSHOT_FOLDER_ID = stringPreferencesKey("snapshot_folder_id")
        val KEY_DRIVE_FILE_GRANTED = booleanPreferencesKey("drive_file_granted")
    }
}
