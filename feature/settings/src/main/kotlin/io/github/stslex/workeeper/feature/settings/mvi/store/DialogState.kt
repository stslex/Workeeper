// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.store

import androidx.compose.runtime.Stable

@Stable
internal sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    @Stable
    data class RestoreConfirmation(
        val createdAtFormatted: String,
        val sizeFormatted: String,
    ) : DialogState

    @Stable
    data object SignOutConfirmation : DialogState
}
