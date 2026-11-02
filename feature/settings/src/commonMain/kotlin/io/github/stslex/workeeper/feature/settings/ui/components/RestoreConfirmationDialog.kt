// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_restore_cancel
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_restore_confirm
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_restore_impact
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_restore_title
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_restore_warning
import org.jetbrains.compose.resources.stringResource
import io.github.stslex.workeeper.feature.settings.resources.Res as SettingsRes

@Composable
internal fun RestoreConfirmationDialog(
    state: DialogState.RestoreConfirmation,
    onAction: (Action.Backup) -> Unit,
) {
    AppConfirmDialog(
        modifier = Modifier.testTag("SettingsRestoreDialog"),
        title = stringResource(SettingsRes.string.feature_settings_backup_restore_title),
        body = stringResource(
            SettingsRes.string.feature_settings_backup_restore_warning,
            state.createdAtFormatted,
            state.sizeFormatted,
        ),
        impactSummary = stringResource(SettingsRes.string.feature_settings_backup_restore_impact),
        confirmLabel = stringResource(SettingsRes.string.feature_settings_backup_restore_confirm),
        dismissLabel = stringResource(SettingsRes.string.feature_settings_backup_restore_cancel),
        onConfirm = { onAction(Action.Backup.ConfirmRestore) },
        onDismiss = { onAction(Action.Backup.DismissRestoreDialog) },
    )
}
