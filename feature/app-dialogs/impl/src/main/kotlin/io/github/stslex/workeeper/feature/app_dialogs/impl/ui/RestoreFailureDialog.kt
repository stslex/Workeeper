// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.R

/**
 * Three-action restore-failure dialog. Distinct from
 * `AppConfirmationDialog` (which supports at most two buttons) — it stacks
 * the secondary actions (Report / Export) in a row above the primary OK
 * action so they share the same chrome but get their own touch targets.
 *
 * Strict dismiss policy per spec: `dismissOnBackPress = false`,
 * `dismissOnClickOutside = false` — the user must tap an action button
 * explicitly so the failure can never be swiped away without acknowledgement.
 */
@Composable
internal fun RestoreFailureDialog(
    dialog: AppDialog.RestoreFailure,
    onAcknowledge: () -> Unit,
    onReport: () -> Unit,
    onExportDiagnostics: () -> Unit,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(
        onDismissRequest = onAcknowledge,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .clip(AppUi.shapes.medium)
                .background(dialogBg)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Text(
                text = stringResource(R.string.app_dialog_restore_failure_title),
                style = AppUi.typography.titleLarge,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = stringResource(
                    R.string.app_dialog_restore_failure_body,
                    dialog.reason.name,
                ),
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = AppDimension.Space.sm,
                    alignment = Alignment.End,
                ),
            ) {
                AppButton.Tertiary(
                    text = stringResource(R.string.app_dialog_restore_failure_export_action),
                    onClick = onExportDiagnostics,
                    size = AppButtonSize.MEDIUM,
                )
                AppButton.Tertiary(
                    text = stringResource(R.string.app_dialog_restore_failure_report_action),
                    onClick = onReport,
                    size = AppButtonSize.MEDIUM,
                )
                AppButton.Primary(
                    text = stringResource(R.string.app_dialog_restore_failure_confirm),
                    onClick = onAcknowledge,
                    size = AppButtonSize.MEDIUM,
                )
            }
        }
    }
}
