// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/**
 * Hot, current-value view of the backup provider session. Consumers observe this via
 * [io.github.stslex.workeeper.core.data.backup.api.BackupAuth.state] to render
 * sign-in UI and to gate calls that require a signed-in account.
 */
sealed interface AuthState {

    /** No active session. Calls that require authentication will fail until [SignedIn]. */
    data object SignedOut : AuthState

    /** Active session for [account]. */
    data class SignedIn(val account: Account) : AuthState
}
