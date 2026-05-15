// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.R
import io.github.stslex.workeeper.feature.app_dialogs.impl.store.AppDialogStore
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Renders the currently-pending [AppDialog] above every navigation destination.
 *
 * Mounting site: `App.kt`, inside `AppTheme`, in the same root `Box` as
 * `AppNavigationHost`. The host holds no state of its own; everything is
 * derived from [AppDialogStore.currentDialog]. Dismiss = clear flags in
 * DataStore → flow re-emits → recompose.
 *
 * `AppDialogStore` is an application-scope singleton, so it is reached via
 * an `EntryPoint` rather than `hiltViewModel`. The composable creates no
 * `ViewModel` of its own.
 *
 * Action handling (e.g. "Report issue" → launches issue tracker, "Undo" →
 * triggers restore-rollback by setting a sibling flag) is **out of scope**
 * for the initial app-dialogs PR; this host currently dismisses on every
 * action. Concrete action wiring lands alongside the producer features.
 */
@Composable
fun AppDialogHost() {
    val context = LocalContext.current
    val store = remember(context) {
        EntryPointAccessors.fromApplication<AppDialogHostEntryPoint>(context.applicationContext)
            .appDialogStore()
    }
    val scope = rememberCoroutineScope()
    val current by store.currentDialog.collectAsState(initial = null)
    AppDialogHostContent(
        current = current,
        onDismiss = { dialog -> scope.launch { store.dismiss(dialog) } },
    )
}

/**
 * Pure-Compose renderer split out of [AppDialogHost] so UI tests can drive it
 * with synthetic state without standing up the Hilt graph. Returns nothing when
 * [current] is `null` — the host composes empty content in that case.
 */
@Composable
internal fun AppDialogHostContent(
    current: AppDialog?,
    onDismiss: (AppDialog) -> Unit,
) {
    val dialog = current ?: return
    val dismiss = { onDismiss(dialog) }
    when (dialog) {
        is AppDialog.RestoreSuccess -> RestoreSuccessDialog(dialog, onAcknowledge = dismiss)
        is AppDialog.RestoreFailure -> RestoreFailureDialog(dialog, onAcknowledge = dismiss)
        is AppDialog.UndoRestoreConfirmation -> UndoRestoreConfirmationDialog(
            dialog = dialog,
            onConfirm = dismiss,
            onCancel = dismiss,
        )
        AppDialog.UndoRestoreSuccess -> UndoRestoreSuccessDialog(onAcknowledge = dismiss)
    }
}

@Composable
private fun RestoreSuccessDialog(
    dialog: AppDialog.RestoreSuccess,
    onAcknowledge: () -> Unit,
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
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    )
}

@Composable
private fun RestoreFailureDialog(
    dialog: AppDialog.RestoreFailure,
    onAcknowledge: () -> Unit,
) {
    AppConfirmationDialog(
        title = stringResource(R.string.app_dialog_restore_failure_title),
        body = stringResource(R.string.app_dialog_restore_failure_body, dialog.reason.name),
        confirmLabel = stringResource(R.string.app_dialog_restore_failure_confirm),
        onConfirm = onAcknowledge,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

@Composable
private fun UndoRestoreConfirmationDialog(
    dialog: AppDialog.UndoRestoreConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val formattedDate = remember(dialog.originalDataDateEpochMs) {
        formatMediumDate(dialog.originalDataDateEpochMs)
    }
    AppConfirmationDialog(
        title = stringResource(R.string.app_dialog_undo_restore_confirmation_title),
        body = stringResource(
            R.string.app_dialog_undo_restore_confirmation_body,
            formattedDate,
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

@Composable
private fun UndoRestoreSuccessDialog(onAcknowledge: () -> Unit) {
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

private fun formatMediumDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AppDialogHostEntryPoint {
    fun appDialogStore(): AppDialogStore
}
