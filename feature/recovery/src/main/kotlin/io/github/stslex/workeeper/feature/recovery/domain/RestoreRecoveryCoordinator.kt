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
import java.io.File
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

        // A COMMITTED **rollback** already applied its snapshot durably: the live database IS
        // the rolled-back data and nothing further may be swapped. Re-driving it would look for
        // a preserved file the rollback itself consumed, fail, and leave this attempt
        // unresolved FOREVER — refusing every future restore and undo (whose `beginAttempt`
        // would then see a foreign owner). Finish its bookkeeping instead and continue.
        //
        // SOURCE-AWARE and replay-safe (R4.1): `rollbackSnapshotPath` is the DURABLE
        // discriminator of which file the committed rollback applied — null means the canonical
        // slot, non-null the exact named source — so the finalization never infers ownership
        // from file existence alone. A canonical-sourced rollback finishes the canonical's
        // consumption idempotently and clears availability: a death between the commit record
        // and the consume must NOT leave the same undo offered again, because replaying it
        // after later writes would erase them. An explicit-source rollback consumes exactly its
        // named file; the canonical — the PREVIOUS restore's undo — survives with its
        // availability, and the flag clears only when the canonical is actually absent. The
        // attempt resolves LAST and the whole branch replays idempotently: a failure mid-way
        // keeps the `Committed` journal so the next launch reaches the same terminal state.
        // The user-facing dialog of the interrupted rollback is deliberately NOT replayed:
        // `Kind.Rollback` does not record recovery-vs-undo INTENT (only the applied source),
        // and inventing a dialog would be worse than at-most-once feedback — the accepted,
        // documented residual (spec §8.5b).
        if (attempt.phase == RestoreAttempt.Phase.Committed &&
            attempt.kind == RestoreAttempt.Kind.Rollback
        ) {
            runCatching {
                val explicitPath = attempt.rollbackSnapshotPath
                if (explicitPath == null) {
                    snapshotProvider.deletePreRestoreBackup()
                    restoreStateRepository.clearPreRestoreBackupAvailable()
                } else {
                    runCatching { File(explicitPath).delete() }
                    if (snapshotProvider.getPreRestoreBackupFile() == null) {
                        restoreStateRepository.clearPreRestoreBackupAvailable()
                    }
                }
                restoreStateRepository.resolveAttempt(attempt.id)
            }.onFailure { error ->
                logger.e(error, "committed-rollback finalization failed — it will replay")
            }
            logger.w { "an interrupted rollback was already committed — bookkeeping finished" }
            return PreflightOutcome.RecoveryCompleted
        }

        // PREPARED (or any unknown/legacy state): the outcome of the mutation is NOT durably
        // known. The live file may be the old one, the new one, or a partially replaced one, and
        // a schema peek would happily succeed on the untouched OLD database — the exact false
        // "restore succeeded" this journal exists to prevent. Recover conservatively.
        return recoverFrom(attempt, context, cause = null)
    }

    /**
     * The failure path: roll back onto the attempt's own recovery source and classify the
     * result for the caller (spec §8.4/§8.5b).
     *
     * **Source-owner identity (R4 invariant 2).** For a PREPARED attempt the journal-named
     * reservation is authoritative — between the live-file mutation and the reservation's
     * promotion it is the only file holding the true pre-attempt database, and if it is missing
     * the canonical slot (another attempt's older snapshot) is never substituted: the seam
     * rejects with a typed error and this launch goes to terminal recovery. For a COMMITTED
     * attempt the promotion is durably known to have completed BEFORE `Committed` could exist
     * (the commit-sequence ordering), so the canonical slot provably holds THIS attempt's
     * pre-image and is the correct source — the retained reservation may already be cleaned up.
     *
     * **Outcome truth (R4 blocker B).** Only a rollback that COMMITTED with clean bookkeeping
     * proves the live database's provenance. Everything else — including a pre-PONR rejection,
     * which proves only that THIS rollback did not mutate, never what the original attempt did
     * to the live file — is terminal recovery: no Main UI, no DB-bound work armed, every asset
     * and the journal entry preserved for an explicit recovery attempt.
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
        val sourcePath = attempt.rollbackSnapshotPath
            .takeIf { attempt.phase == RestoreAttempt.Phase.Prepared }
        val rollback = databaseReplacement.rollbackToPreRestoreBackup(
            sourcePath = sourcePath,
            effects = ScenarioOneRollbackEffects(attempt.id, sourcePath),
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

            // A rejection proves only that THIS rollback did not mutate — the ORIGINAL attempt
            // may have died before, during, or after its own mutation, so the live database's
            // provenance is exactly as unknown as before the rollback ran. Terminal recovery
            // (never the old RetrySafe, which armed DB-bound work and composed Main UI over it).
            is DatabaseReplacementResult.RejectedBeforeMutation,
            // Post-PONR, a closed handle, or a terminal runtime: same verdict.
            is DatabaseReplacementResult.RecoveredByRollback,
            is DatabaseReplacementResult.FailedAfterMutation,
            is DatabaseReplacementResult.FatalNoGeneration,
            -> {
                logger.w { "scenario-1 rollback did not commit ($rollback) — recovery required" }
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

        /**
         * Data-bearing-first, resolve LAST (R4 invariant 7): the undo consumed the canonical
         * slot, so the availability clear and the persisted dialog land before the attempt
         * resolves — a death in between leaves a `Committed` rollback the next launch's replay
         * branch finishes idempotently (ground-truth flag, resolve), instead of a resolved
         * journal with half-done bookkeeping. The caller's acknowledge stays last: a dismiss
         * before the success dialog persists would lose the dialog across a crash.
         */
        override suspend fun onCommitted() {
            restoreStateRepository.clearPreRestoreBackupAvailable()
            appDialogPublisher.publish(AppDialog.UndoRestoreSuccess)
            restoreStateRepository.resolveAttempt(attemptId)
            acknowledge()
        }

        override suspend fun onRejectedBeforeMutation(error: BackupError) {
            restoreStateRepository.resolveAttempt(attemptId)
        }

        /** Post-PONR: leave the attempt unresolved so the next launch completes the recovery. */
        override suspend fun onFailedAfterMutation(error: BackupError) = Unit

        override suspend fun onFatal() = Unit
    }

    /**
     * The committed restore's finalization — replay-safe by ordering (R4 invariant 7): every
     * data-bearing write lands BEFORE the attempt resolves, so a death anywhere in the sequence
     * leaves the journal at `Committed` and the next launch replays this whole method
     * idempotently. Pre-R4 the resolve came first, and a death in between erased the replay
     * token while hiding a perfectly valid undo snapshot forever.
     *
     *  1. mark undo availability (idempotent; the promotion durably preceded `Committed`, so
     *     the canonical slot provably holds this attempt's pre-image);
     *  2. publish the success dialog (persisted; re-publishing on replay is at-least-once
     *     feedback, which for a success notice is the right side to err on);
     *  3. delete the RETAINED reservation copy the promotion left behind (idempotent — the
     *     clean same-process path already discarded it);
     *  4. resolve the attempt LAST.
     *
     * A failure inside the sequence is logged and the launch still proceeds as a success — the
     * restore IS durably committed and verified; the unresolved journal makes the NEXT launch
     * retry the finalization (a new restore/undo in the meantime is refused by `beginAttempt`
     * until then, which is the honest refusal).
     */
    private suspend fun handleRestoreSuccess(
        attempt: RestoreAttempt,
        context: RestoreInProgressContext,
    ) {
        runCatching {
            restoreStateRepository.markPreRestoreBackupAvailable(context.startedAtEpochMs)
            appDialogPublisher.publish(
                AppDialog.RestoreSuccess(
                    restoredAtEpochMs = System.currentTimeMillis(),
                    previousVersionAvailable = true,
                ),
            )
            attempt.rollbackSnapshotPath?.let { path -> runCatching { File(path).delete() } }
            restoreStateRepository.resolveAttempt(attempt.id)
        }.onFailure { error ->
            logger.e(error, "restore-success finalization failed — it will replay next launch")
        }
    }

    /** The scenario-1 rollback's typed compensation — runs on the transaction's coroutine. */
    private inner class ScenarioOneRollbackEffects(
        /** Reuses the RECOVERED attempt's id: this rollback finishes that attempt, not a new one. */
        override val attemptId: String,
        /**
         * The source path this rollback was SUBMITTED with, CARRIED THROUGH the re-claim: the
         * recovered attempt's reservation for a `Prepared` attempt (re-claiming with null would
         * erase the journal's only pointer to the file holding the true pre-attempt database),
         * or null for a `Committed` attempt, whose source is provably the canonical slot. Null
         * also decides the flag policy below: it means the CANONICAL slot is what gets applied
         * and consumed.
         */
        private val sourcePath: String?,
    ) : DatabaseReplacementEffects {

        /**
         * The recovered attempt already owns the slot; re-claiming with its id is idempotent —
         * and for a pre-R4 LEGACY marker, this claim is what atomically converts the id-less
         * boolean into an owner-scoped record. The result is CHECKED (R4 blocker C): a refusal
         * means the slot is owned by someone else entirely, and mutating the live database with
         * no journal claim of our own would leave the interrupted state unrecoverable — the
         * throw rejects the transaction before anything irreversible instead.
         */
        override suspend fun onBeforeMutation(rollbackSnapshotPath: String) {
            val claimed = restoreStateRepository.beginAttempt(
                RestoreAttempt(
                    id = attemptId,
                    kind = RestoreAttempt.Kind.Rollback,
                    phase = RestoreAttempt.Phase.Prepared,
                    context = null,
                    rollbackSnapshotPath = sourcePath,
                ),
            )
            check(claimed) {
                "the journal slot is not owned by recovered attempt $attemptId; refusing to roll back"
            }
        }

        override suspend fun onMutationCommitted() {
            check(restoreStateRepository.recordAttemptCommitted(attemptId)) {
                "the journal slot is no longer owned by attempt $attemptId"
            }
        }

        /**
         * Data-bearing-first, resolve LAST (R4 invariant 7). The availability flag clears ONLY
         * when the applied source was the canonical slot ([sourcePath] == null — the slot was
         * consumed): a reservation-sourced recovery never touched the canonical, so the
         * PREVIOUS restore's undo remains valid and revoking it would be exactly the
         * cross-owner invalidation R4 invariant 3 bans. A death before the resolve leaves a
         * `Committed` rollback the replay branch finishes idempotently.
         */
        override suspend fun onCommitted() {
            if (sourcePath == null) {
                restoreStateRepository.clearPreRestoreBackupAvailable()
            }
            appDialogPublisher.publish(
                AppDialog.RestoreFailure(reason = BackupErrorCodeForFailure),
            )
            restoreStateRepository.resolveAttempt(attemptId)
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
         * TERMINAL recovery: the recovery rollback did not durably commit — rejected, failed
         * post-PONR, fatal, or committed with broken bookkeeping — so the live database's
         * provenance is unproven. This process must arm NO DB-bound work (query planner,
         * repositories, dialog observer) and must not show the main UI. Every recovery asset
         * and the journal entry are preserved and the user is routed to the explicit recovery
         * surface.
         *
         * There is deliberately NO "safe retry" sibling (R4 blocker B): a pre-PONR rejection of
         * the RECOVERY rollback proves only that the rollback did not mutate — never what the
         * ORIGINAL `Prepared` attempt did to the live file before dying — so no rollback
         * outcome short of a clean durable commit can license Main UI over that file. The one
         * in-process consequence is that a graph-only reinitialize whose preflight finds an
         * unresolved `Prepared` attempt now ABORTS (outgoing generation keeps serving, journal
         * intact) instead of publishing a fresh generation over unproven data — which is what
         * locked invariant 4 requires.
         */
        RecoveryRequired,

        /**
         * An interrupted rollback was found already durably COMMITTED: the live database is the
         * rolled-back data, its bookkeeping has now been finished, and nothing further is owed.
         * The launch continues normally — re-driving it would consume a file the rollback
         * already applied and strand the attempt forever.
         */
        RecoveryCompleted,
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
