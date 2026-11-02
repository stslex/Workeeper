// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi

@Stable
sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    @Stable
    data class RestoreConfirmation(
        val createdAtFormatted: String,
        val sizeFormatted: String,
    ) : DialogState

    @Stable
    data object SignOutConfirmation : DialogState

    @Stable
    data class FrequencyPicker(
        val selectedSchedule: BackupScheduleUi,
        val allowOnMobileData: Boolean,
    ) : DialogState

    /**
     * The Home start card's mode sheet (HS5's second entry point). No payload — the checked
     * row reads `State.startCardMode`, the preference's single source of truth.
     */
    @Stable
    data object StartCardModePicker : DialogState
}
