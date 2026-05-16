// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.scheduling

import kotlinx.coroutines.flow.Flow

/**
 * Observable read/write surface for [BackupPreferences]. The single instance is
 * scoped at `@Singleton`; settings UI, worker, and the post-sign-in bootstrap
 * flow all share it.
 *
 * Updaters are intentionally per-field rather than `setPreferences(p)` so two
 * concurrent updates (e.g. worker writing `setLastAttempt` while the user toggles
 * `setAllowOnMobileData`) cannot clobber each other's writes. Each updater
 * issues a DataStore `edit { }` block that touches only its key.
 */
interface BackupPreferencesRepository {

    fun observe(): Flow<BackupPreferences>

    suspend fun setSchedule(schedule: BackupSchedule)

    suspend fun setAllowOnMobileData(allow: Boolean)

    suspend fun setLastAttempt(epochMs: Long)

    suspend fun setLastSuccess(epochMs: Long)

    suspend fun setLastError(error: BackupErrorCode?)

    suspend fun setAutoBackupBootstrapped(bootstrapped: Boolean)
}
