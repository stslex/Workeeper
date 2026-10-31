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
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_confirmation_body
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_confirmation_cancel
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_confirmation_confirm
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_confirmation_title
import org.jetbrains.compose.resources.stringResource

/**
 * User-initiated revert-last-restore confirmation. Destructive — confirm chrome
 * is rendered in red because the action overwrites the freshly-restored live
 * database with the pre-restore copy. The body shows the date of the data the
 * user is about to bring back: [dateLabel], formatted in State from
 * [AppDialog.UndoRestoreConfirmation.originalDataDateEpochMs].
 */
@Composable
internal fun UndoRestoreConfirmationDialog(
    dateLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppConfirmationDialog(
        title = stringResource(Res.string.app_dialog_undo_restore_confirmation_title),
        body = stringResource(
            Res.string.app_dialog_undo_restore_confirmation_body,
            dateLabel,
        ),
        confirmLabel = stringResource(Res.string.app_dialog_undo_restore_confirmation_confirm),
        onConfirm = onConfirm,
        dismissLabel = stringResource(Res.string.app_dialog_undo_restore_confirmation_cancel),
        onDismiss = onCancel,
        isDestructive = true,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun UndoRestoreConfirmationDialogLightPreview() {
    UndoRestoreConfirmationDialogPreview(themeMode = ThemeMode.LIGHT)
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun UndoRestoreConfirmationDialogDarkPreview() {
    UndoRestoreConfirmationDialogPreview(themeMode = ThemeMode.DARK)
}

@Composable
private fun UndoRestoreConfirmationDialogPreview(themeMode: ThemeMode) {
    AppTheme(themeMode = themeMode) {
        UndoRestoreConfirmationDialog(
            dateLabel = "Nov 14, 2023",
            onConfirm = {},
            onCancel = {},
        )
    }
}
