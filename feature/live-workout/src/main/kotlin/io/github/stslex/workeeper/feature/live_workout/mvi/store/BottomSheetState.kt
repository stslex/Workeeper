package io.github.stslex.workeeper.feature.live_workout.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerUiModel
import kotlinx.collections.immutable.ImmutableList

@Stable
sealed interface BottomSheetState {

    @Stable
    data object Hidden : BottomSheetState

    /**
     * Inline exercise picker bottom-sheet state. Display strings (no-match headline,
     * Create CTA label) are pre-formatted in the handler so the kit composable does
     * not derive text — keeps the picker locale-shape agnostic.
     */
    @Stable
    data class ExercisePicker(
        val query: String,
        val results: ImmutableList<ExercisePickerUiModel>,
        val noMatchHeadline: String?,
        val createCtaLabel: String?,
    ) : BottomSheetState
}
