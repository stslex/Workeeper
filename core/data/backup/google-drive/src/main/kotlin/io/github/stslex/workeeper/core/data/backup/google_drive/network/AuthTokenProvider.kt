// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

/**
 * Source of bearer tokens for outbound Drive HTTP calls. Implementations resolve
 * the token from the active backup session (see `DriveBackupAuth` in `auth/`).
 *
 * Returns `null` when no session is active — the network layer treats that as an
 * unauthenticated state and surfaces [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.NotAuthenticated]
 * to upstream consumers.
 *
 * Public (not `internal`) because the Metro `NetworkBindingContainer.provideHttpClient` provider takes it as
 * a parameter, and a public `@BindingContainer` `@Provides` function may not expose an `internal` parameter
 * type (App-Scope Collapse Step 3, PF.3). Terminal widening — its only member returns `String?`; it is a
 * GMS/ktor-clean interface (no HOME-A containment impact) with zero external consumers.
 */
interface AuthTokenProvider {

    suspend fun currentToken(): String?
}
