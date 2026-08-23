// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * The restore-latest orchestration, extracted from `BackupInteractorImpl` per the domain-layer
 * rule (multi-step orchestration → single-method use case): list → schema gates (BackupTooNew /
 * MissingMigrationPath) → preserve the live db (undo/rollback slot) → mark `restore_in_progress`
 * (crash safety: a death after this point routes the next launch through Scenario 1) → download →
 * hand the swap to the runtime-owned [DatabaseReplacement] transaction (Phase 5 R2, spec §8.5) →
 * temp cleanup. Order, error taxonomy, and the pre-swap failure cleanup are unchanged from the
 * pre-extraction body; on Android production the caller's restart flow follows a Success exactly
 * as before.
 */
@Inject
class RestoreLatestBackupUseCase(
    private val backupStorage: BackupStorage,
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val databaseReplacement: DatabaseReplacement,
    private val restoreStateRepository: RestoreStateRepository,
    private val tempFileProvider: TempFileProvider,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val logger = Log.tag("RestoreLatestBackupUseCase")

    @Suppress("ReturnCount")
    suspend operator fun invoke(): BackupResult<Unit> = withContext(dispatcher) {
        val ref = when (val listResult = backupStorage.listBackups()) {
            is BackupResult.Success -> listResult.data.firstOrNull()
                ?: return@withContext BackupResult.Failure(
                    BackupError.CorruptedBackup(reason = NO_BACKUPS_REASON),
                )

            is BackupResult.Failure -> return@withContext listResult
        }

        val currentSchemaVersion = snapshotProvider.currentSchemaVersion()
        val backupSchemaVersion = ref.manifest.dbSchemaVersion
        if (backupSchemaVersion > currentSchemaVersion) {
            return@withContext BackupResult.Failure(
                BackupError.BackupTooNew(
                    backupSchemaVersion = backupSchemaVersion,
                    appSchemaVersion = currentSchemaVersion,
                ),
            )
        }
        if (backupSchemaVersion < currentSchemaVersion &&
            !snapshotProvider.hasMigrationPath(from = backupSchemaVersion, to = currentSchemaVersion)
        ) {
            return@withContext BackupResult.Failure(
                BackupError.MissingMigrationPath(
                    backupSchemaVersion = backupSchemaVersion,
                    appSchemaVersion = currentSchemaVersion,
                ),
            )
        }

        // The rollback snapshot is NOT taken here: reserving it belongs to the serialized
        // transaction (spec §8.5a), which takes it after validation and before anything
        // irreversible, so two concurrent restores can never overwrite each other's slot and a
        // rejected attempt discards only its own reservation.

        val tempFile = tempFileProvider.createTempFile(TEMP_RESTORE_PREFIX, TEMP_RESTORE_SUFFIX)
        try {
            val download = backupStorage.downloadBackup(ref, tempFile)
            if (download is BackupResult.Failure) {
                // Pre-submission failure: no transaction, no journal entry and no reservation
                // exist yet, so there is nothing to compensate.
                return@withContext download
            }
            // Ownership of tempFile transfers to the runtime AT SUBMISSION (staged copy); the
            // `finally` below deletes only the original path — a no-op once staged — so a
            // cancelled caller can never destroy the file the transaction is swapping in. All
            // compensation rides the typed effects object ON the transaction's coroutine.
            val snapshotResult = databaseReplacement.restoreFromSnapshot(
                source = tempFile,
                effects = RestoreTransactionEffects(
                    attemptId = UUID.randomUUID().toString(),
                    context = RestoreInProgressContext(
                        backupSchemaVersion = backupSchemaVersion,
                        backupCreatedAtEpochMs = ref.manifest.createdAtEpochMs,
                        backupAppVersion = ref.manifest.appVersion,
                        startedAtEpochMs = System.currentTimeMillis(),
                    ),
                ),
            )
            when (snapshotResult) {
                is DatabaseReplacementResult.Committed -> {
                    val effectsError = snapshotResult.effectsError
                    if (effectsError == null) {
                        BackupResult.Success(Unit)
                    } else {
                        // R4.2: a pre-durable failure can no longer surface as `Committed` (it
                        // maps to FailedAfterMutation / the bounded recovery). A non-null
                        // effectsError here means the TERMINAL `onCommitted` callback itself
                        // failed after a durable commit — defensive Failure mapping either way.
                        logger.w { "restore committed with failed terminal effects: $effectsError" }
                        BackupResult.Failure(effectsError)
                    }
                }

                // Compensation already ran INSIDE the transaction (onRejectedBeforeMutation).
                is DatabaseReplacementResult.RejectedBeforeMutation ->
                    BackupResult.Failure(snapshotResult.error)

                // The data is the PRE-restore data: restore-FAILURE semantics — never a success
                // dialog, never an undo offer.
                is DatabaseReplacementResult.RecoveredByRollback ->
                    BackupResult.Failure(snapshotResult.error)

                // Post-PONR without recovery: every asset preserved and the journal left at
                // `Prepared`, which routes the next launch to recovery.
                is DatabaseReplacementResult.FailedAfterMutation ->
                    BackupResult.Failure(snapshotResult.error)

                is DatabaseReplacementResult.FatalNoGeneration -> BackupResult.Failure(
                    BackupError.Io(IOException("replacement fatal: no generation serving")),
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    /**
     * The restore transaction's typed compensation (spec §8.5): every method runs on the
     * TRANSACTION's coroutine, so a dead initiator never strands it. Idempotent; DataStore-only.
     */
    private inner class RestoreTransactionEffects(
        override val attemptId: String,
        private val context: RestoreInProgressContext,
    ) : DatabaseReplacementEffects {

        /**
         * Claims the durable attempt slot as
         * [io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt.Phase.Prepared]
         * — identity, manifest context and the runtime's reserved rollback path in ONE atomic
         * write, before anything irreversible. Throwing is how the transaction is REJECTED when
         * another unresolved attempt still owns the slot: a second restore must never inherit
         * or overwrite the first's bookkeeping.
         */
        override suspend fun onBeforeMutation(rollbackSnapshotPath: String) {
            val claimed = restoreStateRepository.beginAttempt(
                RestoreAttempt(
                    id = attemptId,
                    kind = RestoreAttempt.Kind.Restore,
                    phase = RestoreAttempt.Phase.Prepared,
                    context = context,
                    rollbackSnapshotPath = rollbackSnapshotPath.takeIf { it.isNotEmpty() },
                ),
            )
            check(claimed) {
                "another unresolved restore attempt owns the journal slot; refusing to start"
            }
        }

        /** The swap committed: record it durably — the only source of a later success verdict. */
        override suspend fun onMutationCommitted() {
            val recorded = restoreStateRepository.recordAttemptCommitted(attemptId)
            check(recorded) { "the journal slot is no longer owned by attempt $attemptId" }
        }

        /** Nothing irreversible happened → release the slot; the reservation is the runtime's. */
        override suspend fun onRejectedBeforeMutation(error: BackupError) {
            restoreStateRepository.resolveAttempt(attemptId)
        }

        /**
         * In-process recovery already rolled back: the attempt is finished. Data-bearing write
         * FIRST, resolve LAST (R4 invariant 7): a death in between leaves the journal entry
         * for the next launch's conservative recovery instead of a resolved journal with
         * half-done bookkeeping.
         *
         * The availability verdict comes from GROUND TRUTH — the canonical file's existence
         * (R4 review): a reservation-sourced recovery never touched the canonical, so the
         * PREVIOUS restore's undo remains valid and clearing it here would be the cross-owner
         * invalidation invariant 3 bans; a canonical-consuming recovery leaves no file, so the
         * flag clears.
         */
        override suspend fun onRecoveredByRollback(error: BackupError) {
            if (snapshotProvider.getPreRestoreBackupFile() == null) {
                restoreStateRepository.clearPreRestoreBackupAvailable()
            }
            restoreStateRepository.resolveAttempt(attemptId)
        }

        /**
         * Post-PONR with no recovery: leave the attempt UNRESOLVED (`Prepared`). That durable
         * state is what routes the next launch to the recovery path instead of letting a schema
         * peek claim a success this attempt cannot prove.
         */
        override suspend fun onFailedAfterMutation(error: BackupError) = Unit

        /** Terminal runtime — same reasoning as [onFailedAfterMutation]: leave it unresolved. */
        override suspend fun onFatal() = Unit
    }

    private companion object {
        const val TEMP_RESTORE_PREFIX = "restore_"
        const val TEMP_RESTORE_SUFFIX = ".db"
        const val NO_BACKUPS_REASON = "no backups available"
    }
}
