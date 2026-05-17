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
import androidx.core.net.toUri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.feature.app_dialogs.api.actions.AppDialogActions
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.R
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import kotlinx.coroutines.launch

/**
 * Renders the currently-pending [AppDialog] above every navigation destination.
 *
 * Mounting site: `App.kt`, inside `AppTheme`, in the same root `Box` as
 * `AppNavigationHost`. The host holds no state of its own; everything is
 * derived from [AppDialogRepository.currentDialog]. Dismiss = clear flags in
 * DataStore → flow re-emits → recompose.
 *
 * The repository is an application-scope singleton, so it is reached via
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
    val repository = remember(entryPoint) { entryPoint.appDialogRepository() }
    val actions = remember(entryPoint) { entryPoint.appDialogActions() }
    val scope = rememberCoroutineScope()
    val current by repository.currentDialog.collectAsState(initial = null)
    AppDialogHostContent(
        current = current,
        onDismiss = { dialog -> scope.launch { repository.dismiss(dialog) } },
        onUndoRestoreRequest = { dialog ->
            scope.launch {
                actions.publishUndoConfirmation()
                repository.dismiss(dialog)
            }
        },
        onConfirmUndo = { dialog ->
            scope.launch {
                repository.dismiss(dialog)
                actions.performUndoRestore()
            }
        },
        onReport = { dialog ->
            scope.launch { repository.dismiss(dialog) }
            openReportIssue(context)
        },
        onExportDiagnostics = { dialog ->
            scope.launch {
                val uri = actions.exportRestoreDiagnostics()
                repository.dismiss(dialog)
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

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AppDialogHostEntryPoint {
    fun appDialogRepository(): AppDialogRepository
    fun appDialogActions(): AppDialogActions
}
