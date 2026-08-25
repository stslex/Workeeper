// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

/** Bearer-token source for Drive HTTP calls; `null` means no session (`NotAuthenticated`). */
interface AuthTokenProvider {

    suspend fun currentToken(): String?
}
