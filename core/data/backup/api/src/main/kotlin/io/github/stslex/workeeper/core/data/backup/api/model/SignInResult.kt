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

    /** Sign-in could not be attempted (e.g. no network, provider unavailable). */
    data class Failure(val error: BackupError) : SignInResult
}
