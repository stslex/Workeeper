// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

/**
 * Drops the bearer token from BOTH the DataStore cache and the GMS `AuthorizationClient` cache
 * on a 401 — clearing only DataStore leaves the next silent `authorize()` on the stale token.
 */
interface TokenInvalidator {

    suspend fun invalidate()
}
