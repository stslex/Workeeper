// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.Res
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_body
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_body_no_previous
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_confirm
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_title
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_undo_action
import org.jetbrains.compose.resources.stringResource

/**
 * Post-restore acknowledgement; adds an Undo button when a rollback slot is still available.
 * Back press dismisses, outside tap does not. [dateLabel] is the restore's date, formatted in State.
 */
@Composable
internal fun RestoreSuccessDialog(
    dialog: AppDialog.RestoreSuccess,
    dateLabel: String,
    onAcknowledge: () -> Unit,
    onUndoRestore: () -> Unit,
) {
    val body = if (dialog.previousVersionAvailable) {
        stringResource(Res.string.app_dialog_restore_success_body, dateLabel)
    } else {
        stringResource(Res.string.app_dialog_restore_success_body_no_previous, dateLabel)
    }
    AppConfirmationDialog(
        title = stringResource(Res.string.app_dialog_restore_success_title),
        body = body,
        confirmLabel = stringResource(Res.string.app_dialog_restore_success_confirm),
        onConfirm = onAcknowledge,
        dismissLabel = if (dialog.previousVersionAvailable) {
            stringResource(Res.string.app_dialog_restore_success_undo_action)
        } else null,
        onDismiss = if (dialog.previousVersionAvailable) onUndoRestore else onAcknowledge,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun RestoreSuccessDialogWithUndoLightPreview() {
    RestoreSuccessDialogWithUndoPreview(themeMode = ThemeMode.LIGHT)
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun RestoreSuccessDialogWithUndoDarkPreview() {
    RestoreSuccessDialogWithUndoPreview(themeMode = ThemeMode.DARK)
}

@Composable
private fun RestoreSuccessDialogWithUndoPreview(themeMode: ThemeMode) {
    AppTheme(themeMode = themeMode) {
        RestoreSuccessDialog(
            dialog = AppDialog.RestoreSuccess(
                restoredAtEpochMs = PREVIEW_EPOCH_MS,
                previousVersionAvailable = true,
            ),
            dateLabel = PREVIEW_DATE_LABEL,
            onAcknowledge = {},
            onUndoRestore = {},
        )
    }
}

@Preview(name = "No previous version", showBackground = true)
@Composable
private fun RestoreSuccessDialogNoUndoPreview() {
    AppTheme {
        RestoreSuccessDialog(
            dialog = AppDialog.RestoreSuccess(
                restoredAtEpochMs = PREVIEW_EPOCH_MS,
                previousVersionAvailable = false,
            ),
            dateLabel = PREVIEW_DATE_LABEL,
            onAcknowledge = {},
            onUndoRestore = {},
        )
    }
}

private const val PREVIEW_EPOCH_MS: Long = 1_700_000_000_000L
private const val PREVIEW_DATE_LABEL: String = "Nov 14, 2023"
