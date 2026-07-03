// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import android.content.Intent
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for managing the backup-provider session. Surface is intentionally narrow
 * — provider-specific concerns (scope strings, account chooser variants, token
 * refresh schedules) live in the impl module.
 */
interface BackupAuth {

    /**
     * Hot, replayable view of the current session. Emits [AuthState.SignedOut] on
     * subscribe when no session exists; transitions to [AuthState.SignedIn] after a
     * successful sign-in and back to [AuthState.SignedOut] after sign-out or
     * revocation. Distinct on identity.
     */
    val state: StateFlow<AuthState>

    /**
     * Attempts to obtain a session. Returns [SignInResult.Success] when a cached
     * credential is reused, [SignInResult.NeedsResolution] when the provider
     * requires user interaction (caller must launch the supplied `IntentSender` and
     * forward the result via [completeSignIn]), or [SignInResult.Failure] when
     * sign-in could not be attempted.
     */
    suspend fun signIn(): SignInResult

    /**
     * Hot stream of whether the optional `drive.file` (visible-Drive) scope is currently
     * granted. Drives the AI-export toggle's enabled state and gates the export runner.
     * Re-derived on every authorize, so a later revocation flips it back to `false`.
     */
    fun observeDriveFileGranted(): Flow<Boolean>

    /**
     * Requests the optional `drive.file` scope on the already-connected account (incremental
     * grant for AI export). Same [SignInResult] contract as [signIn]: [SignInResult.Success]
     * if already granted, [SignInResult.NeedsResolution] to launch the consent flow (forward
     * the result via [completeSignIn]), or [SignInResult.Failure]. Does NOT start a fresh
     * sign-in — it adds a scope to the existing session.
     */
    suspend fun requestDriveFileAccess(): SignInResult

    /**
     * Completes a sign-in that previously returned [SignInResult.NeedsResolution].
     * [intentData] is the `Intent` delivered by the activity result of the sender
     * launched by the caller; `null` indicates the user cancelled. Returns the
     * resolved [Account] or a [BackupResult.Failure] when the resolution Intent did
     * not yield a usable credential.
     */
    suspend fun completeSignIn(intentData: Intent?): BackupResult<Account>

    /**
     * Drops the current session and clears any cached credential. Returns
     * [BackupResult.Success] even when no session existed — the post-state is the
     * same. Returns [BackupResult.Failure] only when the provider rejected the
     * revoke request.
     */
    suspend fun signOut(): BackupResult<Unit>
}
