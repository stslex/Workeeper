// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.Res
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_success_body
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_success_confirm
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_success_title
import org.jetbrains.compose.resources.stringResource

/**
 * Post-undo-restore happy-path acknowledgement. Single-button confirm; back
 * press dismisses (matches the other restore-flow dialogs); outside tap does
 * not, so the user must acknowledge before the live UI re-enables.
 */
@Composable
internal fun UndoRestoreSuccessDialog(onAcknowledge: () -> Unit) {
    AppConfirmationDialog(
        title = stringResource(Res.string.app_dialog_undo_restore_success_title),
        body = stringResource(Res.string.app_dialog_undo_restore_success_body),
        confirmLabel = stringResource(Res.string.app_dialog_undo_restore_success_confirm),
        onConfirm = onAcknowledge,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun UndoRestoreSuccessDialogLightPreview() {
    UndoRestoreSuccessDialogPreview(themeMode = ThemeMode.LIGHT)
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun UndoRestoreSuccessDialogDarkPreview() {
    UndoRestoreSuccessDialogPreview(themeMode = ThemeMode.DARK)
}

@Composable
private fun UndoRestoreSuccessDialogPreview(themeMode: ThemeMode) {
    AppTheme(themeMode = themeMode) {
        UndoRestoreSuccessDialog(onAcknowledge = {})
    }
}
