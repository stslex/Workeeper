// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError

/**
 * Outcome of [io.github.stslex.workeeper.core.data.backup.api.BackupAuth.signIn]; sign-in can
 * complete silently or require a UI flow, so callers must handle all branches.
 */
sealed interface SignInResult {

    /** Sign-in completed silently; the session is ready to use. */
    data class Success(val account: Account) : SignInResult

    /** Needs user interaction: act on [resolution], then call `BackupAuth.completeSignIn`. */
    data class NeedsResolution(val resolution: AuthResolution) : SignInResult

    /**
     * A credential came back with a hard-required scope declined; [missingScopes] is diagnostic.
     * GUARD: do not cache the token; drop it provider-side so the next sign-in re-prompts.
     */
    data class PartialGrant(val missingScopes: List<String>) : SignInResult

    /** Sign-in could not be attempted (e.g. no network, provider unavailable). */
    data class Failure(val error: BackupError) : SignInResult
}
