// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.R

/**
 * Post-restore happy path acknowledgement. Single confirm button when the
 * pre-restore database is no longer available (`previousVersionAvailable = false`);
 * adds a secondary "Undo" button when the user still has an in-app rollback slot.
 *
 * Dismiss policy: back press dismisses, outside tap does not — the user must
 * acknowledge or explicitly tap Undo so neither path is taken silently.
 */
@Composable
internal fun RestoreSuccessDialog(
    dialog: AppDialog.RestoreSuccess,
    onAcknowledge: () -> Unit,
    onUndoRestore: () -> Unit,
) {
    val formattedDate = remember(dialog.restoredAtEpochMs) {
        formatMediumDate(dialog.restoredAtEpochMs)
    }
    val body = if (dialog.previousVersionAvailable) {
        stringResource(R.string.app_dialog_restore_success_body, formattedDate)
    } else {
        stringResource(R.string.app_dialog_restore_success_body_no_previous, formattedDate)
    }
    AppConfirmationDialog(
        title = stringResource(R.string.app_dialog_restore_success_title),
        body = body,
        confirmLabel = stringResource(R.string.app_dialog_restore_success_confirm),
        onConfirm = onAcknowledge,
        dismissLabel = if (dialog.previousVersionAvailable) {
            stringResource(R.string.app_dialog_restore_success_undo_action)
        } else null,
        onDismiss = if (dialog.previousVersionAvailable) onUndoRestore else onAcknowledge,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RestoreSuccessDialogWithUndoPreview() {
    AppTheme {
        RestoreSuccessDialog(
            dialog = AppDialog.RestoreSuccess(
                restoredAtEpochMs = PREVIEW_EPOCH_MS,
                previousVersionAvailable = true,
            ),
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
            onAcknowledge = {},
            onUndoRestore = {},
        )
    }
}

private const val PREVIEW_EPOCH_MS: Long = 1_700_000_000_000L
