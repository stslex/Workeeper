// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

/**
 * Drops the bearer token from BOTH the local DataStore cache and the GMS
 * `AuthorizationClient` local cache. Used by `DriveBackupStorage` on a 401
 * response to force the next request to round-trip through `authorize()` and
 * pick up a freshly issued token from Google's OAuth server.
 *
 * Clearing only the DataStore is not enough: GMS holds its own per-account token
 * cache that the next silent `authorize()` will hit, returning the same stale
 * value. See `AuthorizationClient.clearToken(ClearTokenRequest)`.
 *
 * Implementations are best-effort — failures during invalidation are logged but
 * not propagated, since the caller (a retry path) cannot do better than to
 * attempt the refresh anyway.
 */
internal interface TokenInvalidator {

    suspend fun invalidate()
}
