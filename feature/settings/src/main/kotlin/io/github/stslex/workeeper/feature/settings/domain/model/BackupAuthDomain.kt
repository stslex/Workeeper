// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain.model

internal sealed interface BackupAuthDomain {

    data object NotAuthenticated : BackupAuthDomain

    data class Authenticated(val account: AccountDomain) : BackupAuthDomain
}
