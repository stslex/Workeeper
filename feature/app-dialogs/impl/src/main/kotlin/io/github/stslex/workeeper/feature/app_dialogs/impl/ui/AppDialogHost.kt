// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.actions.AppDialogActions
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
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication<AppDialogHostEntryPoint>(context.applicationContext)
    }
    val store = remember(entryPoint) { entryPoint.appDialogStore() }
    val actions = remember(entryPoint) { entryPoint.appDialogActions() }
    val scope = rememberCoroutineScope()
    val current by store.currentDialog.collectAsState(initial = null)
    AppDialogHostContent(
        current = current,
        onDismiss = { dialog -> scope.launch { store.dismiss(dialog) } },
        onUndoRestoreRequest = { dialog ->
            scope.launch {
                actions.publishUndoConfirmation()
                store.dismiss(dialog)
            }
        },
        onConfirmUndo = { dialog ->
            scope.launch {
                store.dismiss(dialog)
                actions.performUndoRestore()
            }
        },
        onReport = { dialog ->
            scope.launch { store.dismiss(dialog) }
            openReportIssue(context)
        },
        onExportDiagnostics = { dialog ->
            scope.launch {
                val uri = actions.exportRestoreDiagnostics()
                store.dismiss(dialog)
                if (uri != null) shareDiagnostics(context, uri)
            }
        },
    )
}

/**
 * Pure-Compose renderer split out of [AppDialogHost] so UI tests can drive it
 * with synthetic state and fine-grained callback fakes — no Hilt graph
 * required. Returns nothing when [current] is `null`.
 */
@Composable
internal fun AppDialogHostContent(
    current: AppDialog?,
    onDismiss: (AppDialog) -> Unit,
    onUndoRestoreRequest: (AppDialog.RestoreSuccess) -> Unit,
    onConfirmUndo: (AppDialog.UndoRestoreConfirmation) -> Unit,
    onReport: (AppDialog.RestoreFailure) -> Unit,
    onExportDiagnostics: (AppDialog.RestoreFailure) -> Unit,
) {
    val dialog = current ?: return
    when (dialog) {
        is AppDialog.RestoreSuccess -> RestoreSuccessDialog(
            dialog = dialog,
            onAcknowledge = { onDismiss(dialog) },
            onUndoRestore = { onUndoRestoreRequest(dialog) },
        )

        is AppDialog.RestoreFailure -> RestoreFailureDialog(
            dialog = dialog,
            onAcknowledge = { onDismiss(dialog) },
            onReport = { onReport(dialog) },
            onExportDiagnostics = { onExportDiagnostics(dialog) },
        )

        is AppDialog.UndoRestoreConfirmation -> UndoRestoreConfirmationDialog(
            dialog = dialog,
            onConfirm = { onConfirmUndo(dialog) },
            onCancel = { onDismiss(dialog) },
        )

        AppDialog.UndoRestoreSuccess -> UndoRestoreSuccessDialog(
            onAcknowledge = { onDismiss(dialog) },
        )
    }
}

private fun openReportIssue(context: Context) {
    val title = Uri.encode(context.getString(R.string.app_dialog_restore_failure_report_title))
    val labels = Uri.encode(GITHUB_ISSUE_LABELS)
    val url = "$GITHUB_ISSUE_BASE_URL?title=$title&labels=$labels"
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun shareDiagnostics(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(
        send,
        context.getString(R.string.app_dialog_restore_failure_share_chooser),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

private const val GITHUB_ISSUE_BASE_URL = "https://github.com/stslex/Workeeper/issues/new"
private const val GITHUB_ISSUE_LABELS = "bug,migration"

@Composable
private fun RestoreSuccessDialog(
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
    fun appDialogActions(): AppDialogActions
}
