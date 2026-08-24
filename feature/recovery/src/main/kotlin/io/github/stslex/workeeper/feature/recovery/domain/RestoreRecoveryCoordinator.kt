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
        runPostRestoreLaunch().also { lastPreflightOutcome = it }

    private suspend fun runPostRestoreLaunch(): PreflightOutcome {
        val attempt = restoreStateRepository.getAttempt() ?: return PreflightOutcome.NoOp
        val context = attempt.context

        // Only a durable Committed restore may be verified as success.
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
                restoreStateRepository.resolveAttempt(attempt.id)
            }.onFailure { error ->
                logger.e(error, "committed-rollback finalization failed — it will replay")
            }
            logger.w { "an interrupted rollback was already committed — bookkeeping finished" }
            return PreflightOutcome.RecoveryCompleted
        }

        // Prepared, legacy, and unparsable attempts are unknown and recover conservatively.
        return recoverFrom(attempt, context, cause = null)
    }

    /** Rolls back from the attempt-owned source; only a clean commit proves recovery. */
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
        /** Reuses the recovered attempt's id rather than creating a second owner. */
        override val attemptId: String,
        /** `null` means this rollback uses and consumes the canonical undo slot. */
        private val sourcePath: String?,
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
            appDialogPublisher.publish(
                AppDialog.RestoreFailure(reason = BackupErrorCodeForFailure),
            )
            restoreStateRepository.resolveAttempt(attemptId)
        }

        /** Non-commit outcomes preserve the unresolved attempt and its assets. */
        override suspend fun onRejectedBeforeMutation(error: BackupError) = publishFailureDialog()

        override suspend fun onFailedAfterMutation(error: BackupError) = publishFailureDialog()

        override suspend fun onFatal() = publishFailureDialog()

        private suspend fun publishFailureDialog() {
            appDialogPublisher.publish(
                AppDialog.RestoreFailure(reason = BackupErrorCodeForFailure),
            )
        }
    }

    fun restartApp() {
        appReinitializer.reinitialize()
    }

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

    private companion object {
        /** Recovery exceptions have no public error-code mapping. */
        val BackupErrorCodeForFailure = BackupErrorCode.Unknown
    }
}
