package io.github.stslex.workeeper.core.data.backup.google_drive.auth

/**
 * Cached OAuth2 access token plus the absolute wall-clock instant at which the
 * cache entry is considered stale. Stored locally by [AccountDataStore] so the
 * token captured at sign-in / `completeSignIn` time is reused on subsequent
 * Drive HTTP calls without an extra `authorize()` round trip.
 *
 * Google's OAuth2 access tokens nominally live 60 minutes; the cache writer
 * applies a 10-minute safety margin so callers see a usable token for the
 * first ~50 minutes of any session.
 */
internal data class TokenSnapshot(
    val token: String,
    val expiresAtEpochMs: Long,
)

/** Safety-margin-adjusted TTL applied by writers to access tokens. */
internal const val TOKEN_TTL_MS: Long = 50L * 60 * 1000
