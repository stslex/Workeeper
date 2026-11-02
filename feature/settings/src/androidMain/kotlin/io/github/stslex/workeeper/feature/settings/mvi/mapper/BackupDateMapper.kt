// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import android.text.format.DateUtils
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.settings.di.SettingsScope
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.resources.Res
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_info_count
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_info_count_zero
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_info_last_backup_format
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_info_last_backup_never

@SingleIn(SettingsScope::class)
internal class BackupDateMapper @Inject constructor(
    private val stringResolver: StringResolver,
) {

    suspend fun toInfo(
        summaries: List<BackupSummaryDomain>,
        now: Long = System.currentTimeMillis(),
    ): BackupInfoUi = if (summaries.isEmpty()) {
        // The mapper only ever runs on a RESULT, so it can never produce `Unknown` — that state
        // belongs to the interval before this is called, and is `State.INITIAL`'s job.
        BackupInfoUi.Empty(backupCountText = formatBackupCount(summaries.size))
    } else {
        BackupInfoUi.Present(
            lastBackupText = formatLastBackup(summaries.first().createdAtEpochMs, now),
            backupCountText = formatBackupCount(summaries.size),
        )
    }

    suspend fun formatLastBackup(
        epochMs: Long?,
        now: Long = System.currentTimeMillis(),
    ): String = if (epochMs == null) {
        stringResolver.getString(Res.string.feature_settings_backup_info_last_backup_never)
    } else {
        val relative = DateUtils.getRelativeTimeSpanString(
            epochMs,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
        stringResolver.getString(
            Res.string.feature_settings_backup_info_last_backup_format,
            relative,
        )
    }

    suspend fun formatBackupCount(count: Int): String = if (count <= 0) {
        stringResolver.getString(Res.string.feature_settings_backup_info_count_zero)
    } else {
        stringResolver.getPluralString(
            Res.plurals.feature_settings_backup_info_count,
            count,
            count,
        )
    }
}
