// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreConfirmationUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action

@Composable
internal fun RestoreConfirmationDialog(
    state: RestoreConfirmationUi,
    onAction: (Action.Backup) -> Unit,
) {
    AppConfirmDialog(
        title = stringResource(R.string.feature_settings_backup_restore_title),
        body = stringResource(
            R.string.feature_settings_backup_restore_warning,
            state.createdAtFormatted,
            state.sizeFormatted,
        ),
        impactSummary = stringResource(R.string.feature_settings_backup_restore_impact),
        confirmLabel = stringResource(R.string.feature_settings_backup_restore_confirm),
        dismissLabel = stringResource(R.string.feature_settings_backup_restore_cancel),
        onConfirm = { onAction(Action.Backup.ConfirmRestore) },
        onDismiss = { onAction(Action.Backup.DismissRestoreDialog) },
    )
}
