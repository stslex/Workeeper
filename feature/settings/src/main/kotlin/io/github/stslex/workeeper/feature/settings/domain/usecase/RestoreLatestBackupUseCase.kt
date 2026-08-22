// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

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
        // back to. Mark restore_in_progress with the manifest payload so the
        // pre-flight can attach Crashlytics keys / diagnostics if rollback fires.
        when (val preserved = snapshotProvider.preserveCurrentDb()) {
            is BackupResult.Success -> Unit
            is BackupResult.Failure -> return@withContext preserved
        }
        restoreStateRepository.markRestoreInProgress(
            RestoreInProgressContext(
                backupSchemaVersion = backupSchemaVersion,
                backupCreatedAtEpochMs = ref.manifest.createdAtEpochMs,
                backupAppVersion = ref.manifest.appVersion,
                startedAtEpochMs = System.currentTimeMillis(),
            ),
        )

        val tempFile = tempFileProvider.createTempFile(TEMP_RESTORE_PREFIX, TEMP_RESTORE_SUFFIX)
        try {
            val download = backupStorage.downloadBackup(ref, tempFile)
            if (download is BackupResult.Failure) {
                rollbackPreSwapFailure()
                return@withContext download
            }
            val snapshotResult = databaseReplacement.restoreFromSnapshot(tempFile)
            if (snapshotResult is BackupResult.Failure) {
                rollbackPreSwapFailure()
            }
            snapshotResult
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Clean up the preserved snapshot + DataStore flag when the restore fails
     * **before** the replacement transaction commits the swap. The live database was
     * never mutated, so file-level rollback is unnecessary — just delete the
     * now-stale preserved snapshot and clear the in-progress flag.
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
