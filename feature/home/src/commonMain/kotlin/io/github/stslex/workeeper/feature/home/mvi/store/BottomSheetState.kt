// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.feature.home.mvi.model.PickerTrainingItem
import kotlinx.collections.immutable.ImmutableList

/**
 * Home's one sheet slot: the training picker and the mode picker share a single sealed field.
 * The conflict dialog stays its own nullable — sheets and dialogs are separate families.
 */
@Stable
sealed interface BottomSheetState {

    @Stable
    data object Hidden : BottomSheetState

    /** The Start-CTA training picker (v2.3), payload unchanged from `PickerState.Visible`. */
    @Stable
    data class TrainingPicker(
        val templates: ImmutableList<PickerTrainingItem>,
        val isLoading: Boolean,
    ) : BottomSheetState

    /** The mode picker (HS4); no payload — the checked row reads `State.startCardMode`. */
    @Stable
    data object StartModePicker : BottomSheetState
}
