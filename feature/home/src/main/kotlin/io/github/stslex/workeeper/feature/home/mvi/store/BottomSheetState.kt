// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.feature.home.mvi.model.PickerTrainingItem
import kotlinx.collections.immutable.ImmutableList

/**
 * Home's one sheet slot. Two sheets exist on this screen — the training picker and the
 * start card's mode picker — so per the dialog-state rule they share a single sealed field:
 * one variant live at a time, mutual exclusivity at the type level, an open replacing
 * whatever was up. The conflict dialog stays its own nullable on `State` — it is the
 * screen's only dialog, and sheets and dialogs are separate families here as in
 * live-workout.
 *
 * Replaces the `State.PickerState` nested sealed type, whose `Hidden`/`Visible` pair was
 * this shape for one sheet.
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

    /**
     * The start card's mode picker (home-start-card.md HS4). Carries no payload — the
     * checked row reads `State.startCardMode`, the single source of the selection.
     */
    @Stable
    data object StartModePicker : BottomSheetState
}
