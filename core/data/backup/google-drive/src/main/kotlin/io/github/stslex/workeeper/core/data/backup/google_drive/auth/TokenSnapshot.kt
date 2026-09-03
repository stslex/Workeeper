// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

/** Cached OAuth2 access token plus the wall-clock instant at which the entry goes stale. */
data class TokenSnapshot(
    val token: String,
    val expiresAtEpochMs: Long,
)

/** Safety-margin-adjusted TTL applied by writers to access tokens. */
internal const val TOKEN_TTL_MS: Long = 50L * 60 * 1000
