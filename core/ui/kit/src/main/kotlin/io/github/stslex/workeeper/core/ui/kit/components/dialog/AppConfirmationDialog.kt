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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * Generic title-body-buttons dialog. Distinct from
 * [AppConfirmDialog]: this variant has no `impactSummary` block and a
 * single-button (`dismissLabel = null`) mode, intended as the rendering
 * primitive for the cross-feature `AppDialog` catalog
 * (see `documentation/feature-specs/app-dialogs.md` →
 * "AppConfirmationDialog (generic)").
 *
 * Stateless: takes labels as strings (caller resolves from `stringResource`),
 * surfaces no internal state. The host (`AppDialogHost`) maps the variant's
 * dismiss policy to [properties] (`dismissOnBackPress`, `dismissOnClickOutside`).
 *
 * @param dismissLabel `null` → single-button confirm-only dialog (e.g.
 *   `RestoreSuccess` acknowledge); non-null → two-button confirm + dismiss.
 * @param isDestructive when `true`, renders the confirm button in
 *   destructive (red) chrome; otherwise primary chrome.
 */
@Composable
fun AppConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onConfirm,
    isDestructive: Boolean = false,
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
    ),
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(
        onDismissRequest = onDismiss,
        properties = properties,
    ) {
        Column(
            modifier = modifier
                .clip(AppUi.shapes.medium)
                .background(dialogBg)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Text(
                text = title,
                style = AppUi.typography.titleLarge,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = body,
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
                if (dismissLabel != null) {
                    AppButton.Tertiary(
                        text = dismissLabel,
                        onClick = onDismiss,
                        size = AppButtonSize.MEDIUM,
                    )
                }
                if (isDestructive) {
                    AppButton.Destructive(
                        text = confirmLabel,
                        onClick = onConfirm,
                        size = AppButtonSize.MEDIUM,
                    )
                } else {
                    AppButton.Primary(
                        text = confirmLabel,
                        onClick = onConfirm,
                        size = AppButtonSize.MEDIUM,
                    )
                }
            }
        }
    }
}

@Preview(name = "Single-button", showBackground = true)
@Composable
private fun AppConfirmationDialogSinglePreview() {
    AppTheme {
        AppConfirmationDialog(
            title = "Restore complete",
            body = "Your data was restored from a backup dated yesterday.",
            confirmLabel = "OK",
            onConfirm = {},
        )
    }
}

@Preview(name = "Two-button destructive", showBackground = true)
@Composable
private fun AppConfirmationDialogTwoButtonPreview() {
    AppTheme {
        AppConfirmationDialog(
            title = "Undo last restore?",
            body = "Your data will revert to the previous state.",
            confirmLabel = "Undo",
            onConfirm = {},
            dismissLabel = "Cancel",
            onDismiss = {},
            isDestructive = true,
        )
    }
}
