package io.github.stslex.workeeper.feature.settings.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@ViewModelScoped
internal class BackupInteractorImpl @Inject constructor(
    private val backupAuth: BackupAuth,
    private val backupStorage: BackupStorage,
    private val snapshotProvider: DatabaseSnapshotProvider,
    @ApplicationContext private val context: Context,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : BackupInteractor {

    override val authState: Flow<BackupAuthDomain> = backupAuth.state.map { it.toDomain() }

    override suspend fun signIn(): SignInOutcomeDomain = backupAuth.signIn().toDomain()

    override suspend fun completeSignIn(resultIntent: Intent?): BackupResult<AccountDomain> =
        backupAuth.completeSignIn(resultIntent).mapSuccess {
            it.toDomain()
        }

    override suspend fun signOut(): BackupResult<Unit> = backupAuth.signOut()

    override suspend fun createBackup(): BackupResult<Unit> = withContext(dispatcher) {
        val tempFile = File.createTempFile(TEMP_BACKUP_PREFIX, TEMP_BACKUP_SUFFIX, context.cacheDir)
        try {
            val capture = snapshotProvider.captureSnapshot(tempFile)
            if (capture is BackupResult.Failure) return@withContext capture
            val manifest = BackupManifest(
                appVersion = readVersionName(),
                dbSchemaVersion = snapshotProvider.currentSchemaVersion(),
                createdAtEpochMs = System.currentTimeMillis(),
                dbFileSizeBytes = tempFile.length(),
                deviceModel = Build.MODEL,
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
        if (ref.manifest.dbSchemaVersion > currentSchemaVersion) {
            return@withContext BackupResult.Failure(
                BackupError.SchemaTooNew(
                    backupSchemaVersion = ref.manifest.dbSchemaVersion,
                    appSchemaVersion = currentSchemaVersion,
                ),
            )
        }

        val tempFile =
            File.createTempFile(TEMP_RESTORE_PREFIX, TEMP_BACKUP_SUFFIX, context.cacheDir)
        try {
            val download = backupStorage.downloadBackup(ref, tempFile)
            if (download is BackupResult.Failure) return@withContext download
            snapshotProvider.restoreFromSnapshot(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    private fun readVersionName(): String {
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return info.versionName.orEmpty()
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
