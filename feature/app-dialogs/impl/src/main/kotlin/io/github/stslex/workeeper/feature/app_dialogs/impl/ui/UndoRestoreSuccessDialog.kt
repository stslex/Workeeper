// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.app_dialogs.impl.R

/**
 * Post-undo-restore happy-path acknowledgement. Single-button confirm; back
 * press dismisses (matches the other restore-flow dialogs); outside tap does
 * not, so the user must acknowledge before the live UI re-enables.
 */
@Composable
internal fun UndoRestoreSuccessDialog(onAcknowledge: () -> Unit) {
    AppConfirmationDialog(
        title = stringResource(R.string.app_dialog_undo_restore_success_title),
        body = stringResource(R.string.app_dialog_undo_restore_success_body),
        confirmLabel = stringResource(R.string.app_dialog_undo_restore_success_confirm),
        onConfirm = onAcknowledge,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UndoRestoreSuccessDialogPreview() {
    AppTheme {
        UndoRestoreSuccessDialog(onAcknowledge = {})
    }
}
