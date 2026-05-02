// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.dialog

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * Confirmation dialog used by the Live workout overflow Delete-session action. Asymmetric
 * focus from [ActiveSessionConflictDialog] is intentional — the overflow tap is gated by
 * tap distance, so this dialog defaults to Cancel and surfaces the destructive action
 * second, matching the locked design.
 */
@Composable
fun DiscardSessionConfirmDialog(
    sessionName: String,
    progressLabel: String,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .clip(AppUi.shapes.medium)
                .background(dialogBg)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Text(
                text = stringResource(
                    R.string.core_ui_kit_discard_session_confirm_title_format,
                    sessionName,
                ),
                style = AppUi.typography.titleLarge,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = progressLabel,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.core_ui_kit_discard_session_confirm_warning),
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.setType.failureForeground,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = AppDimension.Space.sm,
                    alignment = Alignment.End,
                ),
            ) {
                AppButton.Tertiary(
                    text = stringResource(R.string.core_ui_kit_action_cancel),
                    onClick = onDismiss,
                    size = AppButtonSize.MEDIUM,
                )
                AppButton.Destructive(
                    text = stringResource(R.string.core_ui_kit_discard_session_confirm_delete),
                    onClick = onConfirmDelete,
                    size = AppButtonSize.MEDIUM,
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscardSessionConfirmDialogPreview() {
    AppTheme {
        DiscardSessionConfirmDialog(
            sessionName = "Push Day",
            progressLabel = "2 of 5 exercises done · 7 sets logged",
            onConfirmDelete = {},
            onDismiss = {},
        )
    }
}
