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
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

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

        // Preserve the live database so the post-restart pre-flight (Scenario 1)
        // and any later user-initiated undo (Scenario 3) have something to roll
        // back to.
        when (val preserved = snapshotProvider.preserveCurrentDb()) {
            is BackupResult.Success -> Unit
            is BackupResult.Failure -> return@withContext preserved
        }

        val tempFile = tempFileProvider.createTempFile(TEMP_RESTORE_PREFIX, TEMP_RESTORE_SUFFIX)
        try {
            val download = backupStorage.downloadBackup(ref, tempFile)
            if (download is BackupResult.Failure) {
                // Pre-submission failure: no transaction exists yet, so this cleanup is
                // caller-owned — the only compensation that stays outside the effects object.
                rollbackPreSwapFailure()
                return@withContext download
            }
            // Ownership of tempFile transfers to the runtime AT SUBMISSION (staged copy); the
            // `finally` below deletes only the original path — a no-op once staged — so a
            // cancelled caller can never destroy the file the transaction is swapping in. All
            // compensation rides the typed effects object ON the transaction's coroutine.
            val snapshotResult = databaseReplacement.restoreFromSnapshot(
                source = tempFile,
                effects = RestoreTransactionEffects(
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
                    snapshotResult.effectsError?.let { effectsError ->
                        // The restore itself committed; only post-commit bookkeeping failed —
                        // surfaced (never a silently clean commit), success semantics kept.
                        logger.w { "restore committed with failed effects: $effectsError" }
                    }
                    BackupResult.Success(Unit)
                }

                // Compensation already ran INSIDE the transaction (onRejectedBeforeMutation).
                is DatabaseReplacementResult.RejectedBeforeMutation ->
                    BackupResult.Failure(snapshotResult.error)

                // The data is the PRE-restore data (mandate 3): restore-FAILURE semantics —
                // never a success dialog, never an undo offer. Marker compensation ran inside
                // the transaction (onRecoveredByRollback).
                is DatabaseReplacementResult.RecoveredByRollback ->
                    BackupResult.Failure(snapshotResult.error)

                // Post-PONR without recovery: every asset preserved; the journal flag written
                // by onFailedAfterMutation routes the next launch to the failure path.
                is DatabaseReplacementResult.FailedAfterMutation ->
                    BackupResult.Failure(snapshotResult.error)

                DatabaseReplacementResult.FatalNoGeneration -> BackupResult.Failure(
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
        private val context: RestoreInProgressContext,
    ) : DatabaseReplacementEffects {

        /** Crash-safety marker, written inside the mutex before anything irreversible. */
        override suspend fun onBeforeMutation() {
            restoreStateRepository.markRestoreInProgress(context)
        }

        /**
         * Nothing irreversible happened → pre-swap cleanup is legal (and only here). Same body
         * as [rollbackPreSwapFailure], inlined: a private-method call from this inner class
         * would force a synthetic accessor (Android Lint `SyntheticAccessor`).
         */
        override suspend fun onRejectedBeforeMutation(error: BackupError) {
            snapshotProvider.deletePreRestoreBackup()
            restoreStateRepository.clearRestoreInProgress()
        }

        /** Recovery already rolled back in-process: the marker is stale, the slot consumed. */
        override suspend fun onRecoveredByRollback(error: BackupError) {
            restoreStateRepository.clearRestoreInProgress()
            restoreStateRepository.clearPreRestoreBackupAvailable()
        }

        /**
         * Post-PONR, no recovery ran: journal the interrupted mutation so the next launch's
         * pre-flight takes the FAILURE path even when a schema peek would succeed against the
         * untouched old file (a false "restore succeeded" is the lie this flag prevents).
         */
        override suspend fun onFailedAfterMutation(error: BackupError) {
            restoreStateRepository.markRestoreMutationInterrupted()
        }

        /** Terminal runtime — same journal entry; the next process recovers. */
        override suspend fun onFatal() {
            restoreStateRepository.markRestoreMutationInterrupted()
        }
    }

    /**
     * Clean up the preserved snapshot + DataStore flag when the restore fails with NOTHING
     * irreversible done (download failure before submission; pre-mutation rejection via the
     * effects object). The live database was never mutated, so file-level rollback is
     * unnecessary — just delete the now-stale preserved snapshot and clear the in-progress flag.
     */
    private suspend fun rollbackPreSwapFailure() {
        snapshotProvider.deletePreRestoreBackup()
        restoreStateRepository.clearRestoreInProgress()
    }

    private companion object {
        const val TEMP_RESTORE_PREFIX = "restore_"
        const val TEMP_RESTORE_SUFFIX = ".db"
        const val NO_BACKUPS_REASON = "no backups available"
    }
}
