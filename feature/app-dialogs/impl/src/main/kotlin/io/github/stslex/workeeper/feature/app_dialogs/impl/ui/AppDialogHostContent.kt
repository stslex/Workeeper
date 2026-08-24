// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice

/**
 * Pure-Compose renderer split out of [AppDialogHost] so UI tests can drive it with synthetic
 * state and a fake [onChoice]; renders nothing when [current] is `null`.
 */
@Composable
internal fun AppDialogHostContent(
    current: AppDialog?,
    onChoice: (AppDialogUserChoice) -> Unit,
) {
    val dialog = current ?: return
    when (dialog) {
        is AppDialog.RestoreSuccess -> RestoreSuccessDialog(
            dialog = dialog,
            onAcknowledge = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.Acknowledge)) },
            onUndoRestore = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.RequestUndo)) },
        )

        is AppDialog.RestoreFailure -> RestoreFailureDialog(
            dialog = dialog,
            onAcknowledge = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.Acknowledge)) },
            onReport = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.Report)) },
            onExportDiagnostics = {
                onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.ExportDiagnostics))
            },
        )

        is AppDialog.UndoRestoreConfirmation -> UndoRestoreConfirmationDialog(
            dialog = dialog,
            onConfirm = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.ConfirmUndo)) },
            onCancel = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.Cancel)) },
        )

        AppDialog.UndoRestoreSuccess -> UndoRestoreSuccessDialog(
            onAcknowledge = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.Acknowledge)) },
        )
    }
}
