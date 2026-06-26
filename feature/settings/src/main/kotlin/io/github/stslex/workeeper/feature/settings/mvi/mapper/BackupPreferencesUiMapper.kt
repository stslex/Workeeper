// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import android.text.format.DateUtils
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupWorkInfo
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupSchedule
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupPreferencesUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi

internal object BackupPreferencesUiMapper {

    fun BackupSchedule.toUi(): BackupScheduleUi = when (this) {
        BackupSchedule.Daily -> BackupScheduleUi.DAILY
        BackupSchedule.Weekly -> BackupScheduleUi.WEEKLY
        BackupSchedule.ManualOnly -> BackupScheduleUi.MANUAL_ONLY
    }

    fun BackupScheduleUi.toDomain(): BackupSchedule = when (this) {
        BackupScheduleUi.DAILY -> BackupSchedule.Daily
        BackupScheduleUi.WEEKLY -> BackupSchedule.Weekly
        BackupScheduleUi.MANUAL_ONLY -> BackupSchedule.ManualOnly
    }

    fun toUi(
        prefs: BackupPreferences,
        periodicInfos: List<AutoBackupWorkInfo>,
        now: Long,
    ): BackupPreferencesUi = BackupPreferencesUi(
        schedule = prefs.schedule.toUi(),
        allowOnMobileData = prefs.allowOnMobileData,
        nextBackupText = nextBackupText(prefs.schedule, periodicInfos, now),
        isAuthPaused = prefs.lastError == BackupErrorCode.AuthRevoked,
        aiExportEnabled = prefs.aiExportEnabled,
    )

    private fun nextBackupText(
        schedule: BackupSchedule,
        periodicInfos: List<AutoBackupWorkInfo>,
        now: Long,
    ): String? {
        if (schedule == BackupSchedule.ManualOnly) return null
        val nextEpoch = periodicInfos
            .mapNotNull { it.nextScheduleTimeEpochMs }
            .filter { it > now }
            .minOrNull()
            ?: return null
        val relative = DateUtils.getRelativeTimeSpanString(
            nextEpoch,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
        return relative.toString()
    }
}
