// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreRecoveryReporter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the two cross-cutting recovery flows that live above
 * `BackupInteractor`:
 *
 * - **Post-restart pre-flight (Scenario 1).** Called from
 *   `BaseApplication.onCreate` when `restore_in_progress = true`. Peeks the
 *   live database to verify Room can migrate it. On success, clears the
 *   in-progress flag, marks the preserved snapshot available for undo, and
 *   publishes [AppDialog.RestoreSuccess]. On failure, rolls back to the
 *   preserved snapshot, clears the flag, deletes the preserved file
 *   (consumed), publishes [AppDialog.RestoreFailure], records a Crashlytics
 *   non-fatal, and asks the caller to restart the app.
 *
 * - **User-initiated undo (Scenario 3).** Called from `AppDialogHost` when
 *   the user confirms the undo confirmation dialog. Rolls back to the
 *   preserved snapshot, clears the marker, publishes
 *   [AppDialog.UndoRestoreSuccess] (the dialog survives the restart that
 *   immediately follows), and asks the caller to restart the app.
 *
 * The coordinator lives in `app/app` because it is the only module that
 * has direct access to all four collaborators ([RestoreStateRepository],
 * [DatabaseSnapshotProvider], [AppDialogPublisher], [RestoreRecoveryReporter])
 * at SingletonComponent scope.
 *
 * App restart is **not** performed inline — both flows return a
 * [PreflightOutcome] / `Boolean` and the caller (Application bootstrap or
 * the host EntryPoint) drives the actual restart via the existing
 * `NavigatorExt.restartApp(context)` path.
 */
@Singleton
class RestoreRecoveryCoordinator @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val restoreStateRepository: RestoreStateRepository,
    private val appDialogPublisher: AppDialogPublisher,
    private val reporter: RestoreRecoveryReporter,
) {

    private val logger = Log.tag("RestoreRecoveryCoordinator")

    /**
     * Runs the Scenario 1 pre-flight. Idempotent — safe to call on every
     * launch; short-circuits to [PreflightOutcome.NoOp] when no restore is
     * in progress.
     */
    suspend fun handlePostRestoreLaunch(): PreflightOutcome {
        val inProgressContext = restoreStateRepository.getRestoreInProgressContext()
            ?: return PreflightOutcome.NoOp

        val peekResult = runCatching { snapshotProvider.currentSchemaVersion() }
        return if (peekResult.isSuccess) {
            handleRestoreSuccess(inProgressContext)
            PreflightOutcome.RestoreSucceeded
        } else {
            handleRestoreFailure(inProgressContext, peekResult.exceptionOrNull())
            PreflightOutcome.RestoreRolledBack
        }
    }

    /**
     * Runs the Scenario 3 undo. The outcome distinguishes three cases:
     *
     *  - [UndoRestoreOutcome.Succeeded] — rollback completed; caller should
     *    restart the app immediately.
     *  - [UndoRestoreOutcome.FileMissing] — `pre_restore_backup.db` was
     *    absent (cache eviction OR already consumed by a prior call). No
     *    swap happened; nothing more is possible. The defensive
     *    `pre_restore_backup_available` clear runs.
     *  - [UndoRestoreOutcome.IoFailure] — the file existed but the atomic
     *    rename failed (e.g. disk full). The file is still at its original
     *    location; `pre_restore_backup_available` stays set so the user can
     *    retry from Settings.
     *
     * Callers (notably the consumer-side `RestoreDialogChoiceObserver`)
     * gate the `acknowledgeReaction` dismiss on
     * `Succeeded || FileMissing` — `IoFailure` keeps the dialog visible
     * so the user sees the reaction did not complete and can re-tap.
     */
    internal suspend fun performUndoRestore(): UndoRestoreOutcome {
        if (!snapshotProvider.hasPreRestoreBackup()) {
            // Defensive: the UI gates this behind observePreRestoreBackupAvailable,
            // but the file could be gone (cache eviction).
            restoreStateRepository.clearPreRestoreBackupAvailable()
            return UndoRestoreOutcome.FileMissing
        }
        when (val rollback = snapshotProvider.rollbackToPreRestoreBackup()) {
            is BackupResult.Success -> Unit
            is BackupResult.Failure -> {
                logger.w { "Undo restore rollback failed: ${rollback.error}" }
                return UndoRestoreOutcome.IoFailure
            }
        }
        restoreStateRepository.clearPreRestoreBackupAvailable()
        appDialogPublisher.publish(AppDialog.UndoRestoreSuccess)
        return UndoRestoreOutcome.Succeeded
    }

    private suspend fun handleRestoreSuccess(context: RestoreInProgressContext) {
        restoreStateRepository.clearRestoreInProgress()
        restoreStateRepository.markPreRestoreBackupAvailable(context.startedAtEpochMs)
        appDialogPublisher.publish(
            AppDialog.RestoreSuccess(
                restoredAtEpochMs = System.currentTimeMillis(),
                previousVersionAvailable = true,
            ),
        )
    }

    private suspend fun handleRestoreFailure(
        context: RestoreInProgressContext,
        cause: Throwable?,
    ) {
        if (cause != null) {
            reporter.recordRestoreTimeFailure(
                exception = cause,
                context = context,
                appVersionName = readVersionName(),
            )
        }
        val rollback = snapshotProvider.rollbackToPreRestoreBackup()
        if (rollback is BackupResult.Failure) {
            logger.w { "Scenario 1 rollback failed: ${rollback.error}" }
            // Still clear flag + delete preserved so the next launch is normal.
            snapshotProvider.deletePreRestoreBackup()
        }
        restoreStateRepository.clearRestoreInProgress()
        // No undo slot after a failure rollback — the preserved file was
        // consumed by rollbackToPreRestoreBackup (or deleted defensively above).
        restoreStateRepository.clearPreRestoreBackupAvailable()
        appDialogPublisher.publish(
            AppDialog.RestoreFailure(reason = BackupErrorCodeForFailure),
        )
    }

    private fun readVersionName(): String {
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return info.versionName.orEmpty()
    }

    /**
     * Pulls the launch intent off the package manager and starts it with
     * `FLAG_ACTIVITY_NEW_TASK | CLEAR_TASK`, then exits the current process.
     * Identical mechanic to `NavigatorExt.restartApp` but callable from
     * non-Composable code paths (Application.onCreate, EntryPoint).
     */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("No launch intent for package ${context.packageName}")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        if (context is Activity) context.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    /** Discriminator for the post-restart pre-flight result. */
    enum class PreflightOutcome {
        /** No `restore_in_progress` flag — this was a normal launch. */
        NoOp,

        /** Migration succeeded; success dialog published. Continue startup normally. */
        RestoreSucceeded,

        /**
         * Migration failed; rolled back to the preserved snapshot. Caller MUST
         * restart the app because the in-process Room handle is stale (it
         * already opened the failed db).
         */
        RestoreRolledBack,
    }

    private companion object {
        /**
         * Reason surfaced in [AppDialog.RestoreFailure] when the post-restart
         * pre-flight rolls back. We cannot map the underlying [Throwable] to a
         * specific [io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode]
         * — Room's exception types are not part of the v1 BackupError surface
         * — so we surface `Unknown` and rely on the diagnostic export / the
         * Crashlytics non-fatal for the actual failure shape.
         */
        val BackupErrorCodeForFailure =
            io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode.Unknown
    }
}
