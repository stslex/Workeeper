// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

import android.content.IntentSender
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError

/**
 * Outcome of [io.github.stslex.workeeper.core.data.backup.api.BackupAuth.signIn].
 *
 * The three-way shape exists because Google-style sign-in can complete silently
 * (cached credential) or require launching a UI flow before the account can be
 * resolved. Callers must handle all three branches.
 */
sealed interface SignInResult {

    /** Sign-in completed silently; the session is ready to use. */
    data class Success(val account: Account) : SignInResult

    /**
     * The provider needs the user to interact with a system UI before sign-in can
     * complete. Launch [intentSender], then hand the resulting `Intent` back via
     * [io.github.stslex.workeeper.core.data.backup.api.BackupAuth.completeSignIn].
     */
    data class NeedsResolution(val intentSender: IntentSender) : SignInResult

    /**
     * The provider returned a credential, but at least one hard-required scope was
     * declined on the consent screen. The impl MUST NOT cache the token or transition
     * to a signed-in state in this branch; it should also drop the just-issued bad
     * token from the provider's local cache so the next sign-in re-prompts. Callers
     * surface an explicit "permission missing, retry" error to the user.
     *
     * [missingScopes] is the subset of required OAuth scope URIs the user did not
     * grant — for logging/diagnostics only; not user-facing.
     */
    data class PartialGrant(val missingScopes: List<String>) : SignInResult

    /** Sign-in could not be attempted (e.g. no network, provider unavailable). */
    data class Failure(val error: BackupError) : SignInResult
}
