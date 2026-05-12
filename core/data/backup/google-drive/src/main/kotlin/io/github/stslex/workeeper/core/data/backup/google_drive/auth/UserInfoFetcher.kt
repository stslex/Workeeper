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
 */
internal interface UserInfoFetcher {

    suspend fun fetch(accessToken: String): UserInfo?
}

internal data class UserInfo(
    val email: String?,
    val name: String?,
)
