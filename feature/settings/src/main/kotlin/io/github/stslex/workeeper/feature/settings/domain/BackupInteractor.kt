// SPDX-License-Identifier: GPL-3.0-only
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

    /** Hot stream of the `drive.file` grant; drives the AI-export toggle's effective state. */
    val driveFileGranted: Flow<Boolean>

    suspend fun signIn(): SignInOutcomeDomain

    /**
     * Requests the optional `drive.file` (visible-Drive) scope on the already-connected
     * account — the incremental grant for AI export. Does NOT start a fresh sign-in. Same
     * [SignInOutcomeDomain] contract as [signIn]; the resolution result (if any) is forwarded
     * via [completeSignIn], exactly like sign-in.
     */
    suspend fun requestDriveFileAccess(): SignInOutcomeDomain

    /** One-shot read of whether `drive.file` is currently granted (post-grant reconciliation). */
    suspend fun isDriveFileGranted(): Boolean

    suspend fun completeSignIn(resultIntent: Intent?): BackupResult<AccountDomain>

    suspend fun signOut(): BackupResult<Unit>

    /**
     * Best-effort removal of previously-exported AI snapshots from the visible Drive folder.
     * Invoked when the user disables AI export (consent withdrawal) and during sign-out, BEFORE
     * [signOut] revokes the grant — afterwards the files can no longer be reached. Never throws.
     */
    suspend fun deleteAiExportSnapshots()

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
