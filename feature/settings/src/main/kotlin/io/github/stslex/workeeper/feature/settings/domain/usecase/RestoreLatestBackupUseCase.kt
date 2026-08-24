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
 * Downloads a compatible latest backup, then submits it to the runtime-owned replacement
 * transaction. Reservation and journal claiming belong to that transaction, after download.
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

        val tempFile = tempFileProvider.createTempFile(TEMP_RESTORE_PREFIX, TEMP_RESTORE_SUFFIX)
        try {
            val download = backupStorage.downloadBackup(ref, tempFile)
            if (download is BackupResult.Failure) {
                return@withContext download
            }
            // Submission stages the source, so this caller's cleanup cannot strand the transaction.
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
                        // A committed terminal effect failed; do not report clean success.
                        logger.w { "restore committed with failed terminal effects: $effectsError" }
                        BackupResult.Failure(effectsError)
                    }
                }

                is DatabaseReplacementResult.RejectedBeforeMutation ->
                    BackupResult.Failure(snapshotResult.error)

                is DatabaseReplacementResult.RecoveredByRollback ->
                    BackupResult.Failure(snapshotResult.error)

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

    /** Idempotent DataStore effects run on the runtime transaction coroutine. */
    private inner class RestoreTransactionEffects(
        override val attemptId: String,
        private val context: RestoreInProgressContext,
    ) : DatabaseReplacementEffects {

        /** Claims this attempt's `Prepared` journal slot before mutation. */
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

        /** Records the only journal phase that may later produce success. */
        override suspend fun onMutationCommitted() {
            val recorded = restoreStateRepository.recordAttemptCommitted(attemptId)
            check(recorded) { "the journal slot is no longer owned by attempt $attemptId" }
        }

        /** Pre-mutation rejection releases only this attempt's slot. */
        override suspend fun onRejectedBeforeMutation(error: BackupError) {
            restoreStateRepository.resolveAttempt(attemptId)
        }

        /** Resolve after state writes; canonical-file existence decides undo availability. */
        override suspend fun onRecoveredByRollback(error: BackupError) {
            if (snapshotProvider.getPreRestoreBackupFile() == null) {
                restoreStateRepository.clearPreRestoreBackupAvailable()
            }
            restoreStateRepository.resolveAttempt(attemptId)
        }

        /** Preserve Prepared state so the next launch recovers conservatively. */
        override suspend fun onFailedAfterMutation(error: BackupError) = Unit

        /** Preserve unresolved state for recovery. */
        override suspend fun onFatal() = Unit
    }

    private companion object {
        const val TEMP_RESTORE_PREFIX = "restore_"
        const val TEMP_RESTORE_SUFFIX = ".db"
        const val NO_BACKUPS_REASON = "no backups available"
    }
}
