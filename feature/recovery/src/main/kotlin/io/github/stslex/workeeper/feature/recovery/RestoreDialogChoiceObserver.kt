// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.recovery.boot.RecoveryBootstrap
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.UndoRestoreOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import io.github.stslex.workeeper.feature.recovery.R as RecoveryR

/**
 * Consumer-side reactor for the cross-feature `AppDialog` choices. Replaces
 * the deleted `AppDialogActions` interface + `AppDialogActionsImpl`; reads
 * choices from [AppDialogObserver.observeUserActions] and performs the
 * undo / report / export side-effects, then calls
 * [AppDialogObserver.acknowledgeReaction] to clear the dialog.
 *
 * **Bootstrap (BLOCKER 1).** This `@SingleIn(AppScope)` observer is
 * `@ContributesBinding(AppScope)`-bound to [RecoveryBootstrap] and constructed at
 * `BaseApplication.onCreate` by eagerly reading the `recoveryBootstrap` accessor on
 * the app graph (`appGraph.recoveryBootstrap`).
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
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
// Public for cross-module @ContributesBinding aggregation into the app graph (D1; never hand-construct
// — resolve via DI). Metro-owned via @ContributesBinding(AppScope) bound to RecoveryBootstrap.
class RestoreDialogChoiceObserver @Inject constructor(
    private val context: Context,
    private val observer: AppDialogObserver,
    private val coordinator: RestoreRecoveryCoordinator,
    private val restoreStateRepository: RestoreStateRepository,
    private val appDialogPublisher: AppDialogPublisher,
    private val diagnosticsExporter: RecoveryDiagnosticsExporter,
    lifetime: AppScopeLifetime,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : RecoveryBootstrap {

    private val logger = Log.tag(TAG)

    // Generation-owned (Phase 5, spec §8.2): derived from the graph's AppScopeLifetime root instead
    // of a self-created unreachable scope, so replacing the generation deterministically ENDS this
    // reactor — the duplicate-observer hazard a second graph generation would otherwise create.
    private val scope = lifetime.childScope(dispatcher)

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
                if (publishUndoConfirmation()) {
                    observer.acknowledgeReaction(dialog)
                }
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
                // Dismiss-AFTER (uniform), Phase 5 R2 shape: the SUCCESS acknowledge rides the
                // transaction's onCommitted hook — this observer's scope is a child of the
                // outgoing generation's lifetime, which an in-process rollback kills mid-await,
                // so post-commit effects must run under transaction ownership. Order inside the
                // hook preserves side-effect-first: undo state + UndoRestoreSuccess publish,
                // THEN this acknowledge. IoFailure still keeps the dialog visible for a re-tap;
                // FileMissing (pre-transaction, initiator alive) acknowledges directly.
                val outcome = coordinator.performUndoRestore(onCommitted = {
                    observer.acknowledgeReaction(dialog)
                })
                if (outcome == UndoRestoreOutcome.FileMissing) {
                    observer.acknowledgeReaction(dialog)
                }
                if (outcome == UndoRestoreOutcome.Succeeded) coordinator.restartApp()
            }

            AppDialogUserAction.Cancel -> observer.acknowledgeReaction(dialog)

            else -> Unit
        }
    }

    /**
     * Publishes [AppDialog.UndoRestoreConfirmation] and returns whether it was
     * published. Returns `false` without publishing when the preserved backup
     * was evicted between [AppDialog.RestoreSuccess] being shown and the user
     * tapping "Undo restore" (`getPreRestoreOriginalDate()` is `null`). In that
     * case the caller must NOT acknowledge `RestoreSuccess`: dismissing it would
     * silently destroy the user's only undo entry point. Keeping `RestoreSuccess`
     * visible is the same choice already made for [UndoRestoreOutcome.IoFailure].
     */
    private suspend fun publishUndoConfirmation(): Boolean {
        val originalDate = restoreStateRepository.getPreRestoreOriginalDate()
        if (originalDate == null) {
            logger.w { "Undo confirmation not published: pre-restore original date is missing" }
            return false
        }
        appDialogPublisher.publish(
            AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = originalDate),
        )
        return true
    }

    private suspend fun exportRestoreDiagnostics(): Uri? {
        val info = readPackageInfo()
        return diagnosticsExporter.exportRestoreFailure(
            exception = null,
            context = restoreStateRepository.getAttempt()?.context,
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
