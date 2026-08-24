// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolutionOutcome
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Contract for managing the backup-provider session; provider specifics live in the impl. */
interface BackupAuth {

    /** Hot, replayable view of the current session. Distinct on identity. */
    val state: StateFlow<AuthState>

    /** Attempts to obtain a session; [SignInResult.NeedsResolution] needs [completeSignIn]. */
    suspend fun signIn(): SignInResult

    /** Hot stream of the optional `drive.file` grant, re-derived on every authorize. */
    fun observeDriveFileGranted(): Flow<Boolean>

    /** Requests the optional `drive.file` scope on the already-connected account. */
    suspend fun requestDriveFileAccess(): SignInResult

    /** Completes a sign-in that returned [SignInResult.NeedsResolution]; null payload = cancel. */
    suspend fun completeSignIn(outcome: AuthResolutionOutcome): BackupResult<Account>

    /** Drops the session and clears any cached credential; succeeds when none existed. */
    suspend fun signOut(): BackupResult<Unit>
}
