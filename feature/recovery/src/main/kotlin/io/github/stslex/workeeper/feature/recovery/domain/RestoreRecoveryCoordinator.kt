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
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndo
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndoTransition
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.LegacyRestoreOwners
import io.github.stslex.workeeper.core.data.backup.api.restore.LegacyRestoreState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreRecoveryReporter
import java.util.UUID

/** Installation-owned restore/undo recovery and verified-attempt finalization. */
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

    @Volatile
    var lastPreflightOutcome: PreflightOutcome? = null
        private set

    @Volatile
    var lastRecoveryExportOutcome: RecoveryExportOutcome? = null
        private set

    val recoverySurfaceRequired: Boolean
        get() = lastPreflightOutcome in setOf(
            PreflightOutcome.InterruptedRestore,
            PreflightOutcome.RecoveryRequired,
            PreflightOutcome.FinalizationPending,
        )

    suspend fun handlePostRestoreLaunch(): PreflightOutcome = runPostRestoreLaunch().also { outcome ->
        lastRecoveryExportOutcome = if (outcome in setOf(
                PreflightOutcome.InterruptedRestore,
                PreflightOutcome.RecoveryRequired,
                PreflightOutcome.FinalizationPending,
            )
        ) {
            preserveDbForRecoveryExport()
        } else {
            null
        }
        lastPreflightOutcome = outcome
    }

    private suspend fun preserveDbForRecoveryExport(): RecoveryExportOutcome =
        runCatching { snapshotProvider.preserveDbBeforeMigration() }.fold(
            onSuccess = { result ->
                when (result) {
                    is BackupResult.Success -> RecoveryExportOutcome.Available
                    is BackupResult.Failure -> {
                        logger.w { "durable recovery export unavailable: ${result.error}" }
                        RecoveryExportOutcome.Failed
                    }
                }
            },
            onFailure = { error ->
                logger.e(error, "preserving the live db for recovery failed")
                RecoveryExportOutcome.Failed
            },
        )

    private suspend fun runPostRestoreLaunch(): PreflightOutcome {
        val protocol = runCatching { restoreStateRepository.readProtocol() }.getOrElse { error ->
            logger.e(error, "reading restore protocol failed")
            return PreflightOutcome.RecoveryRequired
        }
        val state = when (protocol) {
            is RestoreProtocolRead.Current -> protocol.state
            is RestoreProtocolRead.Corrupt -> {
                logger.e(IllegalStateException(protocol.reason), "same-install restore state corrupt")
                return PreflightOutcome.RecoveryRequired
            }

            is RestoreProtocolRead.Legacy -> return migrateAndReplayLegacy(
                epoch = protocol.epoch,
                legacy = protocol.state,
            )
        }

        if (state.attempt != null && state.terminalOutbox != null) {
            return PreflightOutcome.RecoveryRequired
        }
        if (!terminalOutboxMatchesPointer(state)) {
            return PreflightOutcome.RecoveryRequired
        }
        val activeUndo = state.activeUndo
        if (activeUndo != null && activeUndoMustRemainUsable(state) &&
            !activeUndoIsUsable(activeUndo)
        ) {
            return PreflightOutcome.RecoveryRequired
        }
        if (!publishTerminalOutbox(state.terminalOutbox)) {
            return PreflightOutcome.FinalizationPending
        }
        return handleCurrentState(state)
    }

    /** Explicit replay-safe table for the released marker, availability flag, and positional C. */
    private suspend fun migrateAndReplayLegacy(
        epoch: InstallEpoch,
        legacy: LegacyRestoreState,
    ): PreflightOutcome {
        if (legacy.restoreInProgress) {
            val undoRef = UndoRef(LegacyRestoreOwners.InterruptedAttempt)
            val attempt = when (ensureLegacyUndoPublished(undoRef)) {
                LegacyUndoPublication.Available -> RestoreAttempt.Restore(
                    id = LegacyRestoreOwners.InterruptedAttempt,
                    phase = RestoreAttempt.Phase.Prepared,
                    context = legacy.context,
                    undoRef = undoRef,
                    sourceRef = null,
                )

                LegacyUndoPublication.Unavailable -> RestoreAttempt.Restore(
                    id = LegacyRestoreOwners.InterruptedAttempt,
                    phase = RestoreAttempt.Phase.Prepared,
                    context = legacy.context,
                    undoRef = null,
                    sourceRef = null,
                )

                LegacyUndoPublication.Failed -> return PreflightOutcome.RecoveryRequired
            }
            if (!installLegacyState(epoch, attempt, activeUndo = null)) {
                return PreflightOutcome.RecoveryRequired
            }
            if (attempt.undoRef != null) deleteLegacyUndoBestEffort()
            return handleCurrentState(
                RestoreProtocolState(epoch, attempt, activeUndo = null, terminalOutbox = null),
            )
        }

        val legacyOriginalDate = legacy.preRestoreOriginalDateEpochMs
        if (legacy.preRestoreBackupAvailable && legacyOriginalDate != null) {
            val ref = UndoRef(LegacyRestoreOwners.ActiveUndo)
            when (ensureLegacyUndoPublished(ref)) {
                LegacyUndoPublication.Available -> {
                    val active = ActiveUndo(
                        ref = ref,
                        originalDataDateEpochMs = legacyOriginalDate,
                    )
                    if (!installLegacyState(
                            epoch,
                            attempt = null,
                            activeUndo = active,
                        )
                    ) {
                        return PreflightOutcome.RecoveryRequired
                    }
                    deleteLegacyUndoBestEffort()
                    return PreflightOutcome.NoOp
                }

                LegacyUndoPublication.Failed -> return PreflightOutcome.RecoveryRequired
                LegacyUndoPublication.Unavailable -> Unit
            }
        }

        // Missing or unusable C makes released availability stale; installing empty state clears it.
        if (!installLegacyState(epoch, attempt = null, activeUndo = null)) {
            return PreflightOutcome.RecoveryRequired
        }
        deleteLegacyUndoBestEffort()
        return PreflightOutcome.NoOp
    }

    private suspend fun installLegacyState(
        epoch: InstallEpoch,
        attempt: RestoreAttempt?,
        activeUndo: ActiveUndo?,
    ): Boolean = runCatching {
        restoreStateRepository.installLegacyState(epoch, attempt, activeUndo)
    }.getOrElse { error ->
        logger.e(error, "installing replay-safe legacy restore state failed")
        false
    }

    private suspend fun ensureLegacyUndoPublished(ref: UndoRef): LegacyUndoPublication {
        if (snapshotProvider.getUndoFile(ref) != null) {
            if (snapshotProvider.migrateLegacyUndo(ref) is BackupResult.Failure) {
                return LegacyUndoPublication.Failed
            }
            return if (snapshotProvider.validateUndo(ref) is BackupResult.Success) {
                LegacyUndoPublication.Available
            } else {
                LegacyUndoPublication.Failed
            }
        }
        if (snapshotProvider.validateLegacyUndo() is BackupResult.Failure) {
            return LegacyUndoPublication.Unavailable
        }
        if (snapshotProvider.migrateLegacyUndo(ref) is BackupResult.Failure) {
            return LegacyUndoPublication.Failed
        }
        return if (snapshotProvider.validateUndo(ref) is BackupResult.Success) {
            LegacyUndoPublication.Available
        } else {
            LegacyUndoPublication.Failed
        }
    }

    private enum class LegacyUndoPublication {
        Available,
        Unavailable,
        Failed,
    }

    private suspend fun handleCurrentState(state: RestoreProtocolState): PreflightOutcome =
        when (val attempt = state.attempt) {
            null -> {
                deleteLegacyUndoBestEffort()
                PreflightOutcome.NoOp
            }

            is RestoreAttempt.Restore -> handleRestoreAttempt(attempt)
            is RestoreAttempt.Rollback -> handleRollbackAttempt(attempt)
        }

    private suspend fun handleRestoreAttempt(
        attempt: RestoreAttempt.Restore,
    ): PreflightOutcome {
        if (attempt.phase == RestoreAttempt.Phase.Prepared && attempt.undoRef == null) {
            return if (liveDatabaseIsHealthyAndSupported()) {
                PreflightOutcome.InterruptedRestore
            } else {
                PreflightOutcome.RecoveryRequired
            }
        }

        val context = attempt.context
        if (attempt.phase == RestoreAttempt.Phase.Committed && context != null) {
            val sourceRef = attempt.sourceRef ?: return PreflightOutcome.RecoveryRequired
            if (snapshotProvider.getRestoreSourceFile(sourceRef) == null) {
                return PreflightOutcome.RecoveryRequired
            }
            val verified = runCatching { snapshotProvider.currentSchemaVersion() }
            if (verified.isSuccess) {
                return finalizeVerifiedRestore(attempt, context)
            }
            recordRestoreFailure(attempt, context, verified.exceptionOrNull())
        } else if (context != null) {
            recordRestoreFailure(attempt, context, cause = null)
        }

        val undoRef = attempt.undoRef ?: return PreflightOutcome.RecoveryRequired
        return applyRestoreCompensation(attempt, undoRef)
    }

    private suspend fun finalizeVerifiedRestore(
        attempt: RestoreAttempt.Restore,
        context: RestoreInProgressContext,
    ): PreflightOutcome {
        val undoRef = attempt.undoRef ?: return PreflightOutcome.RecoveryRequired
        val active = if (snapshotProvider.getUndoFile(undoRef) == null) {
            // A verified N remains valid even when its undo disappeared; P must not be advertised.
            null
        } else {
            if (snapshotProvider.validateUndo(undoRef) is BackupResult.Failure) {
                return PreflightOutcome.RecoveryRequired
            }
            ActiveUndo(ref = undoRef, originalDataDateEpochMs = context.startedAtEpochMs)
        }
        val terminal = RestoreTerminal.RestoreSucceeded(
            owner = attempt.id,
            restoredAtEpochMs = System.currentTimeMillis(),
            previousVersionAvailable = active != null,
        )
        val finalized = runCatching {
            restoreStateRepository.finalizeAttempt(
                attemptId = attempt.id,
                activeUndoTransition = ActiveUndoTransition.Replace(active),
                terminal = terminal,
            )
        }.getOrElse { error ->
            logger.e(error, "verified restore finalization write failed")
            false
        }
        if (!finalized) return PreflightOutcome.FinalizationPending

        // GUARD: Rebuild publishes this only after fallible candidate arming; see Phase 5 §27.
        return PreflightOutcome.RestoreSucceeded
    }

    private suspend fun handleRollbackAttempt(
        attempt: RestoreAttempt.Rollback,
    ): PreflightOutcome {
        if (attempt.phase == RestoreAttempt.Phase.Prepared) {
            return applyPreparedRollback(attempt)
        }
        if (runCatching { snapshotProvider.currentSchemaVersion() }.isFailure) {
            return PreflightOutcome.RecoveryRequired
        }

        val terminal = when (attempt.origin) {
            RestoreAttempt.RollbackOrigin.UserUndo ->
                RestoreTerminal.UndoSucceeded(attempt.id)

            RestoreAttempt.RollbackOrigin.ScenarioOneRecovery ->
                RestoreTerminal.RestoreFailed(attempt.id, BackupErrorCode.Unknown)
        }
        val finalized = runCatching {
            restoreStateRepository.finalizeAttempt(
                attemptId = attempt.id,
                activeUndoTransition = ActiveUndoTransition.ClearIf(attempt.sourceRef),
                terminal = terminal,
            )
        }.getOrElse { error ->
            logger.e(error, "committed rollback finalization write failed")
            false
        }
        if (!finalized) return PreflightOutcome.FinalizationPending

        val published = publishTerminalOutbox(terminal)
        runCatching { snapshotProvider.deleteUndo(attempt.sourceRef) }
            .onFailure { error -> logger.w { "owned undo deletion deferred: $error" } }
        return if (published) {
            PreflightOutcome.RecoveryCompleted
        } else {
            PreflightOutcome.FinalizationPending
        }
    }

    private suspend fun applyRestoreCompensation(
        restore: RestoreAttempt.Restore,
        sourceRef: UndoRef,
    ): PreflightOutcome {
        val owner = newOwner()
        val rollback = RestoreAttempt.Rollback(
            id = owner,
            phase = RestoreAttempt.Phase.Prepared,
            sourceRef = sourceRef,
            origin = RestoreAttempt.RollbackOrigin.ScenarioOneRecovery,
        )
        val result = databaseReplacement.rollbackFromUndo(
            sourceRef = sourceRef,
            effects = RecoveryRollbackEffects(
                rollback = rollback,
                restoreOwner = restore.id,
            ),
        )
        return rollbackSubmissionOutcome(result)
    }

    private suspend fun applyPreparedRollback(
        rollback: RestoreAttempt.Rollback,
    ): PreflightOutcome {
        val result = databaseReplacement.rollbackFromUndo(
            sourceRef = rollback.sourceRef,
            effects = RecoveryRollbackEffects(rollback = rollback, restoreOwner = null),
        )
        return rollbackSubmissionOutcome(result)
    }

    private fun rollbackSubmissionOutcome(result: DatabaseReplacementResult): PreflightOutcome =
        when (result) {
            is DatabaseReplacementResult.Committed -> if (result.effectsError == null) {
                PreflightOutcome.RestoreRolledBack
            } else {
                PreflightOutcome.RecoveryRequired
            }

            is DatabaseReplacementResult.RejectedBeforeMutation,
            is DatabaseReplacementResult.RecoveredByRollback,
            is DatabaseReplacementResult.FailedAfterMutation,
            is DatabaseReplacementResult.FatalNoGeneration,
            -> PreflightOutcome.RecoveryRequired
        }

    internal suspend fun performUndoRestore(sourceRef: UndoRef): UndoRestoreOutcome {
        val current = restoreStateRepository.readProtocol() as? RestoreProtocolRead.Current
            ?: return UndoRestoreOutcome.RecoveryRequired
        if (current.state.activeUndo?.ref != sourceRef) return UndoRestoreOutcome.NotCurrent
        if (snapshotProvider.getUndoFile(sourceRef) == null) {
            return UndoRestoreOutcome.RecoveryRequired
        }

        val rollback = RestoreAttempt.Rollback(
            id = newOwner(),
            phase = RestoreAttempt.Phase.Prepared,
            sourceRef = sourceRef,
            origin = RestoreAttempt.RollbackOrigin.UserUndo,
        )
        val result = databaseReplacement.rollbackFromUndo(
            sourceRef = sourceRef,
            effects = UserRollbackEffects(rollback),
        )
        return when (result) {
            is DatabaseReplacementResult.Committed -> if (result.effectsError == null) {
                UndoRestoreOutcome.Succeeded
            } else {
                UndoRestoreOutcome.IoFailure
            }

            is DatabaseReplacementResult.RejectedBeforeMutation -> when (result.error) {
                is BackupError.CorruptedBackup -> UndoRestoreOutcome.RecoveryRequired
                else -> UndoRestoreOutcome.IoFailure
            }

            is DatabaseReplacementResult.RecoveredByRollback,
            is DatabaseReplacementResult.FailedAfterMutation,
            is DatabaseReplacementResult.FatalNoGeneration,
            -> UndoRestoreOutcome.IoFailure
        }
    }

    private inner class UserRollbackEffects(
        private val rollback: RestoreAttempt.Rollback,
    ) : DatabaseReplacementEffects {

        override val attemptId: RestoreOwnerId = rollback.id

        override suspend fun onBeforeMutation(
            undoRef: UndoRef,
            restoreSourceRef: RestoreSourceRef?,
        ) {
            check(undoRef == rollback.sourceRef && restoreSourceRef == null)
            check(restoreStateRepository.beginAttempt(rollback)) {
                "another unresolved attempt owns the journal slot"
            }
        }

        override suspend fun onMutationCommitted() = recordCommitted(rollback.id)

        override suspend fun onBeforeCompensation(
            rollbackOwner: RestoreOwnerId,
            appliedRef: UndoRef,
        ) = error("a requested rollback is never reclassified as restore compensation")

        override suspend fun onCompensationCommitted(rollbackOwner: RestoreOwnerId) =
            error("a requested rollback has no compensation commit")

        override suspend fun onRejectedBeforeMutation(error: BackupError) {
            restoreStateRepository.discardPreparedAttempt(rollback.id)
        }
    }

    private inner class RecoveryRollbackEffects(
        private val rollback: RestoreAttempt.Rollback,
        private val restoreOwner: RestoreOwnerId?,
    ) : DatabaseReplacementEffects {

        override val attemptId: RestoreOwnerId = rollback.id

        override suspend fun onBeforeMutation(
            undoRef: UndoRef,
            restoreSourceRef: RestoreSourceRef?,
        ) {
            check(undoRef == rollback.sourceRef && restoreSourceRef == null)
            val claimed = if (restoreOwner == null) {
                restoreStateRepository.beginAttempt(rollback)
            } else {
                restoreStateRepository.beginCompensation(restoreOwner, rollback)
            }
            check(claimed) { "recovery rollback no longer owns the journal transition" }
        }

        override suspend fun onMutationCommitted() = recordCommitted(rollback.id)

        override suspend fun onBeforeCompensation(
            rollbackOwner: RestoreOwnerId,
            appliedRef: UndoRef,
        ) = error("a recovery rollback is never a restore compensation")

        override suspend fun onCompensationCommitted(rollbackOwner: RestoreOwnerId) =
            error("a recovery rollback has no compensation commit")
    }

    internal suspend fun recordCommitted(owner: RestoreOwnerId) {
        check(restoreStateRepository.recordAttemptCommitted(owner)) {
            "the journal slot is no longer owned by $owner"
        }
    }

    private suspend fun publishTerminalOutbox(terminal: RestoreTerminal?): Boolean {
        terminal ?: return true
        val published = runCatching { appDialogPublisher.publish(terminal.toDialog()) }
            .onFailure { error -> logger.e(error, "terminal outbox publication deferred") }
            .isSuccess
        if (!published) return false

        // The dialog write is mandatory; acknowledgement is replay cleanup. See Phase 5 §8.5b.
        runCatching { restoreStateRepository.acknowledgeTerminal(terminal.owner) }
            .onFailure { error -> logger.e(error, "terminal outbox acknowledgement deferred") }
        return true
    }

    /** Returns false while the policy-ordered mandatory terminal publication is still pending. */
    suspend fun publishPendingTerminalOutbox(): Boolean {
        val state = runCatching { restoreStateRepository.readProtocol() }
            .getOrElse { error ->
                logger.w { "terminal outbox read deferred: $error" }
                return recordTerminalPublicationPending()
            } as? RestoreProtocolRead.Current ?: return recordTerminalPublicationPending()
        if (state.state.attempt != null || !terminalOutboxMatchesPointer(state.state)) {
            return recordTerminalPublicationPending()
        }
        val terminal = state.state.terminalOutbox as? RestoreTerminal.RestoreSucceeded
            ?: return recordTerminalPublicationPending()
        if (!publishTerminalOutbox(terminal)) {
            return recordTerminalPublicationPending()
        }
        return true
    }

    private suspend fun recordTerminalPublicationPending(): Boolean {
        lastRecoveryExportOutcome = preserveDbForRecoveryExport()
        lastPreflightOutcome = PreflightOutcome.FinalizationPending
        return false
    }

    /** Cold-start-only sweep; runtime transitions sweep at their serialized submission boundary. */
    suspend fun sweepRecoveryGarbage() {
        val state = runCatching { restoreStateRepository.readProtocol() }
            .getOrElse { error ->
                logger.w { "recovery garbage sweep state read deferred: $error" }
                return
            } as? RestoreProtocolRead.Current ?: return
        runCatching { snapshotProvider.sweepRecoveryFiles(state.state) }
            .onFailure { error -> logger.w { "recovery garbage sweep deferred: $error" } }
    }

    private suspend fun deleteLegacyUndoBestEffort() {
        runCatching { snapshotProvider.deleteLegacyPreRestore() }
            .onFailure { error -> logger.w { "legacy undo deletion deferred: $error" } }
    }

    private suspend fun liveDatabaseIsHealthyAndSupported(): Boolean {
        return snapshotProvider.inspectLiveDatabaseWithoutRoom() is BackupResult.Success
    }

    private suspend fun activeUndoIsUsable(activeUndo: ActiveUndo): Boolean =
        snapshotProvider.getUndoFile(activeUndo.ref) != null &&
            snapshotProvider.validateUndo(activeUndo.ref) is BackupResult.Success

    /** A committed rollback may finalize by descriptor after consuming its own exact source. */
    private fun activeUndoMustRemainUsable(state: RestoreProtocolState): Boolean {
        val activeRef = state.activeUndo?.ref ?: return false
        return when (val attempt = state.attempt) {
            is RestoreAttempt.Restore -> attempt.phase != RestoreAttempt.Phase.Committed
            is RestoreAttempt.Rollback ->
                attempt.phase != RestoreAttempt.Phase.Committed || attempt.sourceRef != activeRef

            null -> true
        }
    }

    private fun terminalOutboxMatchesPointer(state: RestoreProtocolState): Boolean =
        when (val terminal = state.terminalOutbox) {
            null -> true
            is RestoreTerminal.RestoreSucceeded -> {
                val active = state.activeUndo
                terminal.previousVersionAvailable == (active != null) &&
                    (active == null || active.ref.owner == terminal.owner)
            }

            is RestoreTerminal.UndoSucceeded -> state.activeUndo == null
            is RestoreTerminal.RestoreFailed -> true
        }

    private fun recordRestoreFailure(
        attempt: RestoreAttempt.Restore,
        context: RestoreInProgressContext,
        cause: Throwable?,
    ) {
        reporter.recordRestoreTimeFailure(
            exception = cause ?: InterruptedRestoreException(attempt),
            context = context,
            appVersionName = platformInfo.appVersionName(),
        )
    }

    fun restartApp() = appReinitializer.reinitialize()

    internal class InterruptedRestoreException(attempt: RestoreAttempt.Restore) :
        IllegalStateException(
            "restore attempt ${attempt.id} was unresolved at launch in phase ${attempt.phase}",
        )

    enum class PreflightOutcome {
        NoOp,
        RestoreSucceeded,
        RestoreRolledBack,
        InterruptedRestore,
        RecoveryRequired,
        FinalizationPending,
        RecoveryCompleted,
    }

    private companion object {
        fun newOwner(): RestoreOwnerId = RestoreOwnerId(UUID.randomUUID().toString())
    }
}

sealed interface RecoveryExportOutcome {
    data object Available : RecoveryExportOutcome
    data object Failed : RecoveryExportOutcome
}

internal fun RestoreTerminal.toDialog(): AppDialog = when (this) {
    is RestoreTerminal.RestoreSucceeded -> AppDialog.RestoreSuccess(
        restoredAtEpochMs = restoredAtEpochMs,
        previousVersionAvailable = previousVersionAvailable,
        terminalOwner = owner,
    )

    is RestoreTerminal.RestoreFailed -> AppDialog.RestoreFailure(reason, terminalOwner = owner)
    is RestoreTerminal.UndoSucceeded -> AppDialog.UndoRestoreSuccess(terminalOwner = owner)
}
