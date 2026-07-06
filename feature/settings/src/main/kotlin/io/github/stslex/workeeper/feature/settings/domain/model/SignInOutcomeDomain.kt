// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain.model

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolution

internal sealed interface SignInOutcomeDomain {

    data object Success : SignInOutcomeDomain

    data class NeedsResolution(val resolution: AuthResolution) : SignInOutcomeDomain

    data object PartialGrant : SignInOutcomeDomain

    data class Failure(val error: BackupError) : SignInOutcomeDomain
}
