// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.R

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
        title = stringResource(R.string.app_dialog_undo_restore_confirmation_title),
        body = stringResource(
            R.string.app_dialog_undo_restore_confirmation_body,
            dateLabel,
        ),
        confirmLabel = stringResource(R.string.app_dialog_undo_restore_confirmation_confirm),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.app_dialog_undo_restore_confirmation_cancel),
        onDismiss = onCancel,
        isDestructive = true,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UndoRestoreConfirmationDialogPreview() {
    AppTheme {
        UndoRestoreConfirmationDialog(
            dateLabel = "Nov 14, 2023",
            onConfirm = {},
            onCancel = {},
        )
    }
}
