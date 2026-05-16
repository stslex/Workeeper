// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain.model

import android.content.IntentSender
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError

internal sealed interface SignInOutcomeDomain {

    data object Success : SignInOutcomeDomain

    data class NeedsResolution(val intentSender: IntentSender) : SignInOutcomeDomain

    data object PartialGrant : SignInOutcomeDomain

    data class Failure(val error: BackupError) : SignInOutcomeDomain
}
