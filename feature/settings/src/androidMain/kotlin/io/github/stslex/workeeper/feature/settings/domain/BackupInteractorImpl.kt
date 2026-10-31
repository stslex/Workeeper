// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolutionOutcome
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.settings.di.SettingsScope
import io.github.stslex.workeeper.feature.settings.domain.mapper.BackupDomainMapper.toDomain
import io.github.stslex.workeeper.feature.settings.domain.mapper.BackupDomainMapper.toSummary
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import io.github.stslex.workeeper.feature.settings.domain.usecase.RestoreLatestBackupUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Inject
@SingleIn(SettingsScope::class)
class BackupInteractorImpl(
    private val backupAuth: BackupAuth,
    private val backupStorage: BackupStorage,
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val restoreLatestBackup: RestoreLatestBackupUseCase,
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

    // The multi-step restore orchestration lives in RestoreLatestBackupUseCase (domain rule:
    // multi-repository, conditionally-branching flows extract into a single-method use case).
    override suspend fun restoreLatest(): BackupResult<Unit> = restoreLatestBackup()

    private fun <T, R> BackupResult<T>.mapSuccess(transform: (T) -> R): BackupResult<R> =
        when (this) {
            is BackupResult.Success -> BackupResult.Success(transform(data))
            is BackupResult.Failure -> this
        }

    private companion object {
        const val TEMP_BACKUP_PREFIX = "backup_"
        const val TEMP_BACKUP_SUFFIX = ".db"
    }
}
