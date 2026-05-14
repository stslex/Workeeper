// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi

@Composable
internal fun AutoBackupRow(
    schedule: BackupScheduleUi,
    nextBackupText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheduleLabel = stringResource(
        R.string.feature_settings_backup_auto_row_label,
        stringResource(schedule.labelRes()),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.sm,
            )
            .testTag("AutoBackupRow"),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        Text(
            text = scheduleLabel,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textPrimary,
        )
        if (nextBackupText != null && schedule != BackupScheduleUi.MANUAL_ONLY) {
            Text(
                text = stringResource(
                    R.string.feature_settings_backup_auto_next_backup,
                    nextBackupText,
                ),
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.textTertiary,
            )
        }
    }
}

private fun BackupScheduleUi.labelRes(): Int = when (this) {
    BackupScheduleUi.DAILY -> R.string.feature_settings_backup_frequency_daily
    BackupScheduleUi.WEEKLY -> R.string.feature_settings_backup_frequency_weekly
    BackupScheduleUi.MANUAL_ONLY -> R.string.feature_settings_backup_frequency_manual_only
}

@Preview
@Composable
private fun AutoBackupRowWeeklyLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        AutoBackupRow(
            schedule = BackupScheduleUi.WEEKLY,
            nextBackupText = "in 5 days",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun AutoBackupRowWeeklyDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AutoBackupRow(
            schedule = BackupScheduleUi.WEEKLY,
            nextBackupText = "in 5 days",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun AutoBackupRowManualOnlyPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AutoBackupRow(
            schedule = BackupScheduleUi.MANUAL_ONLY,
            nextBackupText = null,
            onClick = {},
        )
    }
}
