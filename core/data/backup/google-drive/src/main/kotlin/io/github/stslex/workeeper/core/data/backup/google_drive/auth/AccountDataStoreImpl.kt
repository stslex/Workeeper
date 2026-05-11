package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import kotlinx.coroutines.flow.Flow
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

    private fun accountFromPrefs(prefs: Preferences): Account? {
        val email = prefs[KEY_EMAIL] ?: return null
        return Account(email = email, displayName = prefs[KEY_DISPLAY_NAME])
    }

    private companion object {
        const val ACCOUNT_PREFS_NAME = "backup_account_prefs"
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
    }
}
