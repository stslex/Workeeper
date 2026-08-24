// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.scheduling

import kotlinx.coroutines.flow.Flow

/**
 * Observable read/write surface for [BackupPreferences], shared by settings UI, worker and
 * bootstrap. GUARD: keep updaters per-field — a whole-object setter clobbers concurrent writes.
 */
interface BackupPreferencesRepository {

    fun observe(): Flow<BackupPreferences>

    suspend fun setSchedule(schedule: BackupSchedule)

    suspend fun setAllowOnMobileData(allow: Boolean)

    suspend fun setLastAttempt(epochMs: Long)

    suspend fun setLastSuccess(epochMs: Long)

    suspend fun setLastError(error: BackupErrorCode?)

    suspend fun setAutoBackupBootstrapped(bootstrapped: Boolean)

    suspend fun setAiExportEnabled(enabled: Boolean)
}
