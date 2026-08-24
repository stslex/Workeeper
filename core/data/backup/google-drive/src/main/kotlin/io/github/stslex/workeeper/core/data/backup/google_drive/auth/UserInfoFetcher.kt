// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

/**
 * Resolves the signed-in user's email + display name from `oauth2/v3/userinfo`, which
 * `AuthorizationResult` does not carry. Returns `null` on any failure rather than blocking.
 */
interface UserInfoFetcher {

    suspend fun fetch(accessToken: String): UserInfo?
}

/** Identity fields resolved by [UserInfoFetcher]. */
data class UserInfo(
    val email: String?,
    val name: String?,
)
