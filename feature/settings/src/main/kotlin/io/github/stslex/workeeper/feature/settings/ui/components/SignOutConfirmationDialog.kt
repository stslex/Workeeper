// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action

@Composable
internal fun SignOutConfirmationDialog(
    onAction: (Action.Backup) -> Unit,
) {
    AppConfirmDialog(
        title = stringResource(R.string.feature_settings_backup_sign_out_title),
        body = stringResource(R.string.feature_settings_backup_sign_out_body),
        impactSummary = stringResource(R.string.feature_settings_backup_sign_out_impact),
        confirmLabel = stringResource(R.string.feature_settings_backup_sign_out),
        onConfirm = { onAction(Action.Backup.ConfirmSignOut) },
        onDismiss = { onAction(Action.Backup.DismissSignOutConfirmation) },
    )
}
