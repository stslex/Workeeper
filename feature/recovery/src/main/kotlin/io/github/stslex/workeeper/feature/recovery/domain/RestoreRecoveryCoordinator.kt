// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreRecoveryReporter

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
 * The coordinator lives in `feature/recovery` and is the only module that
 * has direct access to all four collaborators ([RestoreStateRepository],
 * [DatabaseSnapshotProvider], [AppDialogPublisher], [RestoreRecoveryReporter])
 * at SingletonComponent scope.
 *
 * App restart is **not** performed inline by [handlePostRestoreLaunch] —
 * it returns a [PreflightOutcome] and the caller (`BaseApplication.onCreate`)
 * calls [restartApp] on this coordinator when the outcome is
 * [PreflightOutcome.RestoreRolledBack]. Same for [performUndoRestore]'s
 * [UndoRestoreOutcome.Succeeded] outcome on the consumer side
 * ([RestoreDialogChoiceObserver][io.github.stslex.workeeper.feature.recovery.RestoreDialogChoiceObserver]).
 * [restartApp] delegates to the platform-neutral [AppReinitializer] seam, whose single
 * Android actual (a process restart) is shared with the Settings post-restore path.
 */
@SingleIn(AppScope::class)
class RestoreRecoveryCoordinator @Inject internal constructor(
    private val appReinitializer: AppReinitializer,
    private val platformInfo: PlatformInfoProvider,
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val databaseReplacement: DatabaseReplacement,
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
        if (snapshotProvider.getPreRestoreBackupFile() == null) {
            // Defensive: the UI gates this behind observePreRestoreBackupAvailable,
            // but the file could be gone (cache eviction).
            restoreStateRepository.clearPreRestoreBackupAvailable()
            return UndoRestoreOutcome.FileMissing
        }
        when (val rollback = databaseReplacement.rollbackToPreRestoreBackup()) {
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
                appVersionName = platformInfo.appVersionName(),
            )
        }
        val rollback = databaseReplacement.rollbackToPreRestoreBackup()
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

    /**
     * Restarts the app after a recovery step by delegating to the platform-neutral
     * [AppReinitializer] seam. Callable from non-Composable code paths
     * (`Application.onCreate` pre-flight, the undo reactor). The Android actual is a
     * process restart; the mechanism lives in one place (the androidMain actual),
     * shared with the Settings post-restore restart path.
     */
    fun restartApp() {
        appReinitializer.reinitialize()
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
         * specific [BackupErrorCode]
         * — Room's exception types are not part of the v1 BackupError surface
         * — so we surface `Unknown` and rely on the diagnostic export / the
         * Crashlytics non-fatal for the actual failure shape.
         */
        val BackupErrorCodeForFailure = BackupErrorCode.Unknown
    }
}
