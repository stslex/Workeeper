// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import kotlinx.collections.immutable.ImmutableList

@Stable
data class TrainingExerciseItem(
    val exerciseUuid: String,
    val exerciseName: String,
    val exerciseType: ExerciseTypeUiModel,
    val tags: ImmutableList<String>,
    val position: Int,
    val planSets: ImmutableList<PlanSetUiModel>?,
    val planSummary: String,
)

/**
 * D2: the editor's constant copy — the no-exercises error and the two undo toasts — resolved once
 * per Store by `Init`, so the click paths that show it never suspend.
 */
@Stable
data class TrainingEditCopy(
    val noExercisesError: String,
    val exerciseRemoved: String,
    val setRemoved: String,
) {

    companion object {

        /** Until `Init`'s resolution lands; a toast needs a draft exercise or a typed name first. */
        val EMPTY = TrainingEditCopy(noExercisesError = "", exerciseRemoved = "", setRemoved = "")
    }
}

@Stable
data class HistorySessionItem(
    val sessionUuid: String,
    val dateLabel: String,
)

@Stable
data class PickerExerciseItem(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeUiModel,
    val tags: ImmutableList<String>,
) {

    // TODO(tech-debt-localization): Move tag-label aggregation into handler-level mapping
    // and persist a ready-to-render field instead of formatting from the UI model.
    val tagsLabel: String
        get() = tags.joinToString(separator = " · ")
}
