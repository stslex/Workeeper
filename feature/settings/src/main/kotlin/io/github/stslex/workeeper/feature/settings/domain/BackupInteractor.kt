// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolutionOutcome
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import kotlinx.coroutines.flow.Flow

interface BackupInteractor {

    val authState: Flow<BackupAuthDomain>

    /** Hot stream of the `drive.file` grant; drives the AI-export toggle's effective state. */
    val driveFileGranted: Flow<Boolean>

    suspend fun signIn(): SignInOutcomeDomain

    /** Requests the optional `drive.file` scope; any resolution goes through [completeSignIn]. */
    suspend fun requestDriveFileAccess(): SignInOutcomeDomain

    /** One-shot read of whether `drive.file` is currently granted (post-grant reconciliation). */
    suspend fun isDriveFileGranted(): Boolean

    suspend fun completeSignIn(outcome: AuthResolutionOutcome): BackupResult<AccountDomain>

    suspend fun signOut(): BackupResult<Unit>

    /** Best-effort snapshot removal; must run before [signOut] revokes the grant. Never throws. */
    suspend fun deleteAiExportSnapshots()

    suspend fun createBackup(): BackupResult<Unit>

    suspend fun listLatestBackup(): BackupResult<BackupSummaryDomain?>

    suspend fun listBackups(): BackupResult<List<BackupSummaryDomain>>

    /** Restores the most recent backup. GUARD: the caller MUST restart the app on Success. */
    suspend fun restoreLatest(): BackupResult<Unit>
}
