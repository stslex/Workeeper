// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import android.content.Context
import android.text.format.DateUtils
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi

internal object BackupDateMapper {

    fun toInfo(
        summaries: List<BackupSummaryDomain>,
        context: Context,
        now: Long = System.currentTimeMillis(),
    ): BackupInfoUi = BackupInfoUi(
        lastBackupText = formatLastBackup(summaries.firstOrNull()?.createdAtEpochMs, context, now),
        backupCountText = formatBackupCount(summaries.size, context),
    )

    fun formatLastBackup(
        epochMs: Long?,
        context: Context,
        now: Long = System.currentTimeMillis(),
    ): String = if (epochMs == null) {
        context.getString(R.string.feature_settings_backup_info_last_backup_never)
    } else {
        val relative = DateUtils.getRelativeTimeSpanString(
            epochMs,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
        context.getString(
            R.string.feature_settings_backup_info_last_backup_format,
            relative,
        )
    }

    fun formatBackupCount(count: Int, context: Context): String = if (count <= 0) {
        context.getString(R.string.feature_settings_backup_info_count_zero)
    } else {
        context.resources.getQuantityString(
            R.plurals.feature_settings_backup_info_count,
            count,
            count,
        )
    }
}
