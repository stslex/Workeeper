// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

/**
 * Resolves the signed-in user's email + display name from Google's
 * `oauth2/v3/userinfo` endpoint, using a fresh OAuth2 access token.
 *
 * `AuthorizationResult` does not carry identity fields — only the token. This
 * fetcher fills the gap so `DriveBackupAuth` can persist a meaningful
 * [io.github.stslex.workeeper.core.data.backup.api.model.Account] (email shown in
 * the UI, display name where the provider exposes one) instead of falling back
 * to the placeholder identifier.
 *
 * Returns `null` on any failure — network unavailable, scope missing, parse
 * error — so the caller can degrade gracefully (e.g. keep the GSA-derived email
 * or placeholder) rather than blocking sign-in.
 *
 * Public solely for cross-module Metro aggregation (App-Scope Collapse Step 3, PF.3): `UserInfoFetcherImpl`
 * carries `@ContributesBinding(AppScope)`, so this bound interface — and the [UserInfo] it returns — must be
 * visible to app/app's `AppGraph`. Not for external use (the only consumer is gd-internal `DriveBackupAuth`).
 */
interface UserInfoFetcher {

    suspend fun fetch(accessToken: String): UserInfo?
}

/** Public for the same cross-module Metro-aggregation reason as [UserInfoFetcher]; not for external use. */
data class UserInfo(
    val email: String?,
    val name: String?,
)
