// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.switch.AppSwitch
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action

@Composable
internal fun FrequencyPickerBottomSheet(
    state: DialogState.FrequencyPicker,
    onAction: (Action.Backup) -> Unit,
) {
    AppBottomSheet(
        onDismiss = { onAction(Action.Backup.DismissFrequencyPicker) },
        modifier = Modifier.testTag("FrequencyPickerBottomSheet"),
    ) {
        Text(
            text = stringResource(R.string.feature_settings_backup_frequency_picker_title),
            style = AppUi.typography.titleMedium,
            color = AppUi.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(AppDimension.Space.md))
        BackupScheduleUi.entries.forEach { schedule ->
            FrequencyRow(
                schedule = schedule,
                selected = state.selectedSchedule == schedule,
                onSelected = {
                    onAction(
                        Action.Backup.UpdateFrequencyPickerSelection(
                            schedule = schedule,
                            allowOnMobileData = state.allowOnMobileData,
                        ),
                    )
                },
            )
        }
        Spacer(modifier = Modifier.height(AppDimension.Space.md))
        MobileDataToggle(
            allowOnMobileData = state.allowOnMobileData,
            onToggle = { allow ->
                onAction(
                    Action.Backup.UpdateFrequencyPickerSelection(
                        schedule = state.selectedSchedule,
                        allowOnMobileData = allow,
                    ),
                )
            },
        )
        Spacer(modifier = Modifier.height(AppDimension.Space.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                AppDimension.Space.sm,
                Alignment.End,
            ),
        ) {
            AppButton.Secondary(
                text = stringResource(R.string.feature_settings_backup_restore_cancel),
                onClick = { onAction(Action.Backup.DismissFrequencyPicker) },
                size = AppButtonSize.MEDIUM,
            )
            AppButton.Primary(
                text = stringResource(R.string.feature_settings_backup_frequency_picker_save),
                onClick = {
                    onAction(
                        Action.Backup.SaveFrequency(
                            schedule = state.selectedSchedule,
                            allowOnMobileData = state.allowOnMobileData,
                        ),
                    )
                },
                size = AppButtonSize.MEDIUM,
            )
        }
    }
}

@Composable
private fun FrequencyRow(
    schedule: BackupScheduleUi,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelected)
            .padding(vertical = AppDimension.Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelected,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppUi.colors.accent,
                unselectedColor = AppUi.colors.borderStrong,
            ),
        )
        Spacer(modifier = Modifier.padding(start = AppDimension.Space.xs))
        Text(
            text = stringResource(schedule.labelRes()),
            style = AppUi.typography.bodyLarge,
            color = AppUi.colors.textPrimary,
        )
    }
}

@Composable
private fun MobileDataToggle(
    allowOnMobileData: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimension.Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.feature_settings_backup_allow_mobile_data_label),
                style = AppUi.typography.bodyLarge,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.feature_settings_backup_allow_mobile_data_caption),
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.textTertiary,
            )
        }
        AppSwitch(checked = allowOnMobileData, onCheckedChange = onToggle)
    }
}

private fun BackupScheduleUi.labelRes(): Int = when (this) {
    BackupScheduleUi.DAILY -> R.string.feature_settings_backup_frequency_daily
    BackupScheduleUi.WEEKLY -> R.string.feature_settings_backup_frequency_weekly
    BackupScheduleUi.MANUAL_ONLY -> R.string.feature_settings_backup_frequency_manual_only
}

@Preview
@Composable
private fun FrequencyPickerBottomSheetLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        FrequencyPickerBottomSheet(
            state = DialogState.FrequencyPicker(
                selectedSchedule = BackupScheduleUi.WEEKLY,
                allowOnMobileData = false,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun FrequencyPickerBottomSheetDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        FrequencyPickerBottomSheet(
            state = DialogState.FrequencyPicker(
                selectedSchedule = BackupScheduleUi.DAILY,
                allowOnMobileData = true,
            ),
            onAction = {},
        )
    }
}
