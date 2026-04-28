// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * Conflict modal surfaced when the user tries to start a Live workout while a different
 * training already has an in-progress session. Layout is locked: vertical button stack with
 * Resume primary (default focus), Delete & start new outlined-destructive, Cancel text.
 *
 * Caller is responsible for pre-formatting [progressLabel] (locale, plurals).
 */
@Composable
fun ActiveSessionConflictDialog(
    activeSessionName: String,
    progressLabel: String,
    onResume: () -> Unit,
    onDeleteAndStartNew: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = modifier
                .clip(AppUi.shapes.medium)
                .background(dialogBg)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Text(
                text = stringResource(R.string.core_ui_kit_active_session_conflict_title),
                style = AppUi.typography.titleLarge,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = stringResource(
                    R.string.core_ui_kit_active_session_conflict_body_format,
                    activeSessionName,
                    progressLabel,
                ),
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.core_ui_kit_active_session_conflict_warning),
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.setType.failureForeground,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppDimension.heightMd),
                    onClick = onResume,
                    shape = AppUi.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppUi.colors.accent,
                        contentColor = AppUi.colors.onAccent,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.core_ui_kit_active_session_conflict_resume),
                        style = AppUi.typography.labelLarge,
                    )
                }
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppDimension.heightMd),
                    onClick = onDeleteAndStartNew,
                    shape = AppUi.shapes.medium,
                    border = BorderStroke(1.dp, AppUi.colors.setType.failureForeground),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppUi.colors.setType.failureForeground,
                    ),
                ) {
                    Text(
                        text = stringResource(
                            R.string.core_ui_kit_active_session_conflict_delete_and_start,
                        ),
                        style = AppUi.typography.labelLarge,
                    )
                }
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppDimension.heightMd),
                    onClick = onCancel,
                    shape = AppUi.shapes.medium,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AppUi.colors.textSecondary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.core_ui_kit_action_cancel),
                        style = AppUi.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActiveSessionConflictDialogPreview() {
    AppTheme {
        ActiveSessionConflictDialog(
            activeSessionName = "Push Day",
            progressLabel = "2 of 5 exercises done",
            onResume = {},
            onDeleteAndStartNew = {},
            onCancel = {},
        )
    }
}
