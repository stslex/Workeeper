// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
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
 * - **User-initiated undo (Scenario 3).** Called from `RestoreDialogChoiceObserver`
 *   when the user confirms the undo confirmation dialog. Rolls back to the
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
     *
     * **Journal first (Phase 5 R2, spec §8.4):** when the restore transaction recorded an
     * interrupted mutation ([RestoreStateRepository.isRestoreMutationInterrupted]), the swap
     * failed or ended unknown after the point of no return — a schema peek could SUCCEED
     * against the untouched OLD file and produce a false "restore succeeded". The journal
     * routes straight to the failure path: roll back via the preserved snapshot and surface
     * truthful restore-FAILURE semantics, never RestoreSuccess, never a fake undo offer.
     */
    suspend fun handlePostRestoreLaunch(): PreflightOutcome {
        val inProgressContext = restoreStateRepository.getRestoreInProgressContext()
            ?: return PreflightOutcome.NoOp

        if (restoreStateRepository.isRestoreMutationInterrupted()) {
            return if (handleRestoreFailure(inProgressContext, cause = null)) {
                PreflightOutcome.RestoreRolledBack
            } else {
                PreflightOutcome.RecoveryRetryPending
            }
        }

        val peekResult = runCatching { snapshotProvider.currentSchemaVersion() }
        return if (peekResult.isSuccess) {
            handleRestoreSuccess(inProgressContext)
            PreflightOutcome.RestoreSucceeded
        } else {
            if (handleRestoreFailure(inProgressContext, peekResult.exceptionOrNull())) {
                PreflightOutcome.RestoreRolledBack
            } else {
                PreflightOutcome.RecoveryRetryPending
            }
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
    internal suspend fun performUndoRestore(
        onCommitted: suspend () -> Unit = {},
    ): UndoRestoreOutcome {
        if (snapshotProvider.getPreRestoreBackupFile() == null) {
            // Defensive: the UI gates this behind observePreRestoreBackupAvailable,
            // but the file could be gone (cache eviction).
            restoreStateRepository.clearPreRestoreBackupAvailable()
            return UndoRestoreOutcome.FileMissing
        }
        // All compensation rides the typed effects object ON the transaction's coroutine
        // (Phase 5 R2 submission ownership): an in-process undo initiator lives inside the
        // outgoing generation's lifetime, which the transition kills mid-await — the effects
        // survive it. The side-effect-first / dismiss-after discipline is preserved INSIDE
        // onCommitted: the undo state and dialog writes precede the caller's acknowledge.
        val result = databaseReplacement.rollbackToPreRestoreBackup(
            effects = UndoRollbackEffects(onCommitted),
        )
        return when (result) {
            is DatabaseReplacementResult.Committed -> {
                result.effectsError?.let { effectsError ->
                    // The rollback committed; only the post-commit bookkeeping failed —
                    // surfaced here (the seam never reports a silently clean commit).
                    logger.w { "undo rollback committed with failed effects: $effectsError" }
                }
                UndoRestoreOutcome.Succeeded
            }

            // Whichever way it did NOT commit, this caller deletes nothing — pre-mutation
            // rejections left every asset intact, and post-mutation failures hand the recovery
            // assets to the runtime/journal protocol. IoFailure keeps the dialog visible for a
            // re-tap, exactly as before.
            is DatabaseReplacementResult.RejectedBeforeMutation,
            is DatabaseReplacementResult.RecoveredByRollback,
            is DatabaseReplacementResult.FailedAfterMutation,
            DatabaseReplacementResult.FatalNoGeneration,
            -> {
                logger.w { "Undo restore rollback did not commit: $result" }
                UndoRestoreOutcome.IoFailure
            }
        }
    }

    /** The undo transaction's typed compensation — runs on the transaction's coroutine. */
    private inner class UndoRollbackEffects(
        private val acknowledge: suspend () -> Unit,
    ) : DatabaseReplacementEffects {

        override suspend fun onCommitted() {
            restoreStateRepository.clearPreRestoreBackupAvailable()
            appDialogPublisher.publish(AppDialog.UndoRestoreSuccess)
            acknowledge()
        }
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

    /** Runs the failure-path rollback; returns true when the rollback COMMITTED. */
    private suspend fun handleRestoreFailure(
        context: RestoreInProgressContext,
        cause: Throwable?,
    ): Boolean {
        if (cause != null) {
            reporter.recordRestoreTimeFailure(
                exception = cause,
                context = context,
                appVersionName = platformInfo.appVersionName(),
            )
        }
        val rollback = databaseReplacement.rollbackToPreRestoreBackup(
            effects = ScenarioOneRollbackEffects(),
        )
        return when (rollback) {
            is DatabaseReplacementResult.Committed -> {
                rollback.effectsError?.let { effectsError ->
                    logger.w { "scenario-1 rollback committed with failed effects: $effectsError" }
                }
                true
            }

            // EVERY non-commit preserves EVERY viable recovery file and marker (round-2
            // mandate 4 — the old defensive delete-on-FailedAfterMutation branch is REMOVED):
            // `restore_in_progress` stays set and `pre_restore_backup.db` stays on disk, so the
            // NEXT launch re-enters this pre-flight and retries the rollback — the recovery
            // path survives a process restart instead of being destroyed by its first failure.
            // The user still gets FEEDBACK (publishing a dialog touches no recovery asset), and
            // the caller must NOT restart: with the rollback uncommitted a restart would loop
            // silently forever — the launch continues and the next one retries.
            is DatabaseReplacementResult.RejectedBeforeMutation,
            is DatabaseReplacementResult.RecoveredByRollback,
            is DatabaseReplacementResult.FailedAfterMutation,
            DatabaseReplacementResult.FatalNoGeneration,
            -> {
                logger.w { "Scenario 1 rollback did not commit ($rollback) — assets preserved for retry" }
                appDialogPublisher.publish(
                    AppDialog.RestoreFailure(reason = BackupErrorCodeForFailure),
                )
                false
            }
        }
    }

    /** The Scenario-1 rollback's typed compensation — runs on the transaction's coroutine. */
    private inner class ScenarioOneRollbackEffects : DatabaseReplacementEffects {

        override suspend fun onCommitted() {
            restoreStateRepository.clearRestoreInProgress()
            // No undo slot after a failure rollback — the preserved file was consumed.
            restoreStateRepository.clearPreRestoreBackupAvailable()
            appDialogPublisher.publish(
                AppDialog.RestoreFailure(reason = BackupErrorCodeForFailure),
            )
        }
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
         * Migration failed; the rollback to the preserved snapshot COMMITTED. Caller MUST
         * restart the app because the in-process Room handle is stale (it
         * already opened the failed db).
         */
        RestoreRolledBack,

        /**
         * The failure-path rollback did NOT commit (round-2 mandate 4: every asset and marker
         * preserved for the next launch's retry). The caller must NOT restart — the rollback is
         * still pending, so a restart would loop silently forever ("restart → retry → fail →
         * restart"). A [AppDialog.RestoreFailure] was published as feedback; the launch
         * continues (journal-routed case: the live file is the untouched pre-restore data;
         * peek-throw case: DB reads fail loud exactly as any unmigratable-database launch
         * would) and the NEXT launch re-enters the pre-flight and retries.
         */
        RecoveryRetryPending,
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
