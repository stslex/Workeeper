// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolutionOutcome
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.settings.domain.mapper.BackupDomainMapper.toDomain
import io.github.stslex.workeeper.feature.settings.domain.mapper.BackupDomainMapper.toSummary
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class BackupInteractorImpl @Inject constructor(
    private val backupAuth: BackupAuth,
    private val backupStorage: BackupStorage,
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val restoreStateRepository: RestoreStateRepository,
    private val snapshotExportRunner: SnapshotExportRunner,
    private val platformInfo: PlatformInfoProvider,
    private val tempFileProvider: TempFileProvider,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : BackupInteractor {

    override val authState: Flow<BackupAuthDomain> = backupAuth.state.map { it.toDomain() }

    override val driveFileGranted: Flow<Boolean> get() = backupAuth.observeDriveFileGranted()

    override suspend fun signIn(): SignInOutcomeDomain = backupAuth.signIn().toDomain()

    override suspend fun requestDriveFileAccess(): SignInOutcomeDomain =
        backupAuth.requestDriveFileAccess().toDomain()

    override suspend fun isDriveFileGranted(): Boolean =
        backupAuth.observeDriveFileGranted().first()

    override suspend fun completeSignIn(outcome: AuthResolutionOutcome): BackupResult<AccountDomain> =
        backupAuth.completeSignIn(outcome).mapSuccess {
            it.toDomain()
        }

    override suspend fun signOut(): BackupResult<Unit> = backupAuth.signOut()

    override suspend fun deleteAiExportSnapshots() = snapshotExportRunner.clearSnapshots()

    override suspend fun createBackup(): BackupResult<Unit> = withContext(dispatcher) {
        val result = createBinaryBackup()
        // Best-effort AI snapshot AFTER the binary backup. Wrapped so a runner fault can never
        // affect the binary result (the runner also swallows internally); D2 decoupling.
        runCatching { snapshotExportRunner.runIfEligible() }
        result
    }

    private suspend fun createBinaryBackup(): BackupResult<Unit> {
        // TODO(tech-debt): temp-file staging inside a domain interactor is a smell. The
        //  TempFileProvider seam is a tactical decoupling (domain no longer imports
        //  Context.cacheDir); the strategic fix is moving snapshot staging into the data
        //  layer. Not done in Phase A.
        val tempFile = tempFileProvider.createTempFile(TEMP_BACKUP_PREFIX, TEMP_BACKUP_SUFFIX)
        return try {
            val capture = snapshotProvider.captureSnapshot(tempFile)
            if (capture is BackupResult.Failure) return capture
            val manifest = BackupManifest(
                appVersion = platformInfo.appVersionName(),
                dbSchemaVersion = snapshotProvider.currentSchemaVersion(),
                createdAtEpochMs = System.currentTimeMillis(),
                dbFileSizeBytes = tempFile.length(),
                deviceModel = platformInfo.deviceModel(),
            )
            backupStorage.uploadBackup(tempFile, manifest).mapSuccess { }
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun listLatestBackup(): BackupResult<BackupSummaryDomain?> =
        backupStorage.listBackups().mapSuccess { refs -> refs.firstOrNull()?.toSummary() }

    override suspend fun listBackups(): BackupResult<List<BackupSummaryDomain>> =
        backupStorage.listBackups().mapSuccess { refs -> refs.map { it.toSummary() } }

    override suspend fun restoreLatest(): BackupResult<Unit> = withContext(dispatcher) {
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

        val tempFile = tempFileProvider.createTempFile(TEMP_RESTORE_PREFIX, TEMP_BACKUP_SUFFIX)
        try {
            val download = backupStorage.downloadBackup(ref, tempFile)
            if (download is BackupResult.Failure) {
                rollbackPreSwapFailure()
                return@withContext download
            }
            val snapshotResult = snapshotProvider.restoreFromSnapshot(tempFile)
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
     * **before** `restoreFromSnapshot` commits the swap. The live database was
     * never mutated, so file-level rollback is unnecessary — just delete the
     * now-stale preserved snapshot and clear the in-progress flag.
     */
    private suspend fun rollbackPreSwapFailure() {
        snapshotProvider.deletePreRestoreBackup()
        restoreStateRepository.clearRestoreInProgress()
    }

    private fun <T, R> BackupResult<T>.mapSuccess(transform: (T) -> R): BackupResult<R> =
        when (this) {
            is BackupResult.Success -> BackupResult.Success(transform(data))
            is BackupResult.Failure -> this
        }

    private companion object {
        const val TEMP_BACKUP_PREFIX = "backup_"
        const val TEMP_RESTORE_PREFIX = "restore_"
        const val TEMP_BACKUP_SUFFIX = ".db"
        const val NO_BACKUPS_REASON = "no backups available"
    }
}
