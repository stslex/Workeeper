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
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreRecoveryReporter
import java.util.UUID

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
    /**
     * The last pre-flight verdict, cached for the Activity layer exactly as
     * `StartupMigrationCoordinator.lastDecision` is (spec §8.4): `Application.onCreate` runs the
     * pre-flight, and `MainActivity` reads this to decide whether to hand off to the DB-free
     * recovery surface instead of composing the main UI over a database of unknown provenance.
     */
    @Volatile
    var lastPreflightOutcome: PreflightOutcome? = null
        private set

    /** True when this launch must route to the recovery surface instead of the main UI. */
    val recoverySurfaceRequired: Boolean
        get() = lastPreflightOutcome == PreflightOutcome.RecoveryRequired

    suspend fun handlePostRestoreLaunch(): PreflightOutcome =
        runPostRestoreLaunch().also { lastPreflightOutcome = it }

    private suspend fun runPostRestoreLaunch(): PreflightOutcome {
        val attempt = restoreStateRepository.getAttempt() ?: return PreflightOutcome.NoOp
        val context = attempt.context

        // A COMMITTED attempt is the ONLY state that may end in success: the swap is durably
        // known to have happened, so the schema peek is a genuine verification of the new file.
        if (attempt.phase == RestoreAttempt.Phase.Committed &&
            attempt.kind == RestoreAttempt.Kind.Restore &&
            context != null
        ) {
            val peekResult = runCatching { snapshotProvider.currentSchemaVersion() }
            if (peekResult.isSuccess) {
                handleRestoreSuccess(attempt, context)
                return PreflightOutcome.RestoreSucceeded
            }
            return recoverFrom(attempt, context, peekResult.exceptionOrNull())
        }

        // PREPARED (or any unknown/legacy state): the outcome of the mutation is NOT durably
        // known. The live file may be the old one, the new one, or a partially replaced one, and
        // a schema peek would happily succeed on the untouched OLD database — the exact false
        // "restore succeeded" this journal exists to prevent. Recover conservatively.
        return recoverFrom(attempt, context, cause = null)
    }

    /**
     * The failure path: roll back onto the attempt's own rollback snapshot (the journal names it
     * when the runtime reserved one — between the live-file mutation and the snapshot's
     * promotion it is the only file holding the true pre-attempt database) and classify the
     * result for the caller (spec §8.4 "safe retry vs terminal recovery").
     */
    private suspend fun recoverFrom(
        attempt: RestoreAttempt,
        context: RestoreInProgressContext?,
        cause: Throwable?,
    ): PreflightOutcome {
        if (cause != null && context != null) {
            reporter.recordRestoreTimeFailure(
                exception = cause,
                context = context,
                appVersionName = platformInfo.appVersionName(),
            )
        }
        val rollback = databaseReplacement.rollbackToPreRestoreBackup(
            sourcePath = attempt.rollbackSnapshotPath,
            effects = ScenarioOneRollbackEffects(attempt.id),
        )
        return when (rollback) {
            is DatabaseReplacementResult.Committed -> {
                val effectsError = rollback.effectsError
                if (effectsError == null) {
                    PreflightOutcome.RestoreRolledBack
                } else {
                    // The rollback swap committed but its durable bookkeeping did not: the
                    // journal still names an unresolved attempt, so the live file's provenance
                    // is not provable. Terminal recovery, not a restart-and-hope loop.
                    logger.w { "scenario-1 rollback committed without a durable record: $effectsError" }
                    PreflightOutcome.RecoveryRequired
                }
            }

            // PROVEN pre-PONR rejection: nothing was closed, mutated or torn down, so the live
            // database is intact and open and the launch may continue on the SAFE path — the
            // assets and the journal entry stay for the next attempt.
            is DatabaseReplacementResult.RejectedBeforeMutation -> {
                logger.w { "scenario-1 rollback rejected pre-mutation: ${rollback.error}" }
                PreflightOutcome.RetrySafe
            }

            // Post-PONR, a closed handle, or a terminal runtime: this process must NOT arm
            // DB-bound work or show the main UI over an unknown database. Assets preserved;
            // the user reaches an explicit recovery surface.
            is DatabaseReplacementResult.RecoveredByRollback,
            is DatabaseReplacementResult.FailedAfterMutation,
            is DatabaseReplacementResult.FatalNoGeneration,
            -> {
                logger.w { "scenario-1 rollback ended post-mutation ($rollback) — recovery required" }
                PreflightOutcome.RecoveryRequired
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
        val attemptId = UUID.randomUUID().toString()
        val result = databaseReplacement.rollbackToPreRestoreBackup(
            effects = UndoRollbackEffects(attemptId, onCommitted),
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
            is DatabaseReplacementResult.FatalNoGeneration,
            -> {
                logger.w { "Undo restore rollback did not commit: $result" }
                UndoRestoreOutcome.IoFailure
            }
        }
    }

    /** The undo transaction's typed compensation — runs on the transaction's coroutine. */
    private inner class UndoRollbackEffects(
        override val attemptId: String,
        private val acknowledge: suspend () -> Unit,
    ) : DatabaseReplacementEffects {

        override suspend fun onBeforeMutation(rollbackSnapshotPath: String) {
            val claimed = restoreStateRepository.beginAttempt(
                RestoreAttempt(
                    id = attemptId,
                    kind = RestoreAttempt.Kind.Rollback,
                    phase = RestoreAttempt.Phase.Prepared,
                    context = null,
                    rollbackSnapshotPath = null,
                ),
            )
            check(claimed) { "another unresolved attempt owns the journal slot; refusing to undo" }
        }

        override suspend fun onMutationCommitted() {
            check(restoreStateRepository.recordAttemptCommitted(attemptId)) {
                "the journal slot is no longer owned by attempt $attemptId"
            }
        }

        override suspend fun onCommitted() {
            restoreStateRepository.resolveAttempt(attemptId)
            restoreStateRepository.clearPreRestoreBackupAvailable()
            appDialogPublisher.publish(AppDialog.UndoRestoreSuccess)
            acknowledge()
        }

        override suspend fun onRejectedBeforeMutation(error: BackupError) {
            restoreStateRepository.resolveAttempt(attemptId)
        }

        /** Post-PONR: leave the attempt unresolved so the next launch completes the recovery. */
        override suspend fun onFailedAfterMutation(error: BackupError) = Unit

        override suspend fun onFatal() = Unit
    }

    private suspend fun handleRestoreSuccess(
        attempt: RestoreAttempt,
        context: RestoreInProgressContext,
    ) {
        restoreStateRepository.resolveAttempt(attempt.id)
        restoreStateRepository.markPreRestoreBackupAvailable(context.startedAtEpochMs)
        appDialogPublisher.publish(
            AppDialog.RestoreSuccess(
                restoredAtEpochMs = System.currentTimeMillis(),
                previousVersionAvailable = true,
            ),
        )
    }

    /** The scenario-1 rollback's typed compensation — runs on the transaction's coroutine. */
    private inner class ScenarioOneRollbackEffects(
        /** Reuses the RECOVERED attempt's id: this rollback finishes that attempt, not a new one. */
        override val attemptId: String,
    ) : DatabaseReplacementEffects {

        /** The recovered attempt already owns the slot; re-claiming with its id is idempotent. */
        override suspend fun onBeforeMutation(rollbackSnapshotPath: String) {
            restoreStateRepository.beginAttempt(
                RestoreAttempt(
                    id = attemptId,
                    kind = RestoreAttempt.Kind.Rollback,
                    phase = RestoreAttempt.Phase.Prepared,
                    context = null,
                    rollbackSnapshotPath = null,
                ),
            )
        }

        override suspend fun onMutationCommitted() {
            check(restoreStateRepository.recordAttemptCommitted(attemptId)) {
                "the journal slot is no longer owned by attempt $attemptId"
            }
        }

        override suspend fun onCommitted() {
            restoreStateRepository.resolveAttempt(attemptId)
            // No undo slot after a failure rollback — the preserved file was consumed.
            restoreStateRepository.clearPreRestoreBackupAvailable()
            appDialogPublisher.publish(
                AppDialog.RestoreFailure(reason = BackupErrorCodeForFailure),
            )
        }

        /**
         * Every non-commit outcome leaves the attempt UNRESOLVED and every asset in place: the
         * next launch re-enters this pre-flight and retries. The user still gets truthful
         * feedback — publishing a dialog touches no recovery asset.
         */
        override suspend fun onRejectedBeforeMutation(error: BackupError) = publishFailureDialog()

        override suspend fun onFailedAfterMutation(error: BackupError) = publishFailureDialog()

        override suspend fun onFatal() = publishFailureDialog()

        private suspend fun publishFailureDialog() {
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
        /** No unresolved attempt — this was a normal launch. */
        NoOp,

        /**
         * The attempt was durably COMMITTED and verified; success dialog published. Continue
         * startup normally.
         */
        RestoreSucceeded,

        /**
         * The failure-path rollback COMMITTED. Caller MUST restart the app because the
         * in-process Room handle is stale (it already opened the failed db).
         */
        RestoreRolledBack,

        /**
         * PROVEN pre-PONR rejection: nothing was closed, mutated or torn down, so the live
         * database is intact and open. The launch continues on the SAFE path (spec §8.4) with
         * every asset and the journal entry preserved for the next attempt — no restart, which
         * would loop, and no recovery surface, which the intact database does not warrant.
         */
        RetrySafe,

        /**
         * TERMINAL recovery: the mutation's outcome is unknown or the runtime is fatal, so this
         * process must arm NO DB-bound work (query planner, repositories, dialog observer) and
         * must not show the main UI over a database of unknown provenance. Every recovery asset
         * is preserved and the user is routed to the explicit recovery surface.
         */
        RecoveryRequired,
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
