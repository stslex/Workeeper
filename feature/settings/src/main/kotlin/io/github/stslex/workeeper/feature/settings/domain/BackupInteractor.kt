package io.github.stslex.workeeper.feature.settings.domain

import android.content.Intent
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import kotlinx.coroutines.flow.Flow

internal interface BackupInteractor {

    val authState: Flow<BackupAuthDomain>

    suspend fun signIn(): SignInOutcomeDomain

    suspend fun completeSignIn(resultIntent: Intent?): BackupResult<AccountDomain>

    suspend fun signOut(): BackupResult<Unit>

    suspend fun createBackup(): BackupResult<Unit>

    suspend fun listLatestBackup(): BackupResult<BackupSummaryDomain?>

    suspend fun listBackups(): BackupResult<List<BackupSummaryDomain>>

    /**
     * Restores the most recent backup available for the signed-in account. v1 surfaces
     * latest only — no picker UI. A v1.1 follow-up will introduce a picker driven from
     * `Action.Backup.RequestRestore`; tracked in `documentation/tech-debt.md`.
     *
     * The caller MUST trigger an app restart on `BackupResult.Success` — the Room graph
     * is stale after `restoreFromSnapshot` and only a fresh process can rebuild it.
     */
    suspend fun restoreLatest(): BackupResult<Unit>
}
