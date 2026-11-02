// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_sign_out
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_sign_out_body
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_sign_out_impact
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_sign_out_title
import org.jetbrains.compose.resources.stringResource
import io.github.stslex.workeeper.feature.settings.resources.Res as SettingsRes

@Composable
internal fun SignOutConfirmationDialog(
    onAction: (Action.Backup) -> Unit,
) {
    AppConfirmDialog(
        modifier = Modifier.testTag("SettingsSignOutDialog"),
        title = stringResource(SettingsRes.string.feature_settings_backup_sign_out_title),
        body = stringResource(SettingsRes.string.feature_settings_backup_sign_out_body),
        impactSummary = stringResource(SettingsRes.string.feature_settings_backup_sign_out_impact),
        confirmLabel = stringResource(SettingsRes.string.feature_settings_backup_sign_out),
        onConfirm = { onAction(Action.Backup.ConfirmSignOut) },
        onDismiss = { onAction(Action.Backup.DismissSignOutConfirmation) },
    )
}
