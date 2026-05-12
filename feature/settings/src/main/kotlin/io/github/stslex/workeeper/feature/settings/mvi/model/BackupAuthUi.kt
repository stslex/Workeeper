// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Stable

@Stable
internal sealed interface BackupAuthUi {

    data object NotAuthenticated : BackupAuthUi

    data class Authenticated(
        val email: String,
        val displayName: String?,
    ) : BackupAuthUi
}
