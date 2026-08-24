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
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreRecoveryReporter
import java.io.File
import java.util.UUID

/**
 * App-scoped coordinator for attempt-journal preflight recovery and user-requested undo.
 *
 * A `Prepared` attempt is unknown and never produces success; only a durable `Committed` restore
 * can be verified by a schema peek. See the Phase-5 startup-processor spec for crash recovery.
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

    /** Cached preflight verdict read by the Activity layer. */
    @Volatile
    var lastPreflightOutcome: PreflightOutcome? = null
        private set

    /** True when this launch must route to the recovery surface instead of the main UI. */
    val recoverySurfaceRequired: Boolean
        get() = lastPreflightOutcome == PreflightOutcome.RecoveryRequired

    suspend fun handlePostRestoreLaunch(): PreflightOutcome =
        runPostRestoreLaunch().also { outcome ->
            // Preserve BEFORE publishing the verdict: `recoverySurfaceRequired` is what routes
            // MainActivity to the surface that shares this file.
            if (outcome == PreflightOutcome.RecoveryRequired) preserveDbForRecoveryExport()
            lastPreflightOutcome = outcome
        }

    /**
     * Scenario-1 recovery never reaches [StartupMigrationCoordinator]'s route — `StartupProcessor`
     * returns `RouteToRecovery` above the branch that runs it — so the live database is preserved
     * here, or `RecoveryActivity`'s export button has nothing to share.
     */
    private suspend fun preserveDbForRecoveryExport() {
        // GUARD: recovery is the last line of defence; a preserve failure must never crash the
        // launch that routes to it.
        val preserved = runCatching { snapshotProvider.preserveDbBeforeMigration() }
            .onFailure { error -> logger.e(error, "preserving the live db for recovery failed") }
            .getOrNull()
        if (preserved == null) {
            logger.w { "recovery export will be empty: no live database was preserved" }
        }
    }

    private suspend fun runPostRestoreLaunch(): PreflightOutcome {
        val attempt = restoreStateRepository.getAttempt() ?: return PreflightOutcome.NoOp
        val context = attempt.context
        val committedRestore = attempt.phase == RestoreAttempt.Phase.Committed &&
            attempt.kind == RestoreAttempt.Kind.Restore
        // The undo slot is installed only AFTER the durable record, so a committed restore may
        // still owe its install. Finish it before anything reads the canonical (spec §8.5a).
        val undoSlotReady = committedRestore && completeOwedPromotion(attempt)

        // Only a durable Committed restore may be verified as success.
        if (committedRestore && context != null) {
            val peekResult = runCatching { snapshotProvider.currentSchemaVersion() }
            if (peekResult.isSuccess) {
                handleRestoreSuccess(attempt, context, undoSlotReady)
                return PreflightOutcome.RestoreSucceeded
            }
            return recoverFrom(attempt, context, peekResult.exceptionOrNull(), undoSlotReady)
        }

        // Replay committed rollback finalization from its durable source discriminator.
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
                appDialogPublisher.publish(committedRollbackDialog(rollbackOrigin(attempt)))
                restoreStateRepository.resolveAttempt(attempt.id)
            }.onFailure { error ->
                logger.e(error, "committed-rollback finalization failed — it will replay")
            }
            logger.w { "an interrupted rollback was already committed — bookkeeping finished" }
            return PreflightOutcome.RecoveryCompleted
        }

        // Prepared, legacy, and unparsable attempts are unknown and recover conservatively.
        return recoverFrom(attempt, context, cause = null, canonicalIsOurs = undoSlotReady)
    }

    /** Idempotently finishes a durably committed attempt's owed undo-slot install (§8.5a). */
    private suspend fun completeOwedPromotion(attempt: RestoreAttempt): Boolean {
        val reservation = attempt.rollbackSnapshotPath?.let(::File)
        val result = runCatching {
            snapshotProvider.completePromotedRollback(reservation, attempt.id)
        }.getOrElse { error ->
            logger.e(error, "installing the undo image failed")
            return false
        }
        if (result is BackupResult.Failure) {
            logger.w { "no undo image for this restore: ${result.error}" }
            return false
        }
        return true
    }

    /** A rollback's terminal follows its journaled origin; anything unknown reads as recovery. */
    private fun rollbackOrigin(attempt: RestoreAttempt): RestoreAttempt.RollbackOrigin =
        attempt.rollbackOrigin.takeIf { attempt.kind == RestoreAttempt.Kind.Rollback }
            ?: RestoreAttempt.RollbackOrigin.ScenarioOneRecovery

    /** Rolls back from the attempt-owned source; only a clean commit proves recovery. */
    private suspend fun recoverFrom(
        attempt: RestoreAttempt,
        context: RestoreInProgressContext?,
        cause: Throwable?,
        canonicalIsOurs: Boolean,
    ): PreflightOutcome {
        if (context != null) {
            reporter.recordRestoreTimeFailure(
                // A Prepared attempt has no throwable — nothing threw, the process died. Report
                // it anyway: that IS the interrupted-restore case the journal exists to catch.
                exception = cause ?: InterruptedRestoreException(attempt),
                context = context,
                appVersionName = platformInfo.appVersionName(),
            )
        }
        // Only a landed install makes the canonical THIS attempt's pre-image; while the install
        // is still owed, the journal-named reservation is the one owner-correct source.
        val installOwed = attempt.phase == RestoreAttempt.Phase.Committed && !canonicalIsOurs
        if (installOwed && attempt.rollbackSnapshotPath == null) {
            logger.w { "a committed attempt owes its undo install and names no reservation" }
            return PreflightOutcome.RecoveryRequired
        }
        val sourcePath = attempt.rollbackSnapshotPath
            .takeIf { attempt.phase == RestoreAttempt.Phase.Prepared || installOwed }
        val origin = rollbackOrigin(attempt)
        val rollback = databaseReplacement.rollbackToPreRestoreBackup(
            sourcePath = sourcePath,
            effects = RecoveryRollbackEffects(attempt.id, sourcePath, origin),
        )
        return when (rollback) {
            is DatabaseReplacementResult.Committed -> {
                val effectsError = rollback.effectsError
                if (effectsError == null) {
                    PreflightOutcome.RestoreRolledBack
                } else {
                    // The journal remains unresolved, so recovery is still required.
                    logger.w { "scenario-1 rollback committed without a durable record: $effectsError" }
                    PreflightOutcome.RecoveryRequired
                }
            }

            is DatabaseReplacementResult.RejectedBeforeMutation,
            is DatabaseReplacementResult.RecoveredByRollback,
            is DatabaseReplacementResult.FailedAfterMutation,
            is DatabaseReplacementResult.FatalNoGeneration,
            -> {
                logger.w { "scenario-1 rollback did not commit ($rollback) — recovery required" }
                PreflightOutcome.RecoveryRequired
            }
        }
    }

    /** Runs undo; callers acknowledge only [UndoRestoreOutcome.Succeeded] or FileMissing. */
    internal suspend fun performUndoRestore(
        onCommitted: suspend () -> Unit = {},
    ): UndoRestoreOutcome {
        if (snapshotProvider.getPreRestoreBackupFile() == null) {
            // The cached availability flag may outlive the file.
            restoreStateRepository.clearPreRestoreBackupAvailable()
            return UndoRestoreOutcome.FileMissing
        }
        // Runtime-owned effects survive an initiator cancelled with the outgoing generation.
        val attemptId = UUID.randomUUID().toString()
        val result = databaseReplacement.rollbackToPreRestoreBackup(
            effects = UndoRollbackEffects(attemptId, onCommitted),
        )
        return when (result) {
            is DatabaseReplacementResult.Committed -> {
                result.effectsError?.let { effectsError ->
                    // Surface post-commit effect failure rather than reporting clean success.
                    logger.w { "undo rollback committed with failed effects: $effectsError" }
                }
                UndoRestoreOutcome.Succeeded
            }

            is DatabaseReplacementResult.RejectedBeforeMutation ->
                if (result.error is BackupError.CorruptedBackup) {
                    // The undo image is not a usable database. Stop offering it rather than
                    // failing the same way on every retry; the file itself is kept, never
                    // deleted, so a validator false negative cannot destroy a real image.
                    logger.w { "the undo source is unusable: ${result.error}" }
                    restoreStateRepository.clearPreRestoreBackupAvailable()
                    UndoRestoreOutcome.SourceUnusable
                } else {
                    logger.w { "Undo restore rollback did not commit: $result" }
                    UndoRestoreOutcome.IoFailure
                }

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
                    rollbackOrigin = RestoreAttempt.RollbackOrigin.UserUndo,
                ),
            )
            check(claimed) { "another unresolved attempt owns the journal slot; refusing to undo" }
        }

        override suspend fun onMutationCommitted() {
            check(restoreStateRepository.recordAttemptCommitted(attemptId)) {
                "the journal slot is no longer owned by attempt $attemptId"
            }
        }

        /** Persist state and dialog before resolving; caller acknowledgement remains last. */
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

    /** Finalizes a verified restore in replay-safe, data-bearing-first order. */
    private suspend fun handleRestoreSuccess(
        attempt: RestoreAttempt,
        context: RestoreInProgressContext,
        undoSlotReady: Boolean,
    ) {
        runCatching {
            // No install, no new undo claim: the PREVIOUS restore's record still truthfully
            // describes the file that is actually in the slot.
            if (undoSlotReady) {
                restoreStateRepository.markPreRestoreBackupAvailable(context.startedAtEpochMs)
            }
            appDialogPublisher.publish(
                AppDialog.RestoreSuccess(
                    restoredAtEpochMs = System.currentTimeMillis(),
                    previousVersionAvailable = undoSlotReady,
                ),
            )
            if (undoSlotReady) {
                attempt.rollbackSnapshotPath?.let { path -> runCatching { File(path).delete() } }
            }
            restoreStateRepository.resolveAttempt(attempt.id)
        }.onFailure { error ->
            logger.e(error, "restore-success finalization failed — it will replay next launch")
        }
    }

    /**
     * The recovery rollback's typed compensation — runs on the transaction's coroutine. Its
     * user-facing terminal follows the journaled [origin], never the shape of the attempt.
     */
    private inner class RecoveryRollbackEffects(
        /** Reuses the recovered attempt's id rather than creating a second owner. */
        override val attemptId: String,
        /** `null` means this rollback uses and consumes the canonical undo slot. */
        private val sourcePath: String?,
        private val origin: RestoreAttempt.RollbackOrigin,
    ) : DatabaseReplacementEffects {

        /** Reclaim must succeed: only the journal owner may mutate or resolve its attempt. */
        override suspend fun onBeforeMutation(rollbackSnapshotPath: String) {
            val claimed = restoreStateRepository.beginAttempt(
                RestoreAttempt(
                    id = attemptId,
                    kind = RestoreAttempt.Kind.Rollback,
                    phase = RestoreAttempt.Phase.Prepared,
                    context = null,
                    rollbackSnapshotPath = sourcePath,
                    // Carried through the re-claim, or the NEXT replay reads it as unknown.
                    rollbackOrigin = origin,
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

        /** Clear availability only when canonical source was consumed; resolve after state writes. */
        override suspend fun onCommitted() {
            if (sourcePath == null) {
                restoreStateRepository.clearPreRestoreBackupAvailable()
            }
            appDialogPublisher.publish(committedRollbackDialog(origin))
            restoreStateRepository.resolveAttempt(attemptId)
        }

        /** Non-commit outcomes preserve the unresolved attempt and its assets. */
        override suspend fun onRejectedBeforeMutation(error: BackupError) = publishFailureDialog()

        override suspend fun onFailedAfterMutation(error: BackupError) = publishFailureDialog()

        override suspend fun onFatal() = publishFailureDialog()

        /** A failed undo replay stays silent: its file and availability survive for a retry. */
        private suspend fun publishFailureDialog() {
            if (origin == RestoreAttempt.RollbackOrigin.ScenarioOneRecovery) {
                appDialogPublisher.publish(recoveryFailureDialog())
            }
        }
    }

    fun restartApp() {
        appReinitializer.reinitialize()
    }

    /** Stands in for the throwable an interrupted restore never produced. */
    internal class InterruptedRestoreException(attempt: RestoreAttempt) : IllegalStateException(
        "restore attempt ${attempt.id} (${attempt.kind}) was unresolved at launch " +
            "in phase ${attempt.phase}",
    )

    /** Discriminator for the post-restart pre-flight result. */
    enum class PreflightOutcome {
        /** No unresolved attempt — this was a normal launch. */
        NoOp,

        /** A durable restore verified successfully. */
        RestoreSucceeded,

        /** A rollback committed; the caller must rebuild or restart its stale DB handle. */
        RestoreRolledBack,

        /** Unproven DB provenance: arm no DB-bound work and route to recovery. */
        RecoveryRequired,

        /** A previously committed rollback finished replay-safe bookkeeping. */
        RecoveryCompleted,
    }
}

/** The dialog a durably committed rollback owes the user, from its journaled origin. */
internal fun committedRollbackDialog(origin: RestoreAttempt.RollbackOrigin): AppDialog =
    when (origin) {
        RestoreAttempt.RollbackOrigin.UserUndo -> AppDialog.UndoRestoreSuccess
        RestoreAttempt.RollbackOrigin.ScenarioOneRecovery -> recoveryFailureDialog()
    }

/** Recovery exceptions have no public error-code mapping. */
internal fun recoveryFailureDialog(): AppDialog =
    AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
