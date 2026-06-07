// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.recovery.boot.RecoveryBootstrap
import io.github.stslex.workeeper.feature.recovery.diagnostics.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.UndoRestoreOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton
import io.github.stslex.workeeper.feature.recovery.R as RecoveryR

/**
 * Consumer-side reactor for the cross-feature `AppDialog` choices. Replaces
 * the deleted `AppDialogActions` interface + `AppDialogActionsImpl`; reads
 * choices from [AppDialogObserver.observeUserActions] and performs the
 * undo / report / export side-effects, then calls
 * [AppDialogObserver.acknowledgeReaction] to clear the dialog.
 *
 * **Bootstrap (BLOCKER 1).** This `@Singleton` is constructed at
 * `BaseApplication.onCreate` via a Hilt `@EntryPoint`, mirroring the
 * existing `RecoveryEntryPoint` and `ImageStorageEntryPoint` patterns.
 * Construction triggers the `init { ... launchIn(scope) }` block below,
 * registering a subscriber on the observer's `SharedFlow` BEFORE any
 * `MainActivity.onCreate` runs. The first user dispatch lands on a live
 * collector; no lost signal.
 *
 * **Dismiss-after, uniform (BLOCKER 2).** For EVERY variant — including
 * the destructive `UndoRestoreConfirmation` + `ConfirmUndo` path — the
 * side-effect runs FIRST and [AppDialogObserver.acknowledgeReaction] runs
 * AFTER. Crash-mid-reaction leaves the dialog flag set; the dialog re-shows
 * on next launch; the user re-taps; the side-effect runs again
 * (idempotent — `coordinator.performUndoRestore()` no-ops when the
 * pre-restore backup file is already consumed). This invariant must NOT
 * be reversed for the `ConfirmUndo` path: dismiss-first would create a
 * silent-failure window where the dialog disappears, the user perceives
 * the success path, but the rollback did not happen.
 *
 * For `performUndoRestore` specifically, the outcome is a three-way
 * [UndoRestoreOutcome]:
 *
 *  - [UndoRestoreOutcome.Succeeded] → acknowledge + restart.
 *  - [UndoRestoreOutcome.FileMissing] → acknowledge (no further user-driven
 *    action can succeed; safe to dismiss).
 *  - [UndoRestoreOutcome.IoFailure] → do NOT acknowledge (the dialog stays
 *    visible so the user sees the reaction did not complete and can re-tap;
 *    `pre_restore_backup.db` is still on disk and Settings → "Revert last
 *    restore" remains available as a parallel retry path).
 *
 * The gate (`outcome != IoFailure`) is what closes the case-b silent-
 * failure window — without it, an IO-error rollback would dismiss the
 * dialog while the user's data was never actually rolled back.
 */
@Singleton
internal class RestoreDialogChoiceObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observer: AppDialogObserver,
    private val coordinator: RestoreRecoveryCoordinator,
    private val restoreStateRepository: RestoreStateRepository,
    private val appDialogPublisher: AppDialogPublisher,
    private val diagnosticsExporter: RecoveryDiagnosticsExporter,
) : RecoveryBootstrap {

    private val logger = Log.tag(TAG)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        observer.observeUserActions()
            .onEach { choice -> handle(choice) }
            .launchIn(scope)
    }

    private suspend fun handle(choice: AppDialogUserChoice) {
        runCatching { dispatch(choice) }
            .onFailure { logger.e(it, "Reactor failed for ${choice.dialog.id} → ${choice.action}") }
    }

    private suspend fun dispatch(choice: AppDialogUserChoice) {
        when (val dialog = choice.dialog) {
            is AppDialog.RestoreSuccess -> handleRestoreSuccess(dialog, choice.action)
            is AppDialog.RestoreFailure -> handleRestoreFailure(dialog, choice.action)
            is AppDialog.UndoRestoreConfirmation -> handleUndoConfirmation(dialog, choice.action)
            AppDialog.UndoRestoreSuccess -> if (choice.action == AppDialogUserAction.Acknowledge) {
                observer.acknowledgeReaction(dialog)
            }
        }
    }

    private suspend fun handleRestoreSuccess(
        dialog: AppDialog.RestoreSuccess,
        action: AppDialogUserAction,
    ) {
        when (action) {
            AppDialogUserAction.Acknowledge -> observer.acknowledgeReaction(dialog)
            AppDialogUserAction.RequestUndo -> {
                publishUndoConfirmation()
                observer.acknowledgeReaction(dialog)
            }

            else -> Unit
        }
    }

    private suspend fun handleRestoreFailure(
        dialog: AppDialog.RestoreFailure,
        action: AppDialogUserAction,
    ) {
        when (action) {
            AppDialogUserAction.Acknowledge -> observer.acknowledgeReaction(dialog)
            AppDialogUserAction.Report -> {
                openReportIssue()
                observer.acknowledgeReaction(dialog)
            }

            AppDialogUserAction.ExportDiagnostics -> {
                val uri = exportRestoreDiagnostics()
                if (uri != null) shareDiagnostics(uri)
                observer.acknowledgeReaction(dialog)
            }

            else -> Unit
        }
    }

    private suspend fun handleUndoConfirmation(
        dialog: AppDialog.UndoRestoreConfirmation,
        action: AppDialogUserAction,
    ) {
        when (action) {
            AppDialogUserAction.ConfirmUndo -> {
                // Dismiss-AFTER (uniform). Side-effect first; acknowledge only
                // when the outcome is one we can dismiss the dialog on. IoFailure
                // keeps the dialog visible so the user sees the reaction did NOT
                // complete and can re-tap — anything else (success OR the file
                // already being gone) means no further user-driven action could
                // accomplish anything new, so the dialog is safe to dismiss.
                val outcome = coordinator.performUndoRestore()
                if (outcome != UndoRestoreOutcome.IoFailure) {
                    observer.acknowledgeReaction(dialog)
                }
                if (outcome == UndoRestoreOutcome.Succeeded) coordinator.restartApp()
            }

            AppDialogUserAction.Cancel -> observer.acknowledgeReaction(dialog)

            else -> Unit
        }
    }

    private suspend fun publishUndoConfirmation() {
        val originalDate = restoreStateRepository.getPreRestoreOriginalDate() ?: return
        appDialogPublisher.publish(
            AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = originalDate),
        )
    }

    private suspend fun exportRestoreDiagnostics(): Uri? {
        val info = readPackageInfo()
        return diagnosticsExporter.exportRestoreFailure(
            exception = null,
            context = restoreStateRepository.getRestoreInProgressContext(),
            appVersionName = info.versionName.orEmpty(),
            appVersionCode = info.longVersionCode,
        )
    }

    private fun openReportIssue() {
        val title = Uri.encode(context.getString(RecoveryR.string.recovery_restore_failure_report_title))
        val labels = Uri.encode(GITHUB_ISSUE_LABELS)
        val url = "$GITHUB_ISSUE_BASE_URL?title=$title&labels=$labels"
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun shareDiagnostics(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(
            send,
            context.getString(RecoveryR.string.recovery_restore_failure_share_chooser),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    private fun readPackageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

    private companion object {
        const val TAG = "RestoreDialogChoiceObserver"
        const val GITHUB_ISSUE_BASE_URL = "https://github.com/stslex/Workeeper/issues/new"
        const val GITHUB_ISSUE_LABELS = "bug,migration"
    }
}
