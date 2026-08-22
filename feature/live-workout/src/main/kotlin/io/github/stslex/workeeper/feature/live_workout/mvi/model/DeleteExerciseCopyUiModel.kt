// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

import androidx.annotation.StringRes
import io.github.stslex.workeeper.feature.live_workout.R

internal enum class DeleteExerciseCopyUiModel(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @StringRes val confirmRes: Int,
    @StringRes val toastRes: Int,
) {
    ADHOC_WORKOUT(
        titleRes = R.string.feature_live_workout_delete_workout_title,
        bodyRes = R.string.feature_live_workout_delete_workout_body,
        confirmRes = R.string.feature_live_workout_delete_workout_confirm,
        toastRes = R.string.feature_live_workout_toast_exercise_removed_from_workout,
    ),
    TEMPLATE_ADDITION(
        titleRes = R.string.feature_live_workout_delete_plan_title,
        bodyRes = R.string.feature_live_workout_delete_plan_body_added,
        confirmRes = R.string.feature_live_workout_delete_plan_confirm,
        toastRes = R.string.feature_live_workout_toast_exercise_removed,
    ),
    TEMPLATE_PLANNED(
        titleRes = R.string.feature_live_workout_delete_plan_title,
        bodyRes = R.string.feature_live_workout_delete_plan_body_planned,
        confirmRes = R.string.feature_live_workout_delete_plan_confirm,
        toastRes = R.string.feature_live_workout_toast_exercise_removed,
    ),
}
