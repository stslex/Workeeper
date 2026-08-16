// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/**
 * Identity of the user currently signed in to the backup provider.
 *
 * [email] is the canonical account identifier surfaced to the user; [displayName]
 * is purely cosmetic and may be `null` when the provider does not expose one.
 */
data class Account(
    val email: String,
    val displayName: String?,
)
