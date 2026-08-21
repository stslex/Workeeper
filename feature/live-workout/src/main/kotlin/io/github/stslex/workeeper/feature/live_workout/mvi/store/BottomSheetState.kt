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

    /** `sh-session` (extraction §1.9): the topbar overflow — add exercise · cancel session. */
    @Stable
    data object SessionMenu : BottomSheetState

    /** `sh-ex`: the per-exercise menu. Content derives from the exercise's live State row. */
    @Stable
    data class ExerciseMenu(val performedExerciseUuid: String) : BottomSheetState

    /**
     * `sh-del`: the exercise-removal confirmation reached from [ExerciseMenu]'s delete item.
     * Copy uses the durable session context; template sessions retain their added/planned body.
     */
    @Stable
    data class DeleteExerciseConfirm(val performedExerciseUuid: String) : BottomSheetState

    /** `sh-desc`: the exercise description, reachable when the template has one. */
    @Stable
    data class ExerciseDescription(val performedExerciseUuid: String) : BottomSheetState
}
